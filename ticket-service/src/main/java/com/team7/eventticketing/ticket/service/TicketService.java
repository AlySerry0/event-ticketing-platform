package com.team7.eventticketing.ticket.service;

import com.team7.eventticketing.contracts.events.TicketCancelledEvent;
import com.team7.eventticketing.contracts.events.TicketIssuedEvent;
import com.team7.eventticketing.contracts.events.TicketStatusChangedEvent;
import com.team7.eventticketing.contracts.feign.BookingServiceClient;
import com.team7.eventticketing.contracts.feign.EventServiceClient;
import com.team7.eventticketing.contracts.dto.BookingDTO;
import com.team7.eventticketing.contracts.dto.EventDTO;
import com.team7.eventticketing.contracts.dto.VenueCoordsDTO;
import com.team7.eventticketing.ticket.adapter.*;
import com.team7.eventticketing.ticket.dto.*;
import com.team7.eventticketing.ticket.dto.BatchTicketRequestDTO;
import com.team7.eventticketing.ticket.messaging.publishers.TicketEventPublisher;
import com.team7.eventticketing.ticket.model.Ticket;
import com.team7.eventticketing.ticket.model.TicketStatus;
import com.team7.eventticketing.ticket.repository.TicketRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.team7.eventticketing.ticket.dto.TicketScanDTO;
import com.team7.eventticketing.ticket.model.cassandra.TicketScanEvent;
import com.team7.eventticketing.ticket.repository.cassandra.TicketScanEventRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.team7.eventticketing.ticket.observer.EntityObserver;
import com.team7.eventticketing.ticket.observer.EntitySubject;
import com.team7.eventticketing.ticket.observer.MongoEventLogger;
import com.team7.eventticketing.ticket.util.CacheInvalidationService;

@Service
public class TicketService implements EntitySubject {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    @Autowired
    private final TicketRepository ticketRepository;

    @Autowired
    @Lazy
    private TicketService self;

    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
    private final CacheInvalidationService cacheInvalidationService;
    private final EventSummaryAdapter eventSummaryAdapter;
    private final UnusedTicketAdapter unusedTicketAdapter;
    private final TicketScanEventRepository ticketScanEventRepository;
    private final CassandraRowAdapter cassandraRowAdapter;
    private final TicketScanEventAdapter ticketScanEventAdapter;
    private final TicketAnalyticsAdapter ticketAnalyticsAdapter;
    private final MongoEventLogger mongoEventLogger;
    private final NearbyTicketAdapter nearbyTicketAdapter;
    private final BookingServiceClient bookingServiceClient;
    private final EventServiceClient eventServiceClient;
    private final TicketEventPublisher ticketEventPublisher;

    @Autowired
    public TicketService(
        MongoEventLogger mongoEventLogger,
        TicketRepository ticketRepository,
        CacheInvalidationService cacheInvalidationService,
        EventSummaryAdapter eventSummaryAdapter,
        TicketAnalyticsAdapter ticketAnalyticsAdapter,
        UnusedTicketAdapter unusedTicketAdapter,
        TicketScanEventRepository ticketScanEventRepository,
        CassandraRowAdapter cassandraRowAdapter,
        TicketScanEventAdapter ticketScanEventAdapter,
        NearbyTicketAdapter nearbyTicketAdapter,
        BookingServiceClient bookingServiceClient,
        EventServiceClient eventServiceClient,
        TicketEventPublisher ticketEventPublisher
    ) {
        this.mongoEventLogger = mongoEventLogger;
        this.ticketRepository = ticketRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.eventSummaryAdapter = eventSummaryAdapter;
        this.ticketAnalyticsAdapter = ticketAnalyticsAdapter;
        this.unusedTicketAdapter = unusedTicketAdapter;
        this.ticketScanEventRepository = ticketScanEventRepository;
        this.cassandraRowAdapter = cassandraRowAdapter;
        this.ticketScanEventAdapter = ticketScanEventAdapter;
        this.nearbyTicketAdapter = nearbyTicketAdapter;
        this.bookingServiceClient = bookingServiceClient;
        this.eventServiceClient = eventServiceClient;
        this.ticketEventPublisher = ticketEventPublisher;
        register(mongoEventLogger);
    }

