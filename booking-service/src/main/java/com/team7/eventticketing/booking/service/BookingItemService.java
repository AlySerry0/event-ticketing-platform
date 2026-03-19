package com.team7.eventticketing.booking.service;

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
}
