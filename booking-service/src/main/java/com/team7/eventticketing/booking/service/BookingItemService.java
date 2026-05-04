package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.dto.BookingItemDTO;
import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.repository.BookingItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookingItemService {

	@Autowired
	private BookingItemRepository bookingItemRepository;

	public BookingItemDTO save(BookingItemDTO bookingItemDTO) {
		BookingItem bookingItem = convertToEntity(bookingItemDTO);
		return convertToDTO(bookingItemRepository.save(bookingItem));
	}

	@Cacheable(value = "booking-item", key = "#id")
	public Optional<BookingItemDTO> findById(Long id) {
		return bookingItemRepository.findById(id).map(this::convertToDTO);
	}

	public List<BookingItemDTO> findAll() {
		return bookingItemRepository.findAll().stream()
				.map(this::convertToDTO)
				.toList();
	}

	public void deleteById(Long id) {
		bookingItemRepository.deleteById(id);
	}

	public Optional<BookingItemDTO> updateBookingItem(Long id, BookingItemDTO itemDetails) {
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
			return convertToDTO(bookingItemRepository.save(item));
		});
	}

	public BookingItemDTO convertToDTO(BookingItem item) {
		BookingItemDTO dto = new BookingItemDTO();
		dto.setId(item.getId());
		dto.setEventOrder(item.getEventOrder());
		dto.setSessionId(item.getSessionId());
		dto.setSessionTitle(item.getSessionTitle());
		dto.setQuantity(item.getQuantity());
		dto.setUnitPrice(item.getUnitPrice());
		dto.setStatus(item.getStatus());
		dto.setMetadata(item.getMetadata());
		return dto;
	}

	private BookingItem convertToEntity(BookingItemDTO dto) {
		BookingItem item = new BookingItem();
		item.setId(dto.getId());
		item.setEventOrder(dto.getEventOrder());
		item.setSessionId(dto.getSessionId());
		item.setSessionTitle(dto.getSessionTitle());
		item.setQuantity(dto.getQuantity());
		item.setUnitPrice(dto.getUnitPrice());
		item.setStatus(dto.getStatus());
		item.setMetadata(dto.getMetadata());
		return item;
	}
}
