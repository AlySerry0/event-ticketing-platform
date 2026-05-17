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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SagaIntegrationTests {

    private static final String BASE = "http://localhost:30080";

    private static final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        RestTemplate rt = new RestTemplate(new SimpleClientHttpRequestFactory());
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        return rt;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String nextNonce() {
        return String.valueOf(nonce.incrementAndGet());
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank() || body.trim().equals("null")) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + body, e);
        }
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String registerAndLogin() {
        String n = nextNonce();
        String email = "saga_" + n + "@test.io";
        String password = "TestPwd!2026";
        String phone = "01" + n.substring(Math.max(0, n.length() - 8));

        ResponseEntity<String> registerRes = restTemplate.postForEntity(
                BASE + "/api/auth/register",
                Map.of("name", "Saga User " + n, "email", email, "password", password, "phone", phone),
                String.class
        );
        assertThat(registerRes.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> loginRes = restTemplate.postForEntity(
                BASE + "/api/auth/login",
                Map.of("email", email, "password", password),
                String.class
        );
        assertThat(loginRes.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode node = parse(loginRes.getBody());
        assertThat(node).isNotNull();
        assertThat(node.has("token")).isTrue();
        return node.get("token").asText();
    }

    private Long extractUserId(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        return parse(payload).get("uid").asLong();
    }

    private Long createEvent(String token, String status, String nameSuffix) {
        ResponseEntity<String> res = restTemplate.exchange(
                BASE + "/api/events",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "name", "Saga Event " + nameSuffix,
                        "venue", "Main Hall",
                        "eventDate", LocalDateTime.now().plusDays(5).withNano(0).toString(),
                        "category", "CONCERT",
                        "status", status,
                        "rating", 0.0,
                        "details", Map.of("description", "Saga integration test")
                ), authHeader(token)),
                String.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return parse(res.getBody()).get("id").asLong();
    }

    private Long createBooking(String token, Long userId, Long eventId, String status) {
        ResponseEntity<String> res = restTemplate.exchange(
                BASE + "/api/bookings",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "userId", userId,
                        "eventId", eventId,
                        "status", status,
                        "totalAmount", 300.0,
                        "bookingDate", LocalDateTime.now().withNano(0).toString(),
                        "contactEmail", "contact@test.io"
                ), authHeader(token)),
                String.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return parse(res.getBody()).get("id").asLong();
    }

    private void createTicket(String token, Long bookingId) {
        ResponseEntity<String> res = restTemplate.exchange(
                BASE + "/api/tickets",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "bookingId", bookingId,
                        "attendeeName", "Saga Attendee",
                        "status", "VALID",
                        "issuedAt", LocalDateTime.now().withNano(0).toString(),
                        "ticketCode", UUID.randomUUID().toString()
                ), authHeader(token)),
                String.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        // save() always forces VALID; completeBooking pre-check requires USED → mark USED now
        Long ticketId = parse(res.getBody()).get("id").asLong();
        ResponseEntity<String> updateRes = restTemplate.exchange(
                BASE + "/api/tickets/" + ticketId,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "USED"), authHeader(token)),
                String.class
        );
        assertThat(updateRes.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private ResponseEntity<String> completeBooking(String token, Long bookingId) {
        return restTemplate.exchange(
                BASE + "/api/bookings/" + bookingId + "/complete",
                HttpMethod.PUT,
                new HttpEntity<>(authHeader(token)),
                String.class
        );
    }

    private ResponseEntity<String> processPayment(String token, Long bookingId, boolean simulateFailure) {
        String url = BASE + "/api/sales/booking/" + bookingId
                + (simulateFailure ? "?simulateFailure=true" : "");
        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(Map.of("method", "CREDIT_CARD", "cardLastFour", "4242"), authHeader(token)),
                String.class
        );
    }

    private JsonNode getBooking(String token, Long bookingId) {
        ResponseEntity<String> res = restTemplate.exchange(
                BASE + "/api/bookings/" + bookingId,
                HttpMethod.GET,
                new HttpEntity<>(authHeader(token)),
                String.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return parse(res.getBody());
    }

    private void waitUntilBookingStatus(String token, Long bookingId, String expected, int maxAttempts)
            throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            if (expected.equals(getBooking(token, bookingId).get("status").asText())) return;
            Thread.sleep(1000);
        }
        assertThat(getBooking(token, bookingId).get("status").asText()).isEqualTo(expected);
    }

    private Long waitUntilPendingSaleExistsForBooking(String token, Long bookingId, int maxAttempts)
            throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            ResponseEntity<String> res = restTemplate.exchange(
                    BASE + "/api/sales/search?status=PENDING",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeader(token)),
                    String.class
            );
            if (res.getStatusCode().is2xxSuccessful()) {
                JsonNode list = parse(res.getBody());
                if (list != null && list.isArray()) {
                    for (JsonNode sale : list) {
                        if (bookingId.equals(sale.get("bookingId").asLong())) {
                            return sale.get("id").asLong();
                        }
                    }
                }
            }
            Thread.sleep(1000);
        }
        // timeout — produce a clear failure
        JsonNode list = parse(restTemplate.exchange(
                BASE + "/api/sales/search?status=PENDING",
                HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(list).isNotNull();
        boolean found = false;
        for (JsonNode sale : list) {
            if (bookingId.equals(sale.get("bookingId").asLong())) { found = true; break; }
        }
        assertThat(found).as("Expected a PENDING TicketSale for bookingId=" + bookingId).isTrue();
        return -1L;
    }

    private void waitUntilSaleStatus(String token, Long saleId, String expected, int maxAttempts)
            throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            ResponseEntity<String> res = restTemplate.exchange(
                    BASE + "/api/sales/" + saleId,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeader(token)),
                    String.class
            );
            if (res.getStatusCode().is2xxSuccessful()) {
                JsonNode node = parse(res.getBody());
                if (node != null && expected.equals(node.get("status").asText())) return;
            }
            Thread.sleep(1000);
        }
        JsonNode node = parse(restTemplate.exchange(
                BASE + "/api/sales/" + saleId,
                HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node).isNotNull();
        assertThat(node.get("status").asText()).isEqualTo(expected);
    }

    // ── scenarios ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void scenarioA_successPath_endsAtPaid() throws Exception {
        String token = registerAndLogin();
        Long userId = extractUserId(token);

        Long eventId  = createEvent(token, "COMPLETED", "A");
        Long bookingId = createBooking(token, userId, eventId, "CHECKED_IN");
        createTicket(token, bookingId);

        assertThat(completeBooking(token, bookingId).getStatusCode().is2xxSuccessful()).isTrue();

        waitUntilBookingStatus(token, bookingId, "PAYMENT_PENDING", 15);
        waitUntilPendingSaleExistsForBooking(token, bookingId, 5);

        assertThat(processPayment(token, bookingId, false).getStatusCode().is2xxSuccessful()).isTrue();

        waitUntilBookingStatus(token, bookingId, "PAID", 15);
    }

    @Test
    @Order(2)
    void scenarioB_paymentFailure_compensationEndsAtRefunded() throws Exception {
        String token = registerAndLogin();
        Long userId = extractUserId(token);

        Long eventId  = createEvent(token, "COMPLETED", "B");
        Long bookingId = createBooking(token, userId, eventId, "CHECKED_IN");
        createTicket(token, bookingId);

        assertThat(completeBooking(token, bookingId).getStatusCode().is2xxSuccessful()).isTrue();

        waitUntilBookingStatus(token, bookingId, "PAYMENT_PENDING", 15);

        ResponseEntity<String> paymentRes = processPayment(token, bookingId, true);
        assertThat(paymentRes.getStatusCode().is2xxSuccessful()).isTrue();
        Long saleId = parse(paymentRes.getBody()).get("id").asLong();

        waitUntilBookingStatus(token, bookingId, "REFUNDED", 20);
        waitUntilSaleStatus(token, saleId, "REFUNDED", 10);
    }

    @Test
    @Order(3)
    void scenarioC_preSagaCheckFailure_returns400_andBookingStaysCheckedIn() {
        String token = registerAndLogin();
        Long userId = extractUserId(token);

        // Event COMPLETED so event-status pre-check passes; 0 USED tickets triggers check #3 → 400
        Long eventId  = createEvent(token, "COMPLETED", "C");
        Long bookingId = createBooking(token, userId, eventId, "CHECKED_IN");

        assertThat(completeBooking(token, bookingId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getBooking(token, bookingId).get("status").asText()).isEqualTo("CHECKED_IN");
    }
}
