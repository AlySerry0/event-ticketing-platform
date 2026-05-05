package com.team7.eventticketing.booking.dto;

/**
 * DTO for Booking Cost Estimate - includes ticket costs, fees, and demand multipliers
 */
public class BookingCostEstimateDTO {
	private Double ticketCost;
	private Double serviceFee;
	private Double estimatedTotal;
	private Double demandMultiplier;

	// Constructors
	public BookingCostEstimateDTO() {
	}

	public BookingCostEstimateDTO(Double ticketCost, Double serviceFee, Double estimatedTotal, Double demandMultiplier) {
		this.ticketCost = ticketCost;
		this.serviceFee = serviceFee;
		this.estimatedTotal = estimatedTotal;
		this.demandMultiplier = demandMultiplier;
	}

	// --- Builder Pattern Implementation (DP-4) ---

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Double ticketCost;
		private Double serviceFee;
		private Double estimatedTotal;
		private Double demandMultiplier;

		private Builder() {}

		public Builder ticketCost(Double ticketCost) {
			this.ticketCost = ticketCost;
			return this;
		}

		public Builder serviceFee(Double serviceFee) {
			this.serviceFee = serviceFee;
			return this;
		}

		public Builder estimatedTotal(Double estimatedTotal) {
			this.estimatedTotal = estimatedTotal;
			return this;
		}

		public Builder demandMultiplier(Double demandMultiplier) {
			this.demandMultiplier = demandMultiplier;
			return this;
		}

		public BookingCostEstimateDTO build() {
			return new BookingCostEstimateDTO(ticketCost, serviceFee, estimatedTotal, demandMultiplier);
		}
	}

	// --- Getters and Setters ---

	public Double getTicketCost() {
		return ticketCost;
	}

	public void setTicketCost(Double ticketCost) {
		this.ticketCost = ticketCost;
	}

	public Double getServiceFee() {
		return serviceFee;
	}

	public void setServiceFee(Double serviceFee) {
		this.serviceFee = serviceFee;
	}

	public Double getEstimatedTotal() {
		return estimatedTotal;
	}

	public void setEstimatedTotal(Double estimatedTotal) {
		this.estimatedTotal = estimatedTotal;
	}

	public Double getDemandMultiplier() {
		return demandMultiplier;
	}

	public void setDemandMultiplier(Double demandMultiplier) {
		this.demandMultiplier = demandMultiplier;
	}
}