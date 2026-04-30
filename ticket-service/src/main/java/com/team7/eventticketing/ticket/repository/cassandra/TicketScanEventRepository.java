package com.team7.eventticketing.ticket.repository.cassandra;

import com.team7.eventticketing.ticket.model.cassandra.TicketScanEvent;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.core.mapping.MapId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketScanEventRepository 
        extends CassandraRepository<TicketScanEvent, MapId> {

    List<TicketScanEvent> findByTicketId(Long ticketId);

    List<TicketScanEvent> findByTicketIdAndTimestampBetween(
            Long ticketId, LocalDateTime start, LocalDateTime end);
}