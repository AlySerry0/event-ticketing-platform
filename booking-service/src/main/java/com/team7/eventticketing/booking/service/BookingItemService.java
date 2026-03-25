package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.dto.BookingItemDTO;
import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.repository.BookingItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookingItemService {

	@Autowired
	private BookingItemRepository bookingItemRepository;

	public BookingItem save(BookingItem bookingItem) {
		return bookingItemRepository.save(bookingItem);
	}

	public Optional<BookingItem> findById(Long id) {
		return bookingItemRepository.findById(id);
	}

	public List<BookingItem> findAll() {
		return bookingItemRepository.findAll();
	}

	public void deleteById(Long id) {
		bookingItemRepository.deleteById(id);
	}

	public Optional<BookingItem> updateBookingItem(Long id, BookingItemDTO itemDetails) {
		return bookingItemRepository.findById(id).map(item -> {
			if (itemDetails.getEventOrder() != null) item.setEventOrder(itemDetails.getEventOrder());
			if (itemDetails.getSessionId() != null) item.setSessionId(itemDetails.getSessionId());
			if (itemDetails.getSessionTitle() != null) item.setSessionTitle(itemDetails.getSessionTitle());
			if (itemDetails.getQuantity() != null) item.setQuantity(itemDetails.getQuantity());
			if (itemDetails.getUnitPrice() != null) item.setUnitPrice(itemDetails.getUnitPrice());
			if (itemDetails.getStatus() != null) item.setStatus(itemDetails.getStatus());

			// Handle JSONB metadata merge
			if (itemDetails.getMetadata() != null) {
				if (item.getMetadata() == null) {
					item.setMetadata(itemDetails.getMetadata());
				} else {
					item.getMetadata().putAll(itemDetails.getMetadata());
				}
			}
			return bookingItemRepository.save(item);
		});
	}
}
