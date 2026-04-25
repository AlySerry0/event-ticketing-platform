package com.team7.eventticketing.booking.repository;

import com.team7.eventticketing.booking.model.BookingEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingEventRepository extends MongoRepository<BookingEvent, String> {
}
