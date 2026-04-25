package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.dto.BookingItemDTO;
import com.team7.eventticketing.booking.service.BookingItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/booking-items")
public class BookingItemController {

	@Autowired
	private BookingItemService bookingItemService;

	@PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
	@PostMapping
	public BookingItemDTO create(@RequestBody BookingItemDTO bookingItemDTO) {
		return bookingItemService.save(bookingItemDTO);
	}

	@PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
	@GetMapping("/{id}")
	public ResponseEntity<BookingItemDTO> getById(@PathVariable Long id) {
		return bookingItemService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
	@GetMapping
	public List<BookingItemDTO> getAll() {
		return bookingItemService.findAll();
	}

	@PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
	@PutMapping("/{id}")
	public ResponseEntity<BookingItemDTO> update(@PathVariable Long id, @RequestBody BookingItemDTO itemDetails) {
		return bookingItemService.updateBookingItem(id, itemDetails)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (bookingItemService.findById(id).isPresent()) {
			bookingItemService.deleteById(id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}
}
