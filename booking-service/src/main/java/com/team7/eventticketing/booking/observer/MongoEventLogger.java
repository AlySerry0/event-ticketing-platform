package com.team7.eventticketing.booking.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final BookingEventRepository bookingEventRepository;
    private final EventFactory eventFactory;

    public MongoEventLogger(BookingEventRepository bookingEventRepository, EventFactory eventFactory) {
        this.bookingEventRepository = bookingEventRepository;
        this.eventFactory = eventFactory;
    }

    @Override
    public void onEvent(String action, Object payload) {
        try {
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) payload;
                params.putIfAbsent("action", action);
                
                MongoEvent event = eventFactory.createEvent("BOOKING", params);
                if (event instanceof BookingEvent bookingEvent) {
                    bookingEventRepository.save(bookingEvent);
                }
            }
        } catch (DataAccessException e) {
            log.warn("MongoDB connection failed or data access error when saving booking audit event. Soft dependency gracefully skipped.", e);
        } catch (Exception e) {
            log.warn("Failed to save MongoDB booking audit event due to an unexpected error. Soft dependency gracefully skipped.", e);
        }
    }
}
