package com.team7.eventticketing.sales.consumer;

import com.team7.eventticketing.contracts.dto.BookingDTO;
import com.team7.eventticketing.contracts.feign.BookingServiceClient;
import com.team7.eventticketing.contracts.events.BookingCompletedEvent;
import com.team7.eventticketing.sales.config.RabbitMQConfig;
import com.team7.eventticketing.sales.model.PaymentMethod;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import com.team7.eventticketing.sales.service.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;

@Component
public class BookingCompletedConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(BookingCompletedConsumer.class);

    private final TicketSaleRepository ticketSaleRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final BookingServiceClient bookingServiceClient;

    public BookingCompletedConsumer(
            TicketSaleRepository ticketSaleRepository,
            PaymentEventPublisher paymentEventPublisher,
            BookingServiceClient bookingServiceClient
    ) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.bookingServiceClient = bookingServiceClient;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_SAGA_QUEUE)
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("Received booking.completed event for bookingId={}", event.bookingId());

        ticketSaleRepository.findByBookingId(event.bookingId())
                .ifPresentOrElse(
                        existingSale -> log.info(
                                "TicketSale already exists for bookingId={}, saleId={}. Skipping duplicate event.",
                                event.bookingId(),
                                existingSale.getId()
                        ),
                        () -> createPendingSaleAndPublishPaymentInitiated(event)
                );
    }

    private void createPendingSaleAndPublishPaymentInitiated(BookingCompletedEvent event) {
        BookingDTO booking = bookingServiceClient.getBooking(event.bookingId());
        TicketSale sale = new TicketSale();
        sale.setBookingId(event.bookingId());
        sale.setUserId(event.userId());
        sale.setAmount(booking.totalAmount() != null ? booking.totalAmount().doubleValue() : 0.0);
        sale.setMethod(PaymentMethod.CREDIT_CARD);
        sale.setStatus(TicketSaleStatus.PENDING);
        sale.setCreatedAt(LocalDateTime.now());

        HashMap<String, Object> transactionDetails = new HashMap<>();
        transactionDetails.put("sourceEvent", "booking.completed");
        transactionDetails.put("bookingId", event.bookingId());
        transactionDetails.put("eventId", event.eventId());
        transactionDetails.put("occurredAt", event.occurredAt() != null ? event.occurredAt().toString() : null);

        sale.setTransactionDetails(transactionDetails);

        TicketSale savedSale = ticketSaleRepository.saveAndFlush(sale);

        paymentEventPublisher.publishPaymentInitiated(
                savedSale.getId(),
                savedSale.getBookingId(),
                savedSale.getAmount()
        );

        log.info(
                "Created PENDING TicketSale saleId={} for bookingId={} and published payment.initiated",
                savedSale.getId(),
                savedSale.getBookingId()
        );
    }
}