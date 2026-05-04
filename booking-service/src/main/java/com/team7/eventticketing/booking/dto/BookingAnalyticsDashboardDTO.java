package com.team7.eventticketing.booking.dto;

import java.util.Map;

public class BookingAnalyticsDashboardDTO {
	private Long totalBookings;
	private Double totalRevenue;
	private Double averageBookingValue;
	private Double conversionRate;
	private Map<String, Long> bookingsByStatus;

	public BookingAnalyticsDashboardDTO() {}

	// --- PHASE 5: BUILDER PATTERN ---
	private BookingAnalyticsDashboardDTO(Builder builder) {
		this.totalBookings = builder.totalBookings;
		this.totalRevenue = builder.totalRevenue;
		this.averageBookingValue = builder.averageBookingValue;
		this.conversionRate = builder.conversionRate;
		this.bookingsByStatus = builder.bookingsByStatus;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Long totalBookings;
		private Double totalRevenue;
		private Double averageBookingValue;
		private Double conversionRate;
		private Map<String, Long> bookingsByStatus;

		public Builder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
		public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
		public Builder averageBookingValue(Double averageBookingValue) { this.averageBookingValue = averageBookingValue; return this; }
		public Builder conversionRate(Double conversionRate) { this.conversionRate = conversionRate; return this; }
		public Builder bookingsByStatus(Map<String, Long> bookingsByStatus) { this.bookingsByStatus = bookingsByStatus; return this; }

		public BookingAnalyticsDashboardDTO build() {
			return new BookingAnalyticsDashboardDTO(this);
		}
	}

	// Getters and Setters
	public Long getTotalBookings() { return totalBookings; }
	public void setTotalBookings(Long totalBookings) { this.totalBookings = totalBookings; }
	public Double getTotalRevenue() { return totalRevenue; }
	public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }
	public Double getAverageBookingValue() { return averageBookingValue; }
	public void setAverageBookingValue(Double averageBookingValue) { this.averageBookingValue = averageBookingValue; }
	public Double getConversionRate() { return conversionRate; }
	public void setConversionRate(Double conversionRate) { this.conversionRate = conversionRate; }
	public Map<String, Long> getBookingsByStatus() { return bookingsByStatus; }
	public void setBookingsByStatus(Map<String, Long> bookingsByStatus) { this.bookingsByStatus = bookingsByStatus; }
}