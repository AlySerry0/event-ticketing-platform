package com.team7.eventticketing.booking.dto;

import com.team7.eventticketing.booking.model.BookingStatus;

import java.util.List;
import java.util.Map;

public class BookingDetailsDTO {
    private Long bookingId;
    private Long userId;
    private Long eventId;
    private BookingStatus status;
    private Double totalAmount;
    private Map<String, Object> metadata;
    private List<BookingItemDTO> items;
    private Integer totalItems;
    private Integer confirmedItems;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final BookingDetailsDTO dto = new BookingDetailsDTO();

        public Builder bookingId(Long bookingId) {
            dto.bookingId = bookingId;
            return this;
        }

        public Builder userId(Long userId) {
            dto.userId = userId;
            return this;
        }

        public Builder eventId(Long eventId) {
            dto.eventId = eventId;
            return this;
        }

        public Builder status(BookingStatus status) {
            dto.status = status;
            return this;
        }

        public Builder totalAmount(Double totalAmount) {
            dto.totalAmount = totalAmount;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            dto.metadata = metadata;
            return this;
        }

        public Builder items(List<BookingItemDTO> items) {
            dto.items = items;
            return this;
        }

        public Builder totalItems(Integer totalItems) {
            dto.totalItems = totalItems;
            return this;
        }

        public Builder confirmedItems(Integer confirmedItems) {
            dto.confirmedItems = confirmedItems;
            return this;
        }

        public BookingDetailsDTO build() {
            return dto;
        }
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
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

    public List<BookingItemDTO> getItems() {
        return items;
    }

    public void setItems(List<BookingItemDTO> items) {
        this.items = items;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public Integer getConfirmedItems() {
        return confirmedItems;
    }

    public void setConfirmedItems(Integer confirmedItems) {
        this.confirmedItems = confirmedItems;
    }
}