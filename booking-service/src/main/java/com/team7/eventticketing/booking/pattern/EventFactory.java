package com.team7.eventticketing.booking.pattern;

import com.team7.eventticketing.booking.model.BookingEvent;
import java.time.LocalDateTime;
import java.util.Map;

public class EventFactory {

	public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
		if (type == EventType.BOOKING) {
			BookingEvent event = new BookingEvent();
			// The factory extracts the required fields from the map
			event.setBookingId((Long) params.get("bookingId"));
			event.setAction((String) params.get("action"));
			event.setTimestamp(LocalDateTime.now());
			event.setDetails(params);
			return event;
		}

		// Since we are in the Booking Service, we only need to handle BOOKING events right now.
		throw new IllegalArgumentException("Unsupported EventType for Booking Service: " + type);
	}
}