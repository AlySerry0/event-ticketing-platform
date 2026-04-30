package com.team7.eventticketing.booking.service;

import com.team7.eventticketing.booking.model.Booking;
import com.team7.eventticketing.booking.model.BookingStatus;
import com.team7.eventticketing.booking.model.neo4j.AttendedRelationship;
import com.team7.eventticketing.booking.model.neo4j.EventNode;
import com.team7.eventticketing.booking.model.neo4j.UserNode;
import com.team7.eventticketing.booking.repository.BookingRepository;
import com.team7.eventticketing.booking.repository.EventNodeRepository;
import com.team7.eventticketing.booking.repository.UserNodeRepository;
import com.team7.eventticketing.booking.observer.MongoEventLogger;
import com.team7.eventticketing.booking.util.CacheInvalidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceAttendanceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserNodeRepository userNodeRepository;

    @Mock
    private EventNodeRepository eventNodeRepository;

    @Mock
    private MongoEventLogger mongoEventLogger;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private BookingItemService bookingItemService;

    @InjectMocks
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // The constructor registers the logger
        bookingService = new BookingService(mongoEventLogger, cacheInvalidationService);
        // Need to manually inject other mocks because we used the constructor for the logger
        try {
            java.lang.reflect.Field repoField = BookingService.class.getDeclaredField("bookingRepository");
            repoField.setAccessible(true);
            repoField.set(bookingService, bookingRepository);

            java.lang.reflect.Field userNodeRepoField = BookingService.class.getDeclaredField("userNodeRepository");
            userNodeRepoField.setAccessible(true);
            userNodeRepoField.set(bookingService, userNodeRepository);

            java.lang.reflect.Field eventNodeRepoField = BookingService.class.getDeclaredField("eventNodeRepository");
            eventNodeRepoField.setAccessible(true);
            eventNodeRepoField.set(bookingService, eventNodeRepository);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void recordAttendance_Success() {
        Long bookingId = 1L;
        Long userId = 10L;
        Long eventId = 20L;

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(userNodeRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(bookingRepository.findUserNameById(userId)).thenReturn("John Doe");
        when(eventNodeRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(bookingRepository.findEventDetailsById(eventId)).thenReturn(new Object[][]{{"Concert", "MUSIC", LocalDateTime.now()}});

        bookingService.recordAttendance(bookingId);

        verify(userNodeRepository, times(1)).save(any(UserNode.class));
        verify(mongoEventLogger, times(1)).onEvent(eq("INTERACTION_RECORDED"), any());
    }

    @Test
    void recordAttendance_Idempotency() {
        Long bookingId = 1L;
        Long userId = 10L;
        Long eventId = 20L;

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setStatus(BookingStatus.COMPLETED);

        UserNode userNode = new UserNode();
        userNode.setUserId(userId);
        
        EventNode eventNode = new EventNode();
        eventNode.setEventId(eventId);
        
        AttendedRelationship rel = new AttendedRelationship();
        rel.setEvent(eventNode);
        rel.getRecordedBookingIds().add(bookingId);
        userNode.getAttendedEvents().add(rel);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(userNodeRepository.findByUserId(userId)).thenReturn(Optional.of(userNode));

        bookingService.recordAttendance(bookingId);

        verify(userNodeRepository, never()).save(any(UserNode.class));
        verify(mongoEventLogger, never()).onEvent(eq("INTERACTION_RECORDED"), any());
    }

    @Test
    void recordAttendance_WrongStatus() {
        Long bookingId = 1L;
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(ResponseStatusException.class, () -> bookingService.recordAttendance(bookingId));
    }

    @Test
    void recordAttendance_NoEvent() {
        Long bookingId = 1L;
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setEventId(null);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(ResponseStatusException.class, () -> bookingService.recordAttendance(bookingId));
    }
}
