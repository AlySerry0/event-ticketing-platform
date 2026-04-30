package com.team7.eventticketing.ticket.service;
import com.team7.eventticketing.ticket.adapter.EventSummaryAdapter;
import com.team7.eventticketing.ticket.adapter.UnusedTicketAdapter;
import com.team7.eventticketing.ticket.dto.BatchTicketRequestDTO;

import com.team7.eventticketing.ticket.dto.NearbyTicketDTO;
import com.team7.eventticketing.ticket.dto.IssueTicketDTO;
import com.team7.eventticketing.ticket.dto.EventAttendanceSummaryDTO;
import com.team7.eventticketing.ticket.dto.TicketDTO;
import com.team7.eventticketing.ticket.dto.UnusedTicketDTO;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.TicketStatus;
import com.team7.eventticketing.ticket.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.team7.eventticketing.ticket.adapter.CassandraRowAdapter;
import com.team7.eventticketing.ticket.dto.TicketScanDTO;
import com.team7.eventticketing.ticket.model.cassandra.TicketScanEvent;
import com.team7.eventticketing.ticket.repository.cassandra.TicketScanEventRepository;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import com.team7.eventticketing.ticket.observer.EntityObserver;
import com.team7.eventticketing.ticket.observer.EntitySubject;
import com.team7.eventticketing.ticket.observer.MongoEventLogger;
import com.team7.eventticketing.ticket.util.CacheInvalidationService;
import java.util.Map;

@Service
public class TicketService implements EntitySubject {

    @Autowired
    private final TicketRepository ticketRepository;


    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final CacheInvalidationService cacheInvalidationService;
    private final EventSummaryAdapter eventSummaryAdapter;
    private final UnusedTicketAdapter unusedTicketAdapter;
    private final TicketScanEventRepository ticketScanEventRepository;
    private final CassandraRowAdapter cassandraRowAdapter;

    @Autowired
    public TicketService(MongoEventLogger mongoEventLogger, TicketRepository ticketRepository, CacheInvalidationService cacheInvalidationService, EventSummaryAdapter eventSummaryAdapter, UnusedTicketAdapter unusedTicketAdapter, TicketScanEventRepository ticketScanEventRepository, CassandraRowAdapter cassandraRowAdapter) {
        this.ticketRepository = ticketRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.eventSummaryAdapter = eventSummaryAdapter;
        this.ticketScanEventRepository = ticketScanEventRepository;
        this.unusedTicketAdapter = unusedTicketAdapter;
        this.cassandraRowAdapter = cassandraRowAdapter;
        this.register(mongoEventLogger);
    }

    @Override
    public void register(EntityObserver o) {
        observers.add(o);
    }

    @Override
    public void unregister(EntityObserver o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers(String action, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(action, payload);
        }
    }


