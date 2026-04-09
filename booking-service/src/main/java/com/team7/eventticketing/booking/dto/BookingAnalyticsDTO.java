package com.team7.eventticketing.booking.dto;

public class BookingAnalyticsDTO {
	private Long totalBookings;
	private Long completedBookings;
	private Long cancelledBookings;
	private Double totalRevenue;
	private Double averageBookingAmount;
	private Double completionRate;

	public BookingAnalyticsDTO() {}

	public Long getTotalBookings() { return totalBookings; }
	public void setTotalBookings(Long totalBookings) { this.totalBookings = totalBookings; }

	public Long getCompletedBookings() { return completedBookings; }
	public void setCompletedBookings(Long completedBookings) { this.completedBookings = completedBookings; }

	public Long getCancelledBookings() { return cancelledBookings; }
	public void setCancelledBookings(Long cancelledBookings) { this.cancelledBookings = cancelledBookings; }

	public Double getTotalRevenue() { return totalRevenue; }
	public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }

	public Double getAverageBookingAmount() { return averageBookingAmount; }
	public void setAverageBookingAmount(Double averageBookingAmount) { this.averageBookingAmount = averageBookingAmount; }

	public Double getCompletionRate() { return completionRate; }
	public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
}
