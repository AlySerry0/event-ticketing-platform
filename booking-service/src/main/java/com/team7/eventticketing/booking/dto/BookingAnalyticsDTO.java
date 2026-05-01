package com.team7.eventticketing.booking.dto;

public class BookingAnalyticsDTO {
	private Long totalBookings;
	private Long completedBookings;
	private Long cancelledBookings;
	private Double totalRevenue;
	private Double averageBookingAmount;
	private Double completionRate;

	public BookingAnalyticsDTO() {}

	private BookingAnalyticsDTO(Builder builder) {
		this.totalBookings = builder.totalBookings;
		this.completedBookings = builder.completedBookings;
		this.cancelledBookings = builder.cancelledBookings;
		this.totalRevenue = builder.totalRevenue;
		this.averageBookingAmount = builder.averageBookingAmount;
		this.completionRate = builder.completionRate;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Long totalBookings;
		private Long completedBookings;
		private Long cancelledBookings;
		private Double totalRevenue;
		private Double averageBookingAmount;
		private Double completionRate;

		public Builder totalBookings(Long totalBookings) { this.totalBookings = totalBookings; return this; }
		public Builder completedBookings(Long completedBookings) { this.completedBookings = completedBookings; return this; }
		public Builder cancelledBookings(Long cancelledBookings) { this.cancelledBookings = cancelledBookings; return this; }
		public Builder totalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
		public Builder averageBookingAmount(Double averageBookingAmount) { this.averageBookingAmount = averageBookingAmount; return this; }
		public Builder completionRate(Double completionRate) { this.completionRate = completionRate; return this; }

		public BookingAnalyticsDTO build() {
			return new BookingAnalyticsDTO(this);
		}
	}

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