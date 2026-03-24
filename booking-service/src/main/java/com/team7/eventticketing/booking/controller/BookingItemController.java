package com.team7.eventticketing.booking.controller;

import com.team7.eventticketing.booking.model.BookingItem;
import com.team7.eventticketing.booking.service.BookingItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking-items")
public class BookingItemController {

	@Autowired
	private BookingItemService bookingItemService;

	@PostMapping
	public BookingItem create(@RequestBody BookingItem bookingItem) {
		return bookingItemService.save(bookingItem);
	}

	@GetMapping("/{id}")
	public ResponseEntity<BookingItem> getById(@PathVariable Long id) {
		return bookingItemService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping
	public List<BookingItem> getAll() {
		return bookingItemService.findAll();
	}

	@PutMapping("/{id}")
	public ResponseEntity<BookingItem> update(@PathVariable Long id, @RequestBody BookingItem itemDetails) {
		return bookingItemService.findById(id).map(item -> {
			item.setEventOrder(itemDetails.getEventOrder());
			item.setSessionId(itemDetails.getSessionId());
			item.setSessionTitle(itemDetails.getSessionTitle());
			item.setQuantity(itemDetails.getQuantity());
			item.setUnitPrice(itemDetails.getUnitPrice());
			item.setStatus(itemDetails.getStatus());
			item.setMetadata(itemDetails.getMetadata());
			return ResponseEntity.ok(bookingItemService.save(item));
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (bookingItemService.findById(id).isPresent()) {
			bookingItemService.deleteById(id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}
}
