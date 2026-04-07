package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.dto.BookingCostEstimateDTO;
import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.dto.BookingEstimateRequestDTO;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class BookingService {

	@Autowired
	private BookingRepository bookingRepository;

	public BookingDTO save(BookingDTO bookingDTO) {
		Booking booking = convertToEntity(bookingDTO);
		booking.setBookingDate(LocalDateTime.now());
		if (booking.getStatus() == null) {
			booking.setStatus(BookingStatus.PENDING);
		}
		return convertToDTO(bookingRepository.save(booking));
	}

	public Optional<BookingDTO> findById(Long id) {
		return bookingRepository.findById(id).map(this::convertToDTO);
	}

	public List<BookingDTO> findAll() {
		return bookingRepository.findAll().stream()
				.map(this::convertToDTO)
				.toList();
	}

	public void deleteById(Long id) {
		bookingRepository.deleteById(id);
	}

	public Optional<BookingDTO> updateBooking(Long id, BookingDTO bookingDetails) {
		return bookingRepository.findById(id).map(booking -> {
			if (bookingDetails.getContactEmail() != null)
				booking.setContactEmail(bookingDetails.getContactEmail());
			if (bookingDetails.getStatus() != null)
				booking.setStatus(bookingDetails.getStatus());
			if (bookingDetails.getTotalAmount() != null)
				booking.setTotalAmount(bookingDetails.getTotalAmount());
			if (bookingDetails.getBookingDate() != null)
				booking.setBookingDate(bookingDetails.getBookingDate());
			if (bookingDetails.getConfirmedAt() != null)
				booking.setConfirmedAt(bookingDetails.getConfirmedAt());
			if (bookingDetails.getEventId() != null)
				booking.setEventId(bookingDetails.getEventId());
			if (bookingDetails.getUserId() != null)
				booking.setUserId(bookingDetails.getUserId());

			// Handle JSONB metadata merge
			if (bookingDetails.getMetadata() != null) {
				if (booking.getMetadata() == null) {
					booking.setMetadata(bookingDetails.getMetadata());
				} else {
					booking.getMetadata().putAll(bookingDetails.getMetadata());
				}
			}
			return convertToDTO(bookingRepository.save(booking));
		});
	}

	public List<BookingDTO> searchBookings(String statusStr, LocalDate startDate, LocalDate endDate) {
		BookingStatus status = null;

		if (statusStr != null && !statusStr.trim().isEmpty()) {
			try {
				status = BookingStatus.valueOf(statusStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid booking status: " + statusStr);
			}
		}

		if ((startDate != null && endDate == null) || (startDate == null && endDate != null)) {
			throw new IllegalArgumentException("Both startDate and endDate must be provided together.");
		}
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("startDate cannot be after endDate.");
		}

		LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
		LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

		List<Booking> bookings;
		if (status != null && startDateTime != null && endDateTime != null) {
			bookings = bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(status, startDateTime,
					endDateTime);
		} else if (status != null) {
			bookings = bookingRepository.findByStatusOrderByBookingDateDesc(status);
		} else if (startDateTime != null && endDateTime != null) {
			bookings = bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime);
		} else {
			bookings = bookingRepository.findAllByOrderByBookingDateDesc();
		}

		return bookings.stream().map(this::convertToDTO).toList();
	}

	@Transactional
	public BookingDTO confirmBookingAndAssignEvent(Long bookingId, Long eventId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new NoSuchElementException("Booking not found"));

		if (booking.getStatus() != BookingStatus.PENDING) {
			throw new IllegalArgumentException("Booking is not PENDING");
		}

		String eventStatus = bookingRepository.findEventStatusById(eventId);
		if (eventStatus == null) {
			throw new NoSuchElementException("Event not found");
		}
		if (!"UPCOMING".equals(eventStatus)) {
			throw new IllegalArgumentException("Event is not UPCOMING");
		}

		booking.setEventId(eventId);
		booking.setStatus(BookingStatus.CONFIRMED);
		booking.setConfirmedAt(LocalDateTime.now());

		return convertToDTO(bookingRepository.save(booking));
	}

	@Transactional
	public BookingDTO completeBooking(Long id) {
		Booking booking = bookingRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

		if (booking.getStatus() != BookingStatus.CHECKED_IN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not CHECKED_IN");
		}
		booking.setStatus(BookingStatus.COMPLETED);
		if (booking.getTotalAmount() == null || booking.getTotalAmount() == 0.0) {
			double total = 0.0;
			if (booking.getBookingItems() != null) {
				for (BookingItem item : booking.getBookingItems()) {
					total += (item.getQuantity() * item.getUnitPrice());
				}
			}
			booking.setTotalAmount(total);
		}
		Booking savedBooking = bookingRepository.save(booking);
		bookingRepository.createPendingTicketSale(
				savedBooking.getId(),
				savedBooking.getUserId(),
				savedBooking.getTotalAmount()
		);
		return convertToDTO(savedBooking);
	}

	public BookingCostEstimateDTO getCostEstimate(BookingEstimateRequestDTO request) {
		Double avgCapacity = bookingRepository.getAverageSessionCapacityByEventId(request.getEventId());

		if (avgCapacity == null) {
			throw new IllegalArgumentException("Event or sessions not found for ID: " + request.getEventId());
		}

		double basePrice = avgCapacity / 10.0;

		double tierMultiplier = "VIP".equalsIgnoreCase(request.getTicketTier()) ? 2.5 : 1.0;

		double ticketCost = basePrice * tierMultiplier * request.getTicketCount();

		double serviceFee = ticketCost * 0.15;

		long activeBookings = bookingRepository.countActiveBookingsForEvent(request.getEventId());
		double demandMultiplier = 1.0;

		if (activeBookings > 200) {
			demandMultiplier = 1.5;
		} else if (activeBookings >= 51) {
			demandMultiplier = 1.25;
		}

		double estimatedTotal = (ticketCost + serviceFee) * demandMultiplier;

		BookingCostEstimateDTO estimateDTO = new BookingCostEstimateDTO();
		estimateDTO.setTicketCost(ticketCost);
		estimateDTO.setServiceFee(serviceFee);
		estimateDTO.setDemandMultiplier(demandMultiplier);
		estimateDTO.setEstimatedTotal(estimatedTotal);

		return estimateDTO;
	}

	private BookingDTO convertToDTO(Booking booking) {
		BookingDTO dto = new BookingDTO();
		dto.setId(booking.getId());
		dto.setUserId(booking.getUserId());
		dto.setEventId(booking.getEventId());
		dto.setContactEmail(booking.getContactEmail());
		dto.setStatus(booking.getStatus());
		dto.setTotalAmount(booking.getTotalAmount());
		dto.setMetadata(booking.getMetadata());
		dto.setBookingDate(booking.getBookingDate());
		dto.setConfirmedAt(booking.getConfirmedAt());
		return dto;
	}

	private Booking convertToEntity(BookingDTO dto) {
		Booking booking = new Booking();
		booking.setId(dto.getId());
		booking.setUserId(dto.getUserId());
		booking.setEventId(dto.getEventId());
		booking.setContactEmail(dto.getContactEmail());
		booking.setStatus(dto.getStatus());
		booking.setTotalAmount(dto.getTotalAmount());
		booking.setMetadata(dto.getMetadata());
		booking.setBookingDate(dto.getBookingDate());
		booking.setConfirmedAt(dto.getConfirmedAt());
		return booking;
	}
}
