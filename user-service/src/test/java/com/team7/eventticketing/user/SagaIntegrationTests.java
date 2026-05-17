package com.team7.eventticketing.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SagaIntegrationTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public RestTemplate restTemplate() {
            RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) throws IOException {
                    return false;
                }
            });
            return restTemplate;
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper mapper;

    private static final String USER_SVC = "http://localhost:8081";
    private static final String EVENT_SVC = "http://localhost:8082";
    private static final String BOOKING_SVC = "http://localhost:8083";
    private static final String TICKET_SVC = "http://localhost:8084";
    private static final String SALES_SVC = "http://localhost:8085";

    private static final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

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
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private String registerAndLogin() {
        String n = nextNonce();
        String email = "saga_" + n + "@test.io";
        String password = "TestPwd!2026";
        String phone = "01" + n.substring(Math.max(0, n.length() - 8));

        Map<String, Object> registerBody = Map.of(
                "name", "Saga User " + n,
                "email", email,
                "password", password,
                "phone", phone
        );

        ResponseEntity<String> registerRes = restTemplate.postForEntity(
                USER_SVC + "/api/auth/register",
                registerBody,
                String.class
        );

        assertThat(registerRes.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> loginRes = restTemplate.postForEntity(
                USER_SVC + "/api/auth/login",
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
        JsonNode node = parse(payload);
        return node.get("uid").asLong();
    }

    private Long createEvent(String token, String status, String nameSuffix) {
        String eventDate = LocalDateTime.now().plusDays(5).withNano(0).toString();

        Map<String, Object> body = Map.of(
                "name", "Saga Event " + nameSuffix,
                "venue", "Main Hall",
                "eventDate", eventDate,
                "category", "CONCERT",
                "status", status,
                "rating", 0.0,
                "details", Map.of("description", "Saga integration test")
        );

        ResponseEntity<String> res = restTemplate.exchange(
                EVENT_SVC + "/api/events",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeader(token)),
                String.class
        );

        assertThat(res.getStatusCode().is2xxSuccessful() || res.getStatusCode() == HttpStatus.CREATED).isTrue();

        return parse(res.getBody()).get("id").asLong();
    }

    private Long createBooking(String token, Long userId, Long eventId, String status) {
        Map<String, Object> body = Map.of(
                "userId", userId,
                "eventId", eventId,
                "status", status,
                "totalAmount", 300.0,
                "bookingDate", LocalDateTime.now().withNano(0).toString(),
                "contactEmail", "contact@test.io"
        );

        ResponseEntity<String> res = restTemplate.exchange(
                BOOKING_SVC + "/api/bookings",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeader(token)),
                String.class
        );

        assertThat(res.getStatusCode().is2xxSuccessful() || res.getStatusCode() == HttpStatus.CREATED).isTrue();

        return parse(res.getBody()).get("id").asLong();
    }

    private void createTicket(String token, Long bookingId) {
        Map<String, Object> body = Map.of(
                "bookingId", bookingId,
                "attendeeName", "Saga Attendee",
                "status", "VALID",
                "issuedAt", LocalDateTime.now().withNano(0).toString(),
                "ticketCode", UUID.randomUUID().toString()
        );

        ResponseEntity<String> res = restTemplate.exchange(
                TICKET_SVC + "/api/tickets",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeader(token)),
                String.class
        );

        assertThat(res.getStatusCode().is2xxSuccessful() || res.getStatusCode() == HttpStatus.CREATED).isTrue();
    }

    private ResponseEntity<String> completeBooking(String token, Long bookingId) {
        return restTemplate.exchange(
                BOOKING_SVC + "/api/bookings/" + bookingId + "/complete",
                HttpMethod.PUT,
                new HttpEntity<>(authHeader(token)),
                String.class
        );
    }

    private ResponseEntity<String> processPayment(String token, Long bookingId, boolean simulateFailure) {
        String url = SALES_SVC + "/api/sales/booking/" + bookingId;
        if (simulateFailure) {
            url += "?simulateFailure=true";
        }

        Map<String, Object> body = Map.of(
                "method", "CREDIT_CARD",
                "cardLastFour", "4242"
        );

        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, authHeader(token)),
                String.class
        );
    }

    private JsonNode getBooking(String token, Long bookingId) {
        ResponseEntity<String> res = restTemplate.exchange(
                BOOKING_SVC + "/api/bookings/" + bookingId,
                HttpMethod.GET,
                new HttpEntity<>(authHeader(token)),
                String.class
        );

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return parse(res.getBody());
    }

    private void waitUntilBookingStatus(String token, Long bookingId, String expectedStatus, int maxAttempts) throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            JsonNode booking = getBooking(token, bookingId);
            String status = booking.get("status").asText();
            if (expectedStatus.equals(status)) {
                return;
            }
            Thread.sleep(1000);
        }

        JsonNode booking = getBooking(token, bookingId);
        assertThat(booking.get("status").asText()).isEqualTo(expectedStatus);
    }

    @Test
    @Order(1)
    void scenarioA_successPath_endsAtPaid() throws Exception {
        String token = registerAndLogin();
        Long userId = extractUserId(token);

        Long eventId = createEvent(token, "COMPLETED", "A");
        Long bookingId = createBooking(token, userId, eventId, "CHECKED_IN");
        createTicket(token, bookingId);

        ResponseEntity<String> completeRes = completeBooking(token, bookingId);
        assertThat(completeRes.getStatusCode().is2xxSuccessful()).isTrue();

        waitUntilBookingStatus(token, bookingId, "PAYMENT_PENDING", 15);

        ResponseEntity<String> paymentRes = processPayment(token, bookingId, false);
        assertThat(paymentRes.getStatusCode().is2xxSuccessful() || paymentRes.getStatusCode() == HttpStatus.CREATED).isTrue();

        waitUntilBookingStatus(token, bookingId, "PAID", 15);
    }

    @Test
    @Order(2)
    void scenarioB_paymentFailure_compensationEndsAtRefunded() throws Exception {
        String token = registerAndLogin();
        Long userId = extractUserId(token);

        Long eventId = createEvent(token, "COMPLETED", "B");
        Long bookingId = createBooking(token, userId, eventId, "CHECKED_IN");
        createTicket(token, bookingId);

        ResponseEntity<String> completeRes = completeBooking(token, bookingId);
        assertThat(completeRes.getStatusCode().is2xxSuccessful()).isTrue();

        waitUntilBookingStatus(token, bookingId, "PAYMENT_PENDING", 15);

        ResponseEntity<String> paymentRes = processPayment(token, bookingId, true);
        assertThat(paymentRes.getStatusCode().is2xxSuccessful() || paymentRes.getStatusCode() == HttpStatus.CREATED).isTrue();

        waitUntilBookingStatus(token, bookingId, "REFUNDED", 20);
    }

    @Test
    @Order(3)
    void scenarioC_preSagaCheckFailure_returns400_andBookingStaysCheckedIn() {
        String token = registerAndLogin();
        Long userId = extractUserId(token);

        Long eventId = createEvent(token, "UPCOMING", "C");
        Long bookingId = createBooking(token, userId, eventId, "CHECKED_IN");

        ResponseEntity<String> completeRes = completeBooking(token, bookingId);
        assertThat(completeRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode booking = getBooking(token, bookingId);
        assertThat(booking.get("status").asText()).isEqualTo("CHECKED_IN");
    }
}