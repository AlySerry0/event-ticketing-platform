package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.dto.BookingDTO;
import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	public Booking save(Booking booking) {
		return bookingRepository.save(booking);
	}

	public Optional<Booking> findById(Long id) {
		return bookingRepository.findById(id);
	}

	public List<Booking> findAll() {
		return bookingRepository.findAll();
	}

	public void deleteById(Long id) {
		bookingRepository.deleteById(id);
	}

	public Optional<Booking> updateBooking(Long id, BookingDTO bookingDetails) {
		return bookingRepository.findById(id).map(booking -> {
			if (bookingDetails.getContactEmail() != null) booking.setContactEmail(bookingDetails.getContactEmail());
			if (bookingDetails.getStatus() != null) booking.setStatus(bookingDetails.getStatus());
			if (bookingDetails.getTotalAmount() != null) booking.setTotalAmount(bookingDetails.getTotalAmount());
			if (bookingDetails.getBookingDate() != null) booking.setBookingDate(bookingDetails.getBookingDate());
			if (bookingDetails.getConfirmedAt() != null) booking.setConfirmedAt(bookingDetails.getConfirmedAt());
			if (bookingDetails.getEventId() != null) booking.setEventId(bookingDetails.getEventId());
			if (bookingDetails.getUserId() != null) booking.setUserId(bookingDetails.getUserId());

			// Handle JSONB metadata merge
			if (bookingDetails.getMetadata() != null) {
				if (booking.getMetadata() == null) {
					booking.setMetadata(bookingDetails.getMetadata());
				} else {
					booking.getMetadata().putAll(bookingDetails.getMetadata());
				}
			}
			return bookingRepository.save(booking);
		});
	}

	public List<Booking> searchBookings(String statusStr, LocalDate startDate, LocalDate endDate) {
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

		if (status != null && startDateTime != null && endDateTime != null) {
			return bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(status, startDateTime, endDateTime);
		} else if (status != null) {
			return bookingRepository.findByStatusOrderByBookingDateDesc(status);
		} else if (startDateTime != null && endDateTime != null) {
			return bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDateTime, endDateTime);
		} else {
			return bookingRepository.findAllByOrderByBookingDateDesc();
		}
	}

	@Transactional
	public Booking confirmBookingAndAssignEvent(Long bookingId, Long eventId) {
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

		return bookingRepository.save(booking);
	}
}
