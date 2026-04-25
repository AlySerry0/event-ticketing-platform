package com.team7.eventticketing.booking.model;

import com.team7.eventticketing.booking.pattern.MongoEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "booking_events")
public class BookingEvent implements MongoEvent {

	@Id
	private String id;
	private Long bookingId; // Required by the M2 spec
	private String action;
	private LocalDateTime timestamp;
	private Map<String, Object> details;

	// Getters and Setters
	@Override public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public Long getBookingId() { return bookingId; }
	public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

	@Override public String getAction() { return action; }
	public void setAction(String action) { this.action = action; }

	@Override public LocalDateTime getTimestamp() { return timestamp; }
	public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

	@Override public Map<String, Object> getDetails() { return details; }
	public void setDetails(Map<String, Object> details) { this.details = details; }
}