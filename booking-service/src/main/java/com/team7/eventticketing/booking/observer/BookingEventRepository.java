package com.team7.eventticketing.booking.observer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingEventRepository extends MongoRepository<BookingEvent, String> {
}