    @Override
    public void register(EntityObserver o) { observers.add(o); }

    @Override
    public void unregister(EntityObserver o) { observers.remove(o); }

    @Override
    public void notifyObservers(String action, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(action, payload);
        }
    }

    public TicketDTO save(TicketDTO ticketDTO) {
        BookingDTO booking;
        try {
            booking = bookingServiceClient.getBooking(ticketDTO.getBookingId());
        } catch (FeignException.NotFound e) {
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
        ticket.setEventId(booking.eventId());
        Ticket savedTicket = ticketRepository.save(ticket);

        this.notifyObservers("TICKET_CREATED", Map.of("ticketId", savedTicket.getId(), "status", savedTicket.getStatus().name()));
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::*");

        return convertToDTO(savedTicket);
    }

    @Cacheable(value = "ticket", key = "#id")
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
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");
    }

    public TicketDTO convertToDTO(Ticket ticket) {
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setBookingId(ticket.getBookingId());
        dto.setEventId(ticket.getEventId());
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
        ticket.setEventId(dto.getEventId());
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

    public int getUsedTicketCount(Long bookingId) {
        return ticketRepository.countUsedTicketsByBookingId(bookingId);
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
        cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");

        return deletedCount;
    }

    @Cacheable(value = "S4-F3", key = "#lat + '_' + #lon + '_' + #radiusKm")
    public List<NearbyTicketDTO> getNearbyTickets(double lat, double lon, double radiusKm) {
        if (radiusKm < 0) {
            throw new IllegalArgumentException("radiusKm must be non-negative");
        }

        List<Ticket> validTickets = ticketRepository.findByStatusAndEventIdIsNotNull(TicketStatus.VALID);
        Map<Long, List<Ticket>> byEventId = validTickets.stream()
                .collect(Collectors.groupingBy(Ticket::getEventId));

        List<NearbyTicketDTO> result = new ArrayList<>();

        for (Map.Entry<Long, List<Ticket>> entry : byEventId.entrySet()) {
            Long eventId = entry.getKey();
            try {
                VenueCoordsDTO coords = eventServiceClient.getEventVenueCoords(eventId);
                if (coords == null || coords.venueLat() == null || coords.venueLon() == null) continue;

                double distance = Math.sqrt(
                        Math.pow(coords.venueLat() - lat, 2) +
                        Math.pow(coords.venueLon() - lon, 2)
                ) * 111.0;

                if (distance > radiusKm) continue;

                EventDTO event;
                try {
                    event = eventServiceClient.getEvent(eventId);
                } catch (Exception ex) {
                    log.warn("Could not fetch event {} name for nearby tickets: {}", eventId, ex.getMessage());
                    event = null;
                }
                String eventName = event != null ? event.name() : "Unknown";

                for (Ticket ticket : entry.getValue()) {
                    result.add(NearbyTicketDTO.builder()
                            .ticketId(ticket.getId())
                            .attendeeName(ticket.getAttendeeName())
                            .bookingId(ticket.getBookingId())
                            .eventName(eventName)
                            .eventLat(coords.venueLat())
                            .eventLon(coords.venueLon())
                            .distanceKm(distance)
                            .build());
                }
            } catch (FeignException e) {
                log.warn("Could not fetch venue coords for eventId {}: {}", eventId, e.getMessage());
            }
        }

        result.sort(Comparator.comparing(NearbyTicketDTO::getDistanceKm));
        return result;
    }

    @Transactional
    public TicketDTO issueTicket(Long bookingId, IssueTicketDTO request) {
        BookingDTO booking;
        try {
            booking = bookingServiceClient.getBooking(bookingId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (ticketRepository.existsByTicketCode(request.getTicketCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket code already exists");
        }

        Ticket ticket = new Ticket();
        ticket.setBookingId(bookingId);
        ticket.setEventId(booking.eventId());
        ticket.setAttendeeName(request.getAttendeeName());
        ticket.setTicketCode(request.getTicketCode());
        ticket.setMetadata(request.getMetadata());
        ticket.setStatus(TicketStatus.VALID);
        Ticket savedTicket = ticketRepository.save(ticket);

        ticketEventPublisher.publishTicketIssued(new TicketIssuedEvent(
                savedTicket.getId(),
                savedTicket.getBookingId(),
                savedTicket.getAttendeeName(),
                savedTicket.getTicketCode()));


        this.notifyObservers("TICKET_ISSUED", Map.of("ticketId", savedTicket.getId(), "status", savedTicket.getStatus().name()));
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::" + savedTicket.getId());

        return convertToDTO(savedTicket);
    }

    @Cacheable(value = "S4-F1", key = "#bookingId")
    public TicketDTO getLatestTicketForBooking(Long bookingId) {
        try {
            bookingServiceClient.getBooking(bookingId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return ticketRepository.findFirstByBookingIdOrderByIssuedAtDesc(bookingId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tickets found for booking"));
    }

    @Cacheable(cacheNames = "S4-F9", key = "'upcoming-unused'")
    @Transactional(readOnly = true)
    public List<UnusedTicketDTO> getUnusedTicketsForUpcomingEvents() {
        List<Ticket> validTickets = ticketRepository.findByStatusAndEventIdIsNotNull(TicketStatus.VALID);
        if (validTickets.isEmpty()) return List.of();

        Map<Long, List<Ticket>> byEventId = validTickets.stream()
                .collect(Collectors.groupingBy(Ticket::getEventId));

        List<UnusedTicketDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<Ticket>> entry : byEventId.entrySet()) {
            Long eventId = entry.getKey();
            try {
                EventDTO event = eventServiceClient.getEvent(eventId);
                if (event == null || !"UPCOMING".equals(event.status())) continue;

                for (Ticket ticket : entry.getValue()) {
                    result.add(UnusedTicketDTO.builder()
                            .ticketId(ticket.getId())
                            .attendeeName(ticket.getAttendeeName())
                            .ticketCode(ticket.getTicketCode())
                            .bookingId(ticket.getBookingId())
                            .eventName(event.name())
                            .eventDate(event.eventDate())
                            .build());
                }
            } catch (FeignException e) {
                log.warn("Could not fetch event {} for unused tickets check: {}", eventId, e.getMessage());
            }
        }
        return result;
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
        BookingDTO booking;
        try {
            booking = bookingServiceClient.getBooking(batchRequest.getBookingId());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        if (batchRequest.getTickets() == null || batchRequest.getTickets().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket list cannot be empty");
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

        Long eventId = booking.eventId();
        List<Ticket> ticketsToSave = ticketRequests.stream().map(ticketRequest -> {
            Ticket newTicket = new Ticket();
            newTicket.setBookingId(batchRequest.getBookingId());
            newTicket.setEventId(eventId);
            newTicket.setAttendeeName(ticketRequest.getAttendeeName());
            newTicket.setTicketCode(ticketRequest.getTicketCode());
            newTicket.setMetadata(ticketRequest.getMetadata());
            newTicket.setStatus(TicketStatus.VALID);
            newTicket.setIssuedAt(LocalDateTime.now());
            return newTicket;
        }).toList();

        List<Ticket> savedTickets = ticketRepository.saveAll(ticketsToSave);
        savedTickets.forEach(t -> ticketEventPublisher.publishTicketIssued(
                new TicketIssuedEvent(t.getId(), t.getBookingId(), t.getAttendeeName(), t.getTicketCode())));


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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
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

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid date format. Use yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss");
    }

    @Transactional
    public Optional<TicketDTO> updateTicket(Long id, TicketDTO ticketDetails) {
        return ticketRepository.findById(id).map(ticket -> {
            TicketStatus previousStatus = ticket.getStatus();
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
            if (ticketDetails.getIssuedAt() != null)
                ticket.setIssuedAt(ticketDetails.getIssuedAt());

            if (ticketDetails.getMetadata() != null) {
                if (ticket.getMetadata() == null) {
                    ticket.setMetadata(ticketDetails.getMetadata());
                } else {
                    ticket.getMetadata().putAll(ticketDetails.getMetadata());
                }
            }

            Ticket savedTicket = ticketRepository.saveAndFlush(ticket);

            if (ticketDetails.getStatus() != null && ticketDetails.getStatus() != previousStatus) {
                ticketEventPublisher.publishTicketStatusChanged(new TicketStatusChangedEvent(
                        savedTicket.getId(),
                        savedTicket.getBookingId(),
                        savedTicket.getStatus().name()));
            }

            this.notifyObservers("TICKET_UPDATED", Map.of("ticketId", savedTicket.getId(), "status", savedTicket.getStatus().name()));
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
            cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::" + id);
            cacheInvalidationService.invalidateCacheWildcard("event-service::S2-F12::*");

            return convertToDTO(savedTicket);
        });
    }

    public TicketAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }
        notifyObservers("ANALYTICS_VIEWED", Map.of(
                "startDate", startDate.toString(),
                "endDate", endDate.toString()
        ));
        return self.getAnalyticsCached(startDate, endDate);
    }

    @Cacheable(value = "S4-F10", key = "#startDate.toString() + '_' + #endDate.toString()")
    public TicketAnalyticsDTO getAnalyticsCached(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59, 999_000_000);
        List<Object[]> results = ticketRepository.getTicketAnalytics(start, end);
        Object[] row = (results != null && !results.isEmpty())
                ? results.get(0)
                : new Object[]{0L, 0L, 0L, 0L, 0L};
        return ticketAnalyticsAdapter.convert(row);
    }

    @Transactional
    public void recordTicketScan(Long ticketId, TicketScanDTO scanDTO) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        TicketScanEvent scanEvent = ticketScanEventAdapter.adaptToEvent(ticketId, ticket, scanDTO);
        ticketScanEventRepository.save(scanEvent);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("scanType", scanDTO.getScanType());
        payload.put("gate", scanDTO.getGate());
        payload.put("section", scanDTO.getSection());
        payload.put("seatNumber", scanDTO.getSeatNumber());
        payload.put("notes", scanDTO.getNotes());
        payload.put("action", "TRACKING_RECORDED");

        this.notifyObservers("TRACKING_RECORDED", payload);

        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F12::" + ticketId);
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
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

        return events.stream().map(cassandraRowAdapter::adapt).toList();
    }

    @Transactional
    public void captureEventIdForBooking(Long bookingId, Long eventId) {
        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        for (Ticket ticket : tickets) {
            if (ticket.getEventId() == null) {
                ticket.setEventId(eventId);
                ticketRepository.save(ticket);
                log.debug("captureEventId: set eventId={} on ticketId={}", eventId, ticket.getId());
            }
        }
    }

    @Transactional(readOnly = true)
    public void auditTicketsForCompletedBooking(Long bookingId) {
        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        for (Ticket ticket : tickets) {
            ticketEventPublisher.publishTicketStatusChanged(new TicketStatusChangedEvent(
                    ticket.getId(),
                    ticket.getBookingId(),
                    ticket.getStatus().name()));
            log.debug("auditTickets: published ticket.status-changed (audit) for ticketId={}", ticket.getId());
        }
    }


    @Transactional
    public void cancelTicketsForBooking(Long bookingId) {
        List<Ticket> tickets = ticketRepository.findByBookingId(bookingId);
        for (Ticket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.CANCELLED) {
                log.debug("cancelTickets: ticketId={} already CANCELLED — skipping", ticket.getId());
                continue;
            }

            String previousStatus = ticket.getStatus().name();
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);

            ticketEventPublisher.publishTicketCancelled(
                    new TicketCancelledEvent(ticket.getId(), ticket.getBookingId()));

            ticketEventPublisher.publishTicketStatusChanged(new TicketStatusChangedEvent(
                    ticket.getId(),
                    ticket.getBookingId(),
                    TicketStatus.CANCELLED.name()));

            log.info("cancelTickets: ticketId={} cancelled (was {})", ticket.getId(), previousStatus);
        }

        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F10::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F5::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::S4-F6::*");
        cacheInvalidationService.invalidateCacheWildcard("ticket-service::ticket::*");
    }
}
