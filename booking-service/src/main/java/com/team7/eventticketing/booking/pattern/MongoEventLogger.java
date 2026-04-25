package com.team7.eventticketing.booking.pattern;

import com.team7.eventticketing.booking.model.BookingEvent;
import com.team7.eventticketing.booking.repository.BookingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

	private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);
	private final BookingEventRepository repository;
	private final EventType boundEventType = EventType.BOOKING;

	public MongoEventLogger(BookingEventRepository repository) {
		this.repository = repository;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onEvent(String action, Object payload) {
		try {
			// Cast the payload to a Map and inject the action string
			Map<String, Object> params = (Map<String, Object>) payload;
			params.put("action", action);

			// 1. Use the Factory to create the event
			MongoEvent event = EventFactory.createEvent(boundEventType, params);

			// 2. Save to MongoDB
			repository.save((BookingEvent) event);

		} catch (Exception e) {
			// SOFT DEPENDENCY POLICY: Log the error, do NOT throw it!
			log.warn("Failed to write event to MongoDB. Proceeding anyway. Error: {}", e.getMessage());
		}
	}
}