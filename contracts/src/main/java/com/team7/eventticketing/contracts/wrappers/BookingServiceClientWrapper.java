package com.team7.eventticketing.contracts.wrappers;

import com.team7.eventticketing.contracts.feign.BookingServiceClient;
import com.team7.eventticketing.contracts.dto.UserBookingSummaryDTO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wraps BookingServiceClient with:
 *  - try-catch on every call (downstream failure never crashes user-service)
 *  - Automatic correlation ID forwarding from MDC
 *  - Structured logging of Feign errors
 *
 * USAGE: Inject BookingServiceClientWrapper, NOT BookingServiceClient directly.
 */
@Component
public class BookingServiceClientWrapper {

    private static final Logger log =
        LoggerFactory.getLogger(BookingServiceClientWrapper.class);

    private final BookingServiceClient client;

    public BookingServiceClientWrapper(BookingServiceClient client) {
        this.client = client;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Reads the current JWT from MDC (set by JwtAuthenticationFilter). */
    private String token() {
        String t = MDC.get("jwtToken");
        return t != null ? "Bearer " + t : "";
    }

    private String correlationId() {
        return MDC.get("correlationId");
    }

    // ── wrapped calls ────────────────────────────────────────────────────

    /**
     * Returns booking summary for a user.
     * Falls back to BookingSummaryDTO.empty() if booking-service is down or
     * returns 404 (user has no bookings).
     */
    public UserBookingSummaryDTO getUserBookingSummary(Long userId) {
        try {
            return client.getUserBookingSummary(userId, token(), correlationId());

        } catch (FeignException.NotFound e) {
            log.debug("booking-service: no bookings found for userId={}", userId);
            return new UserBookingSummaryDTO();

        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            log.warn("booking-service auth error for userId={}: {}", userId, e.getMessage());
            // Propagate auth errors — the upstream request had a bad token
            throw new ResponseStatusException(HttpStatus.valueOf(e.status()), e.getMessage());

        } catch (FeignException e) {
            log.warn("booking-service unavailable for userId={}: status={} message={}",
                     userId, e.status(), e.getMessage());
            // Degrade gracefully — return empty summary rather than 500
            return new UserBookingSummaryDTO();

        } catch (Exception e) {
            log.error("Unexpected error calling booking-service for userId={}: {}",
                      userId, e.getMessage(), e);
            return new UserBookingSummaryDTO();
        }
    }

//    /**
//     * Returns count of active bookings.
//     * Returns 0 on any error so deactivation logic defaults to "no active bookings".
//     * IMPORTANT: callers that use this for a hard guard (S1-F4) should treat
//     * a -1 return as "unknown — cannot deactivate safely".
//     */
//    public int getActiveBookingCount(Long userId) {
//        try {
//            return client.getActiveBookingCount(userId, token(), correlationId());
//
//        } catch (FeignException.NotFound e) {
//            return 0;
//
//        } catch (FeignException e) {
//            log.warn("booking-service unavailable for active-count userId={}: {}",
//                     userId, e.getMessage());
//            // Return -1 so callers know the count is unknown, not zero
//            return -1;
//
//        } catch (Exception e) {
//            log.error("Unexpected error getting active booking count for userId={}: {}",
//                      userId, e.getMessage(), e);
//            return -1;
//        }
//    }
//
//    /**
//     * Returns total booking count. Returns 0 on error.
//     */
//    public long getTotalBookingCount(Long userId) {
//        try {
//            return client.getTotalBookingCount(userId, token(), correlationId());
//
//        } catch (FeignException.NotFound e) {
//            return 0L;
//
//        } catch (FeignException e) {
//            log.warn("booking-service unavailable for total-count userId={}: {}",
//                     userId, e.getMessage());
//            return 0L;
//
//        } catch (Exception e) {
//            log.error("Unexpected error getting total booking count for userId={}: {}",
//                      userId, e.getMessage(), e);
//            return 0L;
//        }
//    }
}