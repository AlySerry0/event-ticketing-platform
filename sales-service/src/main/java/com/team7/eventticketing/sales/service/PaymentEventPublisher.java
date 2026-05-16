package com.team7.eventticketing.sales.service;

import com.team7.eventticketing.contracts.events.PaymentCompletedEvent;
import com.team7.eventticketing.contracts.events.PaymentFailedEvent;
import com.team7.eventticketing.contracts.events.PaymentInitiatedEvent;
import com.team7.eventticketing.contracts.events.PaymentRefundedEvent;
import com.team7.eventticketing.sales.config.PaymentEventConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentInitiated(
            Long saleId,
            Long bookingId,
            Double amount
    ) {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent(
                saleId,
                bookingId,
                amount,
                LocalDateTime.now()
        );

        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_INITIATED_ROUTING_KEY,
                event
        );
    }

    public void publishPaymentCompleted(
            Long saleId,
            Long bookingId,
            Double amount
    ) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                saleId,
                bookingId,
                amount,
                LocalDateTime.now()
        );

        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_COMPLETED_ROUTING_KEY,
                event
        );
    }

    public void publishPaymentFailed(
            Long saleId,
            Long bookingId,
            String reason
    ) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                saleId,
                bookingId,
                reason,
                LocalDateTime.now()
        );

        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_FAILED_ROUTING_KEY,
                event
        );
    }

    public void publishPaymentRefunded(
            Long saleId,
            Long bookingId,
            Double refundAmount
    ) {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                saleId,
                bookingId,
                refundAmount,
                LocalDateTime.now()
        );

        rabbitTemplate.convertAndSend(
                PaymentEventConfig.PAYMENT_EXCHANGE,
                PaymentEventConfig.PAYMENT_REFUNDED_ROUTING_KEY,
                event
        );
    }
}