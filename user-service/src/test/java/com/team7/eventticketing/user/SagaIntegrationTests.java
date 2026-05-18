package com.team7.eventticketing.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saga integration tests – mirrors the Python happy-path smoke test step for step,
 * plus the compensation (payment failure) and pre-saga-check-failure scenarios.
 *
 * Step mapping (Python → JUnit scenario A):
 * 1 Register
 * 2 Login
 * 3 Create event
 * 4 Add event session
 * 5 Place booking (no eventId yet → status PENDING)
 * 6 Add booking items
 * 7 Confirm booking (assigns eventId, publishes booking.placed)
 * 8 Issue ticket POST /api/tickets/booking/{bookingId}
 * 9a Scan ticket POST /api/tickets/{ticketId}/scan
 * 9b Mark ticket USED
 * 10 Check-in booking → CHECKED_IN
 * 10b Advance event → ONGOING
 * 11 Complete booking → poll PAYMENT_PENDING
 * 12 Process payment POST /api/sales/booking/{bookingId}
 * 13 Poll booking → PAID
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SagaIntegrationTests {

    private static final String BASE = "http://localhost:30080";

    // Unique suffix so parallel / re-runs don't clash – mirrors Python's TS variable
    private static final String TS =
            String.valueOf(Instant.now().toEpochMilli()).substring(7); // 6 digits

    private static final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate rest = buildRestTemplate();

// ── RestTemplate that never throws on 4xx/5xx ────────────────────────────

    private static RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate(new SimpleClientHttpRequestFactory());
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false; // let tests assert status explicitly
            }
        });
        return rt;
    }

