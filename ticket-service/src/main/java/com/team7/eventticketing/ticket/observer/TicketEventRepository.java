package com.team7.eventticketing.ticket.observer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketEventRepository extends MongoRepository<TicketEvent, String> {
}
