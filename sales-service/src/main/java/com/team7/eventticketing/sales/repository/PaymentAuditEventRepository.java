package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.mongo.PaymentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {
}