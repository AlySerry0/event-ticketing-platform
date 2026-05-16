package com.team7.eventticketing.sales.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team7.eventticketing.contracts.dto.BookingDTO;
import com.team7.eventticketing.contracts.dto.UserDTO;
import com.team7.eventticketing.contracts.events.BookingCancelledEvent;
import com.team7.eventticketing.contracts.events.BookingCompletedEvent;
import com.team7.eventticketing.contracts.feign.BookingServiceClient;
import com.team7.eventticketing.contracts.feign.UserServiceClient;
import com.team7.eventticketing.sales.config.PaymentEventConfig;
import com.team7.eventticketing.sales.dto.RefundRequestDTO;
import com.team7.eventticketing.sales.model.PaymentMethod;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import com.team7.eventticketing.sales.service.PaymentEventPublisher;
import com.team7.eventticketing.sales.service.TicketSaleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class BookingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);

    private final TicketSaleRepository ticketSaleRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final BookingServiceClient bookingServiceClient;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;
    private final TicketSaleService ticketSaleService;

    public BookingEventConsumer(
            TicketSaleRepository ticketSaleRepository,
            PaymentEventPublisher paymentEventPublisher,
            BookingServiceClient bookingServiceClient,
            UserServiceClient userServiceClient,
            ObjectMapper objectMapper,
            TicketSaleService ticketSaleService
    ) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.bookingServiceClient = bookingServiceClient;
        this.userServiceClient = userServiceClient;
        this.objectMapper = objectMapper;
        this.ticketSaleService = ticketSaleService;
    }

    @Transactional
    @RabbitListener(queues = PaymentEventConfig.PAYMENT_SAGA_QUEUE)
    public void handleBookingEvent(
            Message message,
            @Header("amqp_receivedRoutingKey") String routingKey
    ) throws Exception {

        if (PaymentEventConfig.BOOKING_COMPLETED_ROUTING_KEY.equals(routingKey)) {
            BookingCompletedEvent event =
                    objectMapper.readValue(message.getBody(), BookingCompletedEvent.class);
            handleBookingCompleted(event);
            return;
        }

        if (PaymentEventConfig.BOOKING_CANCELLED_ROUTING_KEY.equals(routingKey)) {
            BookingCancelledEvent event =
                    objectMapper.readValue(message.getBody(), BookingCancelledEvent.class);
            handleBookingCancelled(event);
            return;
        }

        log.warn("Unsupported booking saga routing key={}", routingKey);
    }

    private void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("Received booking.completed event for bookingId={}", event.bookingId());

        ticketSaleRepository.findByBookingId(event.bookingId())
                .ifPresentOrElse(
                        existingSale -> log.info(
                                "TicketSale already exists for bookingId={}, saleId={}. Skipping duplicate.",
                                event.bookingId(),
                                existingSale.getId()
                        ),
                        () -> createPendingSaleAndPublishPaymentInitiated(event)
                );
    }

    private void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("Received booking.cancelled event for bookingId={}", event.bookingId());

        ticketSaleRepository.findByBookingId(event.bookingId())
                .ifPresentOrElse(
                        sale -> processRefundIfEligible(sale, event),
                        () -> log.warn(
                                "No TicketSale found for cancelled bookingId={}",
                                event.bookingId()
                        )
                );
    }

    private void createPendingSaleAndPublishPaymentInitiated(BookingCompletedEvent event) {
        UserDTO user = null;
        try {
            user = userServiceClient.getUser(event.userId());
        } catch (feign.FeignException.NotFound e) {
            log.warn("User not found for userId={}, proceeding with default payment method", event.userId());
        } catch (feign.FeignException e) {
            log.warn("user-service unavailable for userId={}, proceeding with default payment method: {}",
                    event.userId(), e.getMessage());
        }

        BookingDTO booking;
        try {
            booking = bookingServiceClient.getBooking(event.bookingId());
        } catch (feign.FeignException.NotFound e) {
            log.error("Booking not found for bookingId={}, cannot create TicketSale", event.bookingId());
            throw new RuntimeException("Booking not found for bookingId=" + event.bookingId());
        } catch (feign.FeignException e) {
            log.error("booking-service unavailable for bookingId={}: {}", event.bookingId(), e.getMessage());
            throw new RuntimeException("Booking service temporarily unavailable for bookingId=" + event.bookingId());
        }

        PaymentMethod paymentMethod = PaymentMethod.CREDIT_CARD;
        if (user != null && user.preferences() != null) {
            Object pref = user.preferences().get("preferredPaymentMethod");
            if (pref != null) {
                try {
                    paymentMethod = PaymentMethod.valueOf(pref.toString().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown preferredPaymentMethod '{}' for userId={}, defaulting to CREDIT_CARD",
                            pref, event.userId());
                }
            }
        }

        TicketSale sale = new TicketSale();
        sale.setBookingId(event.bookingId());
        sale.setUserId(event.userId());
        sale.setAmount(booking.totalAmount() != null ? booking.totalAmount().doubleValue() : 0.0);
        sale.setMethod(paymentMethod);
        sale.setStatus(TicketSaleStatus.PENDING);
        sale.setCreatedAt(LocalDateTime.now());

        HashMap<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("sourceEvent", "booking.completed");
        transactionDetails.put("bookingId", event.bookingId());
        transactionDetails.put("eventId", event.eventId());
        transactionDetails.put(
                "occurredAt",
                event.occurredAt() != null ? event.occurredAt().toString() : null
        );
        sale.setTransactionDetails(transactionDetails);

        TicketSale savedSale = ticketSaleRepository.saveAndFlush(sale);

        paymentEventPublisher.publishPaymentInitiated(
                savedSale.getId(),
                savedSale.getBookingId(),
                savedSale.getAmount()
        );

        log.info("Created PENDING TicketSale saleId={} for bookingId={} and published payment.initiated",
                savedSale.getId(), savedSale.getBookingId());
    }

    private void processRefundIfEligible(TicketSale sale, BookingCancelledEvent event) {
        if (sale.getStatus() == TicketSaleStatus.REFUNDED) {
            log.info(
                    "Sale already refunded for bookingId={}, saleId={}",
                    sale.getBookingId(),
                    sale.getId()
            );
            return;
        }

        if (sale.getStatus() != TicketSaleStatus.COMPLETED
                && sale.getStatus() != TicketSaleStatus.PENDING) {
            log.warn(
                    "Skipping refund for saleId={} because status={}",
                    sale.getId(),
                    sale.getStatus()
            );
            return;
        }

        try {
            RefundRequestDTO refundRequest = new RefundRequestDTO();
            refundRequest.setReason(event.reason() != null ? event.reason() : "booking.cancelled");

            ticketSaleService.processRefundWithWindowPolicy(sale.getId(), refundRequest);

            paymentEventPublisher.publishPaymentRefunded(
                    sale.getId(),
                    sale.getBookingId(),
                    sale.getAmount()
            );

            log.info(
                    "Refunded TicketSale saleId={} for bookingId={} via S5-F12 window policy",
                    sale.getId(),
                    sale.getBookingId()
            );

        } catch (ResponseStatusException rse) {
            if (rse.getStatusCode().value() == 404) {
                log.warn(
                        "eventDate not reachable for saleId={}, falling back to direct refund: {}",
                        sale.getId(),
                        rse.getMessage()
                );
                sale.setStatus(TicketSaleStatus.REFUNDED);
                Map<String, Object> details = sale.getTransactionDetails() != null
                        ? sale.getTransactionDetails()
                        : new HashMap<>();
                details.put("refundReason", event.reason());
                details.put("refundTriggeredBy", "booking.cancelled");
                sale.setTransactionDetails(details);
                ticketSaleRepository.saveAndFlush(sale);

                paymentEventPublisher.publishPaymentRefunded(
                        sale.getId(),
                        sale.getBookingId(),
                        sale.getAmount()
                );
            } else {
                log.warn(
                        "Refund denied for saleId={}, bookingId={}: {}",
                        sale.getId(),
                        sale.getBookingId(),
                        rse.getMessage()
                );
            }
        } catch (RuntimeException e) {
            log.error(
                    "Unexpected error processing refund for saleId={}, bookingId={}: {}",
                    sale.getId(),
                    sale.getBookingId(),
                    e.getMessage()
            );
            throw e;
        }
    }
}