package com.team7.eventticketing.booking.dto;

import com.team7.eventticketing.booking.model.BookingStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public class BookingDTO {
	private Long id;
	private Long userId;
	private Long eventId;
	private String contactEmail;
	private BookingStatus status;
	private Double totalAmount;
	private Map<String, Object> metadata;
	private LocalDateTime bookingDate;
	private LocalDateTime confirmedAt;
    private List<BookingItemDTO> bookingItems;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Long getEventId() {
		return eventId;
	}

	public void setEventId(Long eventId) {
		this.eventId = eventId;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public void setContactEmail(String contactEmail) {
		this.contactEmail = contactEmail;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public void setStatus(BookingStatus status) {
		this.status = status;
	}

	public Double getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(Double totalAmount) {
		this.totalAmount = totalAmount;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public LocalDateTime getBookingDate() {
		return bookingDate;
	}

	public void setBookingDate(LocalDateTime bookingDate) {
		this.bookingDate = bookingDate;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public void setConfirmedAt(LocalDateTime confirmedAt) {
		this.confirmedAt = confirmedAt;
	}

    public List<BookingItemDTO> getBookingItems() {
        return bookingItems;
    }

    public void setBookingItems(List<BookingItemDTO> bookingItems) {
        this.bookingItems = bookingItems;
    }
}