// ── Low-level helpers ────────────────────────────────────────────────────

    private String nextNonce() {
        return String.valueOf(nonce.incrementAndGet());
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank() || body.trim().equals("null")) return null;
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + body, e);
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<String> post(String path, Object body, String token) {
        HttpHeaders headers = token != null ? authHeaders(token) : jsonHeaders();
        return rest.exchange(BASE + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> put(String path, Object body, String token) {
        return rest.exchange(BASE + path, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        return rest.exchange(BASE + path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
    }

// ── Domain helpers ───────────────────────────────────────────────────────

    /**
     * Registers a fresh user and returns the JWT from the subsequent login –
     * mirrors Python steps 1 & 2.
     */
    private String registerAndLogin(String suffix) {
        String email = "alice" + suffix + "@saga.test";
        String password = "secret123";
        String phone = "+1-555-" + suffix;

// Step 1 – Register
        ResponseEntity<String> regRes = post("/api/auth/register",
                Map.of("name", "Alice Smith",
                        "email", email,
                        "password", password,
                        "phone", phone),
                null);
        assertThat(regRes.getStatusCode())
                .as("Register should return 201").isEqualTo(HttpStatus.CREATED);

// Step 2 – Login
        ResponseEntity<String> loginRes = post("/api/auth/login",
                Map.of("email", email, "password", password),
                null);
        assertThat(loginRes.getStatusCode().is2xxSuccessful())
                .as("Login should succeed").isTrue();

        JsonNode node = parse(loginRes.getBody());
        assertThat(node).isNotNull();
        assertThat(node.has("token")).isTrue();
        return node.get("token").asText();
    }

    /** Extracts the 'uid' claim from the JWT payload – mirrors Python decode_uid(). */
    private Long extractUserId(String token) {
        String payload = token.split("\\.")[1];
// pad base64 if needed
        payload = payload + "=".repeat((4 - payload.length() % 4) % 4);
        String decoded = new String(Base64.getUrlDecoder().decode(payload));
        return parse(decoded).get("uid").asLong();
    }

    /**
     * Step 3 – Create event.
     * Returns the new event id.
     */
    private Long createEvent(String token, String status, String nameSuffix) {
        ResponseEntity<String> res = post("/api/events",
                Map.of("name", "Saga Conf 2026 " + nameSuffix,
                        "venue", "Test Hall A",
                        "eventDate", "2025-01-10T09:00:00",
                        "category", "CONFERENCE",
                        "status", status),
                token);
        assertThat(res.getStatusCode())
                .as("Create event should return 201").isEqualTo(HttpStatus.CREATED);
        return parse(res.getBody()).get("id").asLong();
    }

    /**
     * Step 4 – Add a session to the event.
     * Returns the new session id.
     */
    private Long addSession(String token, Long eventId) {
        ResponseEntity<String> res = post("/api/events/" + eventId + "/sessions",
                Map.of("title", "Opening Keynote",
                        "speaker", "Dr. Jane Doe",
                        "startTime", "2026-09-15T09:00:00",
                        "endTime", "2026-09-15T10:30:00",
                        "capacity", 200),
                token);
        assertThat(res.getStatusCode())
                .as("Add session should return 201").isEqualTo(HttpStatus.CREATED);
        return parse(res.getBody()).get("id").asLong();
    }

    /**
     * Step 5 – Place a booking (no eventId yet).
     * Returns the booking id.
     */
    private Long placeBooking(String token, Long userId, String suffix) {
        ResponseEntity<String> res = post("/api/bookings",
                Map.of("userId", userId,
                        "contactEmail", "alice" + suffix + "@saga.test"),
                token);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Place booking should succeed").isTrue();
        return parse(res.getBody()).get("id").asLong();
    }

    /**
     * Step 6 – Add booking items.
     */
    private void addBookingItems(String token, Long bookingId, Long sessionId) {
        ResponseEntity<String> res = post("/api/bookings/" + bookingId + "/items",
                List.of(Map.of(
                        "sessionId", sessionId,
                        "sessionTitle", "Opening Keynote",
                        "quantity", 2,
                        "unitPrice", 75.0,
                        "eventOrder", 1)),
                token);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Add booking items should succeed").isTrue();
        JsonNode body = parse(res.getBody());
        assertThat(body.get("bookingItems").size())
                .as("Booking should have items").isGreaterThan(0);
    }

    /**
     * Step 7 – Confirm booking (assigns eventId, triggers booking.placed event).
     */
    private void confirmBooking(String token, Long bookingId, Long eventId) {
        ResponseEntity<String> res = rest.exchange(
                BASE + "/api/bookings/" + bookingId + "/confirm?eventId=" + eventId,
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)),
                String.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Confirm booking should succeed").isTrue();
        JsonNode body = parse(res.getBody());
        assertThat(body.get("status").asText())
                .as("Booking status after confirm").isNotNull();
    }

    /**
     * Step 8 – Issue ticket via POST /api/tickets/booking/{bookingId}.
     * Returns the new ticket id.
     */
    private Long issueTicket(String token, Long bookingId, String suffix) {
        ResponseEntity<String> res = post("/api/tickets/booking/" + bookingId,
                Map.of("attendeeName", "Alice Smith",
                        "ticketCode", "TKT-2026-SAGA-" + suffix),
                token);
        assertThat(res.getStatusCode())
                .as("Issue ticket should return 201").isEqualTo(HttpStatus.CREATED);
        JsonNode body = parse(res.getBody());
        assertThat(body.get("id")).isNotNull();
        return body.get("id").asLong();
    }

    /**
     * Step 9a – Scan ticket via POST /api/tickets/{ticketId}/scan.
     */
    private void scanTicket(String token, Long ticketId) {
        ResponseEntity<String> res = post("/api/tickets/" + ticketId + "/scan",
                Map.of("location", "Gate A",
                        "scannedAt", "2026-09-15T08:55:00"),
                token);
        assertThat(res.getStatusCode())
                .as("Scan ticket should return 201").isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Step 9b – Mark ticket USED via PUT /api/tickets/{ticketId}.
     */
    private void markTicketUsed(String token, Long ticketId) {
        ResponseEntity<String> res = put("/api/tickets/" + ticketId,
                Map.of("status", "USED"), token);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Mark ticket USED should succeed").isTrue();
        assertThat(parse(res.getBody()).get("status").asText())
                .as("Ticket status after update").isEqualTo("USED");
    }

    /**
     * Step 10 – Check-in booking → CHECKED_IN.
     */
    private void checkInBooking(String token, Long bookingId) {
        ResponseEntity<String> res = put("/api/bookings/" + bookingId,
                Map.of("status", "CHECKED_IN"), token);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Check-in booking should succeed").isTrue();
        assertThat(parse(res.getBody()).get("status").asText())
                .isEqualTo("CHECKED_IN");
    }

    /**
     * Step 10b – Advance event to ONGOING.
     */
    private void advanceEventToOngoing(String token, Long eventId) {
        ResponseEntity<String> res = put("/api/events/" + eventId + "/status",
                Map.of("status", "ONGOING"), token);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Advance event to ONGOING should succeed").isTrue();
    }

    /**
     * Step 11 – Trigger complete-booking saga.
     */
    private void completeBooking(String token, Long bookingId) {
        ResponseEntity<String> res = rest.exchange(
                BASE + "/api/bookings/" + bookingId + "/complete",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)),
                String.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("Complete booking should succeed").isTrue();
    }

    /**
     * Step 11 overload that asserts a specific HTTP status (used for pre-check failures).
     */
    private ResponseEntity<String> completeBookingRaw(String token, Long bookingId) {
        return rest.exchange(
                BASE + "/api/bookings/" + bookingId + "/complete",
                HttpMethod.PUT,
                new HttpEntity<>(authHeaders(token)),
                String.class);
    }

    /**
     * Step 12 – Process payment via POST /api/sales/booking/{bookingId}.
     */
    private ResponseEntity<String> processPayment(String token, Long bookingId,
                                                  boolean simulateFailure) {
        String path = "/api/sales/booking/" + bookingId
                + (simulateFailure ? "?simulateFailure=true" : "");
        return post(path,
                Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"),
                token);
    }

// ── Polling helpers ──────────────────────────────────────────────────────

    private JsonNode getBooking(String token, Long bookingId) {
        ResponseEntity<String> res = get("/api/bookings/" + bookingId, token);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return parse(res.getBody());
    }

    /** Mirrors Python poll() – sleeps 2 s between attempts. */
    private void waitForBookingStatus(String token, Long bookingId,
                                      String expected, int retries)
            throws InterruptedException {
        for (int i = 0; i < retries; i++) {
            String current = getBooking(token, bookingId).get("status").asText();
            System.out.printf(" poll [%d/%d] status=%s%n", i + 1, retries, current);
            if (expected.equals(current)) return;
            Thread.sleep(2_000);
        }
// final assertion to produce a clear failure message
        assertThat(getBooking(token, bookingId).get("status").asText())
                .as("Booking " + bookingId + " status after polling")
                .isEqualTo(expected);
    }

    private void waitForSaleStatus(String token, Long saleId,
                                   String expected, int retries)
            throws InterruptedException {
        for (int i = 0; i < retries; i++) {
            ResponseEntity<String> res = get("/api/sales/" + saleId, token);
            if (res.getStatusCode().is2xxSuccessful()) {
                JsonNode node = parse(res.getBody());
                String current = node != null ? node.get("status").asText() : "null";
                System.out.printf(" poll [%d/%d] sale status=%s%n", i + 1, retries, current);
                if (expected.equals(current)) return;
            }
            Thread.sleep(2_000);
        }
        ResponseEntity<String> res = get("/api/sales/" + saleId, token);
        assertThat(parse(res.getBody()).get("status").asText())
                .as("Sale " + saleId + " status after polling")
                .isEqualTo(expected);
    }

// ─────────────────────────────────────────────────────────────────────────
// Scenarios
// ─────────────────────────────────────────────────────────────────────────

    /**
     * Scenario A – Full happy path ending at PAID.
     *
     * Mirrors the Python smoke test step for step:
     * 1 Register → 2 Login → 3 Create event → 4 Add session →
     * 5 Place booking → 6 Add items → 7 Confirm →
     * 8 Issue ticket → 9a Scan → 9b Mark USED →
     * 10 Check-in → 10b ONGOING →
     * 11 Complete → poll PAYMENT_PENDING →
     * 12 Payment → 13 poll PAID
     */
    @Test
    @Order(1)
    void scenarioA_fullHappyPath_endsAtPaid() throws Exception {
        String suffix = TS + nextNonce();
        System.out.println("\n=== Scenario A: Full happy path ===");

// Steps 1 & 2
        System.out.println("\n[1-2] Register & Login");
        String token = registerAndLogin(suffix);
        Long userId = extractUserId(token);
        System.out.println(" userId=" + userId);

// Step 3
        System.out.println("\n[3] Create event");
        Long eventId = createEvent(token, "UPCOMING", "A-" + suffix);
        System.out.println(" eventId=" + eventId);

// Step 4
        System.out.println("\n[4] Add session");
        Long sessionId = addSession(token, eventId);
        System.out.println(" sessionId=" + sessionId);

// Step 5
        System.out.println("\n[5] Place booking");
        Long bookingId = placeBooking(token, userId, suffix);
        System.out.println(" bookingId=" + bookingId);

// Step 6
        System.out.println("\n[6] Add booking items");
        addBookingItems(token, bookingId, sessionId);

// Step 7
        System.out.println("\n[7] Confirm booking (assigns event, publishes booking.placed)");
        confirmBooking(token, bookingId, eventId);

// Step 8
        System.out.println("\n[8] Issue ticket");
        Long ticketId = issueTicket(token, bookingId, suffix);
        System.out.println(" ticketId=" + ticketId);

// Step 9a
        System.out.println("\n[9a] Scan ticket");
        scanTicket(token, ticketId);

// Step 9b
        System.out.println("\n[9b] Mark ticket USED");
        markTicketUsed(token, ticketId);

// Step 10
        System.out.println("\n[10] Check-in booking");
        checkInBooking(token, bookingId);

// Step 10b
        System.out.println("\n[10b] Advance event to ONGOING");
        advanceEventToOngoing(token, eventId);

// Step 11
        System.out.println("\n[11] Complete booking -> poll PAYMENT_PENDING");
        completeBooking(token, bookingId);
        Thread.sleep(2_000);
        waitForBookingStatus(token, bookingId, "PAYMENT_PENDING", 10);

// Step 12
        System.out.println("\n[12] Process payment");
        ResponseEntity<String> payRes = processPayment(token, bookingId, false);
        assertThat(payRes.getStatusCode())
                .as("Process payment should return 201").isEqualTo(HttpStatus.CREATED);
        JsonNode sale = parse(payRes.getBody());
        System.out.printf(" sale status=%s amount=%s%n",
                sale.get("status").asText(), sale.get("amount").asText());

// Step 13
        System.out.println("\n[13] Poll booking until PAID");
        Thread.sleep(2_000);
        waitForBookingStatus(token, bookingId, "PAID", 10);
        System.out.println(" -> PAID ✔");
    }

    /**
     * Scenario B – Payment failure triggers compensation; booking ends at REFUNDED.
     *
     * Same flow as Scenario A up to step 12, but payment is submitted with
     * simulateFailure=true so the saga compensates.
     */
    @Test
    @Order(2)
    void scenarioB_paymentFailure_compensationEndsAtRefunded() throws Exception {
        String suffix = "B" + TS + nextNonce();
        System.out.println("\n=== Scenario B: Payment failure / compensation ===");

// Steps 1 & 2
        String token = registerAndLogin(suffix);
        Long userId = extractUserId(token);

// Steps 3 & 4
        Long eventId = createEvent(token, "UPCOMING", "B-" + suffix);
        Long sessionId = addSession(token, eventId);

// Steps 5, 6, 7
        Long bookingId = placeBooking(token, userId, suffix);
        addBookingItems(token, bookingId, sessionId);
        confirmBooking(token, bookingId, eventId);

// Steps 8, 9a, 9b
        Long ticketId = issueTicket(token, bookingId, suffix);
        scanTicket(token, ticketId);
        markTicketUsed(token, ticketId);

// Steps 10 & 10b
        checkInBooking(token, bookingId);
        advanceEventToOngoing(token, eventId);

// Step 11
        System.out.println("\n[11] Complete booking -> poll PAYMENT_PENDING");
        completeBooking(token, bookingId);
        Thread.sleep(2_000);
        waitForBookingStatus(token, bookingId, "PAYMENT_PENDING", 10);

// Step 12 – force failure
        System.out.println("\n[12] Process payment (simulate failure)");
        ResponseEntity<String> payRes = processPayment(token, bookingId, true);
        assertThat(payRes.getStatusCode().is2xxSuccessful())
                .as("Simulated-failure payment call should still return 2xx").isTrue();
        Long saleId = parse(payRes.getBody()).get("id").asLong();
        System.out.println(" saleId=" + saleId);

// Poll for compensation
        System.out.println("\n[13] Poll booking until REFUNDED");
        Thread.sleep(2_000);
        waitForBookingStatus(token, bookingId, "REFUNDED", 20);
        System.out.println(" booking -> REFUNDED ✔");

        waitForSaleStatus(token, saleId, "REFUNDED", 10);
        System.out.println(" sale -> REFUNDED ✔");
    }

    /**
     * Scenario C – Pre-saga check fails when no USED ticket exists.
     *
     * The complete-booking endpoint must return 400 and the booking must stay
     * at CHECKED_IN (no state mutation).
     *
     * Mirrors Python's implicit assumption: without step 9b the saga guard
     * rejects the request before any message is published.
     */
    @Test
    @Order(3)
    void scenarioC_preSagaCheckFailure_noUsedTicket_returns400_bookingStaysCheckedIn() {
        String suffix = "C" + TS + nextNonce();
        System.out.println("\n=== Scenario C: Pre-saga check failure (no USED ticket) ===");

        String token = registerAndLogin(suffix);
        Long userId = extractUserId(token);

        Long eventId = createEvent(token, "UPCOMING", "C-" + suffix);
        Long sessionId = addSession(token, eventId);

        Long bookingId = placeBooking(token, userId, suffix);
        addBookingItems(token, bookingId, sessionId);
        confirmBooking(token, bookingId, eventId);

// Issue and scan but do NOT mark USED – pre-check should reject completeBooking
        Long ticketId = issueTicket(token, bookingId, suffix);
        scanTicket(token, ticketId);
// markTicketUsed intentionally omitted

        checkInBooking(token, bookingId);
        advanceEventToOngoing(token, eventId);

        System.out.println("\n[11] Complete booking without USED ticket -> expect 400");
        ResponseEntity<String> res = completeBookingRaw(token, bookingId);
        assertThat(res.getStatusCode())
                .as("Complete booking without USED ticket should return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

// Booking must not have changed
        assertThat(getBooking(token, bookingId).get("status").asText())
                .as("Booking status must remain CHECKED_IN after pre-check failure")
                .isEqualTo("CHECKED_IN");
        System.out.println(" booking stays CHECKED_IN ✔");
    }
}