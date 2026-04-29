package com.team7.eventticketing.sales.strategy;

import com.team7.eventticketing.sales.model.TicketSale;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class RefundStrategySelector {

    private final FullWindowRefundStrategy fullWindowRefundStrategy;
    private final PartialWindowRefundStrategy partialWindowRefundStrategy;
    private final NoRefundStrategy noRefundStrategy;

    public RefundStrategySelector(FullWindowRefundStrategy fullWindowRefundStrategy,
                                  PartialWindowRefundStrategy partialWindowRefundStrategy,
                                  NoRefundStrategy noRefundStrategy) {
        this.fullWindowRefundStrategy = fullWindowRefundStrategy;
        this.partialWindowRefundStrategy = partialWindowRefundStrategy;
        this.noRefundStrategy = noRefundStrategy;
    }

    public RefundStrategy select(TicketSale sale, LocalDateTime eventDate) {
        long hoursUntilEvent = Duration.between(LocalDateTime.now(), eventDate).toHours();

        if (hoursUntilEvent > 48) {
            return fullWindowRefundStrategy;
        }

        if (hoursUntilEvent >= 24) {
            return partialWindowRefundStrategy;
        }

        return noRefundStrategy;
    }
}