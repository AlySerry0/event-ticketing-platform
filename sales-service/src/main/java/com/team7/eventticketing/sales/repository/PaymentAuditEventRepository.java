package com.team7.eventticketing.sales.repository;

import com.team7.eventticketing.sales.model.PaymentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAuditEventRepository extends MongoRepository<PaymentAuditEvent, String> {

    List<PaymentAuditEvent> findBySaleIdOrderByTimestampAsc(Long saleId);

    List<PaymentAuditEvent> findBySaleIdAndActionNotInOrderByTimestampAsc(Long saleId, java.util.Collection<String> actions);
}