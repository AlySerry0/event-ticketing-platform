package com.team7.eventticketing.sales.consumer;

import com.team7.eventticketing.contracts.events.BookingCancelledEvent;
import com.team7.eventticketing.sales.config.RabbitMQConfig;
import com.team7.eventticketing.sales.model.TicketSale;
import com.team7.eventticketing.sales.model.TicketSaleStatus;
import com.team7.eventticketing.sales.repository.TicketSaleRepository;
import com.team7.eventticketing.sales.service.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class BookingCancelledConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(BookingCancelledConsumer.class);

    private final TicketSaleRepository ticketSaleRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public BookingCancelledConsumer(
            TicketSaleRepository ticketSaleRepository,
            PaymentEventPublisher paymentEventPublisher
    ) {
        this.ticketSaleRepository = ticketSaleRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_SAGA_QUEUE)
    public void handleBookingCancelled(BookingCancelledEvent event) {

        log.info(
                "Received booking.cancelled event for bookingId={}",
                event.bookingId()
        );

        ticketSaleRepository.findByBookingId(event.bookingId())
                .ifPresentOrElse(
                        sale -> processRefundIfEligible(sale, event),
                        () -> log.warn(
                                "No TicketSale found for cancelled bookingId={}",
                                event.bookingId()
                        )
                );
    }

    private void processRefundIfEligible(
            TicketSale sale,
            BookingCancelledEvent event
    ) {

        if (sale.getStatus() == TicketSaleStatus.REFUNDED) {
            log.info(
                    "Sale already refunded for bookingId={}, saleId={}",
                    sale.getBookingId(),
                    sale.getId()
            );
            return;
        }

        if (sale.getStatus() != TicketSaleStatus.COMPLETED) {
            log.warn(
                    "Skipping refund for saleId={} because status={}",
                    sale.getId(),
                    sale.getStatus()
            );
            return;
        }

        sale.setStatus(TicketSaleStatus.REFUNDED);

        Map<String, Object> details = sale.getTransactionDetails();

        if (details != null) {
            details.put("refundReason", event.reason());
            details.put("refundTriggeredBy", "booking.cancelled");
        }

        TicketSale refundedSale =
                ticketSaleRepository.saveAndFlush(sale);

        paymentEventPublisher.publishPaymentRefunded(
                refundedSale.getId(),
                refundedSale.getBookingId(),
                refundedSale.getAmount()
        );

        log.info(
                "Refunded TicketSale saleId={} for bookingId={}",
                refundedSale.getId(),
                refundedSale.getBookingId()
        );
    }
}