    public TicketDTO save(TicketDTO ticketDTO) {

        if (!ticketRepository.existsBookingById(ticketDTO.getBookingId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (ticketDTO.getId() == null && ticketDTO.getTicketCode() != null) {
            if (ticketRepository.existsByTicketCode(ticketDTO.getTicketCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists");
            }
        }
        ticketDTO.setIssuedAt(LocalDateTime.now());
        ticketDTO.setStatus(TicketStatus.VALID);
        Ticket ticket = convertToEntity(ticketDTO);
        Ticket savedTicket = ticketRepository.save(ticket);
        
        this.notifyObservers("TICKET_CREATED", Map.of("ticketId", savedTicket.getId(), "status", savedTicket.getStatus().name()));
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::*");

        return convertToDTO(savedTicket);
    }

    public Optional<TicketDTO> findById(Long id) {
        return ticketRepository.findById(id).map(this::convertToDTO);
    }

    public List<TicketDTO> findAll() {
        return ticketRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public void deleteById(Long id) {
        ticketRepository.deleteById(id);
        
        this.notifyObservers("TICKET_DELETED", Map.of("ticketId", id));
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::" + id);
    }

    public TicketDTO convertToDTO(Ticket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setBookingId(ticket.getBookingId());
        dto.setAttendeeName(ticket.getAttendeeName());
        dto.setTicketCode(ticket.getTicketCode());
        dto.setStatus(ticket.getStatus());
        dto.setIssuedAt(ticket.getIssuedAt());
        dto.setMetadata(ticket.getMetadata());
        return dto;
    }

    public Ticket convertToEntity(TicketDTO dto) {
        Ticket ticket = new Ticket();
        ticket.setId(dto.getId());
        ticket.setBookingId(dto.getBookingId());
        ticket.setAttendeeName(dto.getAttendeeName());
        ticket.setTicketCode(dto.getTicketCode());
        ticket.setStatus(dto.getStatus());
        ticket.setIssuedAt(dto.getIssuedAt());
        ticket.setMetadata(dto.getMetadata());
        return ticket;
    }

    @Cacheable(cacheNames = "S4-F8", key = "#eventId")
    public EventAttendanceSummaryDTO getEventSummary(Long eventId) {
      List<Object[]> results = ticketRepository.getEventAttendanceSummary(eventId);
      if (results == null || results.isEmpty()) {
          throw new RuntimeException("No tickets found");
      }
      EventAttendanceSummaryDTO dto = eventSummaryAdapter.convert(results.get(0));

      if (dto.getTotalTickets() == 0) {
          throw new RuntimeException("No tickets found");
      }

      return dto;
    }

    @Transactional
    public int purgeOldTickets(int olderThanDays) {
        if (olderThanDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "olderThanDays must be greater than 0");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        int deletedCount = ticketRepository.deleteOldExpiredOrCancelled(cutoff);
        
        this.notifyObservers("OLD_DATA_PURGED", Map.of("olderThanDays", olderThanDays, "deletedCount", deletedCount));
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        
        return deletedCount;
    }

    public List<NearbyTicketDTO> getNearbyTickets(double lat, double lon, double radiusKm) {
        if (radiusKm < 0) {
            throw new IllegalArgumentException("radiusKm must be non-negative");
        }
        List<Object[]> results = ticketRepository.findNearbyTicketsNative(lat, lon, radiusKm);
        return results.stream().map(row -> new NearbyTicketDTO(
                ((Number) row[0]).longValue(),
                (String) row[1],
                ((Number) row[2]).longValue(),
                (String) row[3],
                (Double) row[4],
                (Double) row[5],
                (Double) row[6])).toList();
    }

    @Transactional
    public TicketDTO issueTicket(Long bookingId, IssueTicketDTO request) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (ticketRepository.existsByTicketCode(request.getTicketCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists");
        }

        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setAttendeeName(request.getAttendeeName());
        ticket.setTicketCode(request.getTicketCode());
        ticket.setMetadata(request.getMetadata());
        ticket.setStatus(TicketStatus.VALID);
        Ticket savedTicket = ticketRepository.save(ticket);
        
        this.notifyObservers("TICKET_ISSUED", Map.of("ticketId", savedTicket.getId(), "status", savedTicket.getStatus().name()));
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::" + savedTicket.getId());

        return convertToDTO(savedTicket);
    }

    public TicketDTO getLatestTicketForBooking(Long bookingId) {
        if (!ticketRepository.existsBookingById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return ticketRepository.findFirstByBookingIdOrderByIssuedAtDesc(bookingId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for booking"));
    }

    @Cacheable(cacheNames = "S4-F9", key = "'upcoming-unused'")
    @Transactional(readOnly = true)
    public List<UnusedTicketDTO> getUnusedTicketsForUpcomingEvents() {
        List<Object[]> rows = ticketRepository.findUnusedTicketsForUpcomingEvents();
        if (rows == null || rows.isEmpty()) {
            return List.of(); // or throw if your spec requires 404
        }

        return rows.stream()
                .map(unusedTicketAdapter::convert)
                .toList();
    }
    @Cacheable(value = "S4-F5", key = "#key + '|' + #operator + '|' + #value") 
    public List<TicketDTO> filterTicketsByMetadata(String key, String operator, String value) {
        List<String> validOperators = List.of("eq", "gt", "lt");
        if (!validOperators.contains(operator)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operator. Must be eq, gt, or lt");
        }

        List<Ticket> matchingTickets = switch (operator) {
            case "eq" -> ticketRepository.findByMetadataEquals(key, value);
            case "gt" -> ticketRepository.findByMetadataGreaterThan(key, value);
            case "lt" -> ticketRepository.findByMetadataLessThan(key, value);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operator");
        };

        return matchingTickets.stream().map(this::convertToDTO).toList();
    }

    @Transactional
    public int issueBatchTickets(BatchTicketRequestDTO batchRequest) {
        if (!ticketRepository.existsBookingById(batchRequest.getBookingId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        List<IssueTicketDTO> ticketRequests = batchRequest.getTickets();

        List<String> incomingTicketCodes = ticketRequests.stream()
                .map(IssueTicketDTO::getTicketCode)
                .toList();

        long uniqueCount = incomingTicketCodes.stream().distinct().count();
        if (uniqueCount < incomingTicketCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate ticket codes found in batch");
        }

        List<Ticket> existingTickets = ticketRepository.findByTicketCodeIn(incomingTicketCodes);
        if (!existingTickets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate ticket codes found in database");
        }

        List<Ticket> ticketsToSave = ticketRequests.stream().map(ticketRequest -> {
            Ticket newTicket = new Ticket();
            newTicket.setBookingId(batchRequest.getBookingId());
            newTicket.setAttendeeName(ticketRequest.getAttendeeName());
            newTicket.setTicketCode(ticketRequest.getTicketCode());
            newTicket.setMetadata(ticketRequest.getMetadata());
            newTicket.setStatus(TicketStatus.VALID);
            newTicket.setIssuedAt(LocalDateTime.now());
            return newTicket;
        }).toList();

        List<Ticket> savedTickets = ticketRepository.saveAll(ticketsToSave);
        
        this.notifyObservers("BATCH_ISSUED", Map.of("bookingId", batchRequest.getBookingId(), "size", savedTickets.size()));
        
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::*");
        
        return savedTickets.size();
    }
    @Cacheable(value = "S4-F6", key = "#startDate + '|' + #endDate + '|' + #ticketStatusInput")
    public List<TicketDTO> getTicketsInDateRange(String startDate, String endDate, String ticketStatusInput) {

        LocalDateTime startDateTime = parseFlexibleDate(startDate, true);
        LocalDateTime endDateTime = parseFlexibleDate(endDate, false);

        if (startDateTime.isAfter(endDateTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate must be before or equal to endDate"
            );
        }

        if (ticketStatusInput != null && !ticketStatusInput.trim().isEmpty()) {
            TicketStatus ticketStatus;
            try {
                ticketStatus = TicketStatus.valueOf(ticketStatusInput.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket status: " + ticketStatusInput);
            }
            return ticketRepository.findByStatusAndIssuedAtBetweenOrderByIssuedAtAsc(ticketStatus, startDateTime, endDateTime)
                    .stream().map(this::convertToDTO).toList();
        }

        return ticketRepository.findByIssuedAtBetweenOrderByIssuedAtAsc(startDateTime, endDateTime)
                .stream().map(this::convertToDTO).toList();
    }

    private LocalDateTime parseFlexibleDate(String input, boolean isStart) {
        if (input == null || input.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date parameter is required");
        }

        try {
            return LocalDateTime.parse(input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }

        try {
            LocalDate date = LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE);
            return isStart ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception ignored) {
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid date format. Use yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss"
        );
    }

    @Transactional
    public Optional<TicketDTO> updateTicket(Long id, TicketDTO ticketDetails) {
        return ticketRepository.findById(id).map(ticket -> {
            // Uniqueness check if ticket code is changing
            if (ticketDetails.getTicketCode() != null && !ticketDetails.getTicketCode().equals(ticket.getTicketCode())) {
                if (ticketRepository.existsByTicketCode(ticketDetails.getTicketCode())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists");
                }
                ticket.setTicketCode(ticketDetails.getTicketCode());
            }

            if (ticketDetails.getBookingId() != null)
                ticket.setBookingId(ticketDetails.getBookingId());
            if (ticketDetails.getAttendeeName() != null)
                ticket.setAttendeeName(ticketDetails.getAttendeeName());
            if (ticketDetails.getStatus() != null)
                ticket.setStatus(ticketDetails.getStatus());

            // Preservation: Only update issuedAt if explicitly provided and not null
            if (ticketDetails.getIssuedAt() != null)
                ticket.setIssuedAt(ticketDetails.getIssuedAt());

            // Handle metadata merge
            if (ticketDetails.getMetadata() != null) {
                if (ticket.getMetadata() == null) {
                    ticket.setMetadata(ticketDetails.getMetadata());
                } else {
                    ticket.getMetadata().putAll(ticketDetails.getMetadata());
                }
            }

            Ticket savedTicket = ticketRepository.saveAndFlush(ticket);
            
            this.notifyObservers("TICKET_UPDATED", Map.of("ticketId", savedTicket.getId(), "status", savedTicket.getStatus().name()));
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::" + id);

            return convertToDTO(savedTicket);
        });
    }

    @Cacheable(value = "S4-F12", key = "#ticketId")
    public List<TicketScanDTO> getTicketScanHistory(Long ticketId, LocalDateTime startTime, LocalDateTime endTime) {
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        List<TicketScanEvent> events;
        if (startTime != null && endTime != null) {
            events = ticketScanEventRepository.findByTicketIdAndTimestampBetween(ticketId, startTime, endTime);
        } else {
            events = ticketScanEventRepository.findByTicketId(ticketId);
        }

        return events.stream()
                .map(cassandraRowAdapter::adapt)
                .toList();
    }
}


