package com.team7.eventticketing.booking.dto;

import java.util.Map;

public class BookingAnalyticsDashboardDTO {
	private Long totalBookings;
	private Double totalRevenue;
	private Double averageBookingValue;
	private Double conversionRate;
	private Map<String, Long> bookingsByStatus;

	public BookingAnalyticsDashboardDTO() {}

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