package com.team7.eventticketing.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullSystemTestsPart2 {

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
        @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }

        @Bean
        public org.neo4j.driver.Driver neo4jDriver() {
            // Replace "password" with your actual Neo4j password (default is often "neo4j")
            return org.neo4j.driver.GraphDatabase.driver(
                    "bolt://localhost:7687",
                    org.neo4j.driver.AuthTokens.basic("neo4j", "neo4jpass")
            );
        }
    }

    @Autowired private RestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;
    @Autowired private org.neo4j.driver.Driver neo4j;
    @Autowired private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    private long neoCount(String cypher) {
        try (var session = neo4j.session()) {
            return session.run(cypher).single().get(0).asLong();
        }
    }

    private org.neo4j.driver.Value neoProp(String cypher) {
        try (var session = neo4j.session()) {
            return session.run(cypher).single().get(0);
        }
    }

    private static final String USER_SVC = "http://localhost:8081";
    private static final String EVENT_SVC = "http://localhost:8082";
    private static final String BOOKING_SVC = "http://localhost:8083";

    private static final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());
    private String getNonce() { return String.valueOf(nonce.incrementAndGet()); }

    @BeforeEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE users, events, bookings, tickets, ticket_sales CASCADE");
        jdbc.execute("SELECT setval('public.users_id_seq', 1, false)");
        mongoTemplate.dropCollection("booking_events");
        try (var conn = redisConnectionFactory.getConnection()) {
            conn.serverCommands().flushAll();
        }
        try (var session = neo4j.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
    }

    // --- INSERTION HELPERS ---

    private String el(String t, String c, String v) { return "'" + v + "'"; }

    private void seedBooking(Long userId, Long eventId, String status, double amount, String date) {
        jdbc.execute("INSERT INTO bookings (user_id, event_id, status, total_amount, contact_email, booking_date) " +
                "VALUES (" + userId + ", " + eventId + ", " + el("bookings", "status", status) + ", " + amount + ", 'test@t.io', '" + date + "')");
    }

    private Long insertEvent(String name, String category) {
        return ((Number) jdbc.queryForObject(
                "INSERT INTO events (name, venue, event_date, category, status, rating, total_ratings, details, created_at) " +
                        "VALUES ('" + name + "','V','2026-05-01T20:00:00','" + category + "'," + el("events", "status", "UPCOMING") + ", 0.0, 0,'{}', NOW()) RETURNING id",
                Long.class)).longValue();
    }

    private String registerAndLogin(String email, String role) {
        String n = getNonce();
        Map<String, String> body = Map.of("name", "U", "email", email, "password", "P", "phone", "01" + n.substring(Math.max(0, n.length() - 8)));
        restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);
        jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, email);
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login", Map.of("email", email, "password", "P"), String.class);
        return parse(res.getBody()).get("token").asText();
    }

    private JsonNode parse(String body) { try { return mapper.readTree(body); } catch (Exception e) { return null; } }
    private HttpHeaders authHeader(String token) { HttpHeaders h = new HttpHeaders(); h.setBearerAuth(token); return h; }

    // --- BOOKING DASHBOARD (TC54 - TC69) ---

    @Test void tc54_dashboardHappyPath() {
        String token = registerAndLogin("adm54@t.io", "ADMIN");
        Long eid = insertEvent("E54", "CONCERT");
        for (int i = 0; i < 6; i++) seedBooking(1L, eid, "COMPLETED", 150, "2026-03-15");
        for (int i = 0; i < 2; i++) seedBooking(1L, eid, "CANCELLED", 0, "2026-03-15");
        for (int i = 0; i < 2; i++) seedBooking(1L, eid, "PENDING", 50, "2026-03-15");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("totalBookings").asInt()).isEqualTo(10);
        assertThat(node.get("conversionRate").asDouble()).isEqualTo(0.6, org.assertj.core.data.Offset.offset(0.01));
        assertThat(node.get("bookingsByStatus").get("COMPLETED").asInt()).isEqualTo(6);
        assertThat(node.get("bookingsByStatus").get("CANCELLED").asInt()).isEqualTo(2);
        assertThat(node.get("bookingsByStatus").get("PENDING").asInt()).isEqualTo(2);
    }

    @Test void tc55_totalBookingsInDateRange() {
        String token = registerAndLogin("adm55@t.io", "ADMIN");
        for (int i = 0; i < 7; i++) seedBooking(1L, 1L, "COMPLETED", 100, "2026-09-15");
        seedBooking(1L, 1L, "COMPLETED", 100, "2026-10-15");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("totalBookings").asInt()).isEqualTo(7);
    }

    @Test void tc56_totalRevenueFromCompletedBookings() {
        String token = registerAndLogin("adm56@t.io", "ADMIN");
        seedBooking(1L, 1L, "COMPLETED", 500, "2026-09-15");
        seedBooking(1L, 1L, "COMPLETED", 500, "2026-09-20");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("totalRevenue").asDouble()).isEqualTo(1000.0);
    }

    @Test void tc57_averageBookingValueCalculation() {
        String token = registerAndLogin("adm57@t.io", "ADMIN");
        seedBooking(1L, 1L, "COMPLETED", 100, "2026-09-15");
        seedBooking(1L, 1L, "COMPLETED", 200, "2026-09-15");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("averageBookingValue").asDouble()).isEqualTo(150.0);
    }

    @Test void tc58_conversionRateIsolated() {
        String token = registerAndLogin("adm58@t.io", "ADMIN");
        seedBooking(1L, 1L, "CONFIRMED", 0, "2026-09-15");
        seedBooking(1L, 1L, "CHECKED_IN", 0, "2026-09-15");
        seedBooking(1L, 1L, "COMPLETED", 0, "2026-09-15");
        seedBooking(1L, 1L, "PENDING", 0, "2026-09-15");
        seedBooking(1L, 1L, "CANCELLED", 0, "2026-09-15");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("conversionRate").asDouble()).isEqualTo(0.6, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test void tc59_bookingsByStatusKeysPresent() {
        String token = registerAndLogin("adm59@t.io", "ADMIN");
        seedBooking(1L, 1L, "PENDING", 0, "2026-09-15");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.has("bookingsByStatus")).isTrue();
    }

    @Test void tc60_emptyRangeReturnsZeros() {
        String token = registerAndLogin("adm60@t.io", "ADMIN");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2099-01-01&endDate=2099-01-31";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("totalBookings").asInt()).isEqualTo(0);
        assertThat(node.get("totalRevenue").asDouble()).isEqualTo(0.0);
    }

    @Test void tc61_invalidRangeReturns400() {
        String token = registerAndLogin("adm61@t.io", "ADMIN");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-04-01&endDate=2026-03-01";
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void tc62_missingJwtReturns401() {
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31";
        assertThat(restTemplate.getForEntity(url, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc63_malformedJwtReturns401() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer malformed");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31";
        assertThat(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc64_boundaryDateInclusion() {
        String token = registerAndLogin("adm64@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (user_id, event_id, status, total_amount, contact_email, booking_date) " +
                "VALUES (1, 1, 'COMPLETED', 100, 't@t.io', '2026-05-01 00:00:00')");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("totalBookings").asInt()).isEqualTo(1);
    }

    @Test void tc65_outOfRangeExcluded() {
        String token = registerAndLogin("adm65@t.io", "ADMIN");
        seedBooking(1L, 1L, "COMPLETED", 10, "2026-05-31"); // Out
        seedBooking(1L, 1L, "COMPLETED", 10, "2026-06-15"); // In
        seedBooking(1L, 1L, "COMPLETED", 10, "2026-07-01"); // Out
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-06-01&endDate=2026-06-30";
        JsonNode node = parse(restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody());
        assertThat(node.get("totalBookings").asInt()).isEqualTo(1);
    }

    @Test void tc66_logsAnalyticsViewedOnFirstCall() {
        String token = registerAndLogin("adm66@t.io", "ADMIN");
        long before = mongoTemplate.getCollection("booking_events").countDocuments();
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(mongoTemplate.getCollection("booking_events").countDocuments()).isGreaterThan(before);
    }

    @Test void tc67_logsAnalyticsViewedOnCacheHit() {
        String token = registerAndLogin("adm67@t.io", "ADMIN");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31";
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        long after1 = mongoTemplate.getCollection("booking_events").countDocuments();
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(mongoTemplate.getCollection("booking_events").countDocuments()).isGreaterThan(after1);
    }

    @Test void tc68_cacheReturnsIdenticalBodies() {
        String token = registerAndLogin("adm68@t.io", "ADMIN");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31";
        String b1 = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody();
        String b2 = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody();
        assertThat(b1).isEqualTo(b2);
    }

    @Test void tc69_cacheDoesntReaggregate() {
        String token = registerAndLogin("adm69@t.io", "ADMIN");
        String url = BOOKING_SVC + "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31";
        seedBooking(1L, 1L, "COMPLETED", 100, "2026-03-15");
        String b1 = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody();
        seedBooking(1L, 1L, "COMPLETED", 999, "2026-03-15"); // New record after cache population
        String b2 = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class).getBody();
        assertThat(b1).isEqualTo(b2);
    }
    
    // --- ATTENDANCE RECORDING (TC70 - TC84) ---
    
    @Test void tc70_recordAttendanceHappyPath() {
        String token = registerAndLogin("adm70@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (700, 1, 1, 'COMPLETED', 't@t.io', NOW())");
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/700/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        System.out.println(res.getBody());
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }
    
    @Test void tc71_attendanceIdempotency() {
        String token = registerAndLogin("adm71@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (710, 1, 1, 'COMPLETED', 't@t.io', NOW())");
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/710/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/710/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        JsonNode node = parse(res.getBody());
        assertThat(node.get("attendanceCount").asInt()).isEqualTo(1); // Requirements specify count stays 1
    }
    
    @Test void tc72_twoDistinctBookingsSameUserEvent() {
        String token = registerAndLogin("adm72@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (721, 1, 1, 'COMPLETED', 't@t.io', NOW())");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (722, 1, 1, 'COMPLETED', 't@t.io', NOW())");
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/721/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/722/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        JsonNode node = parse(res.getBody());
        assertThat(node.get("attendanceCount").asInt()).isEqualTo(2); // Requirements specify count becomes 2
    }

    @Test void tc73_differentEventCreatesNewEdge() {
        String token = registerAndLogin("adm73@t.io", "ADMIN");
        Long uid = jdbc.queryForObject("SELECT id FROM users WHERE email='adm73@t.io'", Long.class);

        // Create bookings for two different events
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (731, "+uid+", 1, 'COMPLETED', 't@t.io', NOW())");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (732, "+uid+", 2, 'COMPLETED', 't@t.io', NOW())");

        restTemplate.exchange(BOOKING_SVC + "/api/bookings/731/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/732/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);

        // Verify two distinct ATTENDED edges exist in Neo4j
        assertThat(neoCount("MATCH (u:User {userId: "+uid+"})-[r:ATTENDED]->(e:Event {eventId: 1}) RETURN count(r)")).isEqualTo(1);
        assertThat(neoCount("MATCH (u:User {userId: "+uid+"})-[r:ATTENDED]->(e:Event {eventId: 2}) RETURN count(r)")).isEqualTo(1);
    }
    
    @Test void tc74_recordPendingReturns400() {
        String token = registerAndLogin("adm74@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (740, 1, 1, 'PENDING', 't@t.io', NOW())");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/740/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test void tc75_recordCancelledReturns400() {
        String token = registerAndLogin("adm75@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (750, 1, 1, 'CANCELLED', 't@t.io', NOW())");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/750/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test void tc76_recordCheckedInReturns400() {
        String token = registerAndLogin("adm76@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (760, 1, 1, 'CHECKED_IN', 't@t.io', NOW())");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/760/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test void tc77_recordConfirmedReturns400() {
        String token = registerAndLogin("adm77@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (770, 1, 1, 'CONFIRMED', 't@t.io', NOW())");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/770/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test void tc78_recordNotFound404() {
        String t = registerAndLogin("adm78@t.io", "ADMIN");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/999999/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(t)), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    
    @Test void tc79_recordMissingAuth401() {
        assertThat(restTemplate.postForEntity(BOOKING_SVC + "/api/bookings/1/record-attendance", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test void tc80_recordInvalidAuth401() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer fake");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/1/record-attendance", HttpMethod.POST, new HttpEntity<>(h), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc81_attendedEdgeHasDate() {
        String token = registerAndLogin("adm81@t.io", "ADMIN");
        Long uid = jdbc.queryForObject("SELECT id FROM users WHERE email='adm81@t.io'", Long.class);
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (810, "+uid+", 1, 'COMPLETED', 't@t.io', NOW())");

        restTemplate.exchange(BOOKING_SVC + "/api/bookings/810/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);

        // Verify edge has lastAttendedDate property
        org.neo4j.driver.Value dateProp = neoProp("MATCH (:User {userId: "+uid+"})-[r:ATTENDED]->(:Event {eventId: 1}) RETURN r.lastAttendedDate");
        assertThat(dateProp.isNull()).isFalse();
    }

    @Test void tc82_eventNodeExists() {
        String token = registerAndLogin("adm82@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (820, 1, 100, 'COMPLETED', 't@t.io', NOW())");

        restTemplate.exchange(BOOKING_SVC + "/api/bookings/820/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);

        // Verify Event node exists with correct ID
        assertThat(neoCount("MATCH (e:Event {eventId: 100}) RETURN count(e)")).isEqualTo(1);
    }

    @Test void tc83_userNodeExists() {
        String token = registerAndLogin("adm83@t.io", "ADMIN");
        Long uid = jdbc.queryForObject("SELECT id FROM users WHERE email='adm83@t.io'", Long.class);
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (830, "+uid+", 1, 'COMPLETED', 't@t.io', NOW())");

        restTemplate.exchange(BOOKING_SVC + "/api/bookings/830/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);

        // Verify User node exists with correct ID
        assertThat(neoCount("MATCH (u:User {userId: "+uid+"}) RETURN count(u)")).isEqualTo(1);
    }
    
    @Test void tc84_logsInteractionToMongo() {
        String token = registerAndLogin("adm84@t.io", "ADMIN");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (840, 1, 1, 'COMPLETED', 't@t.io', NOW())");
        long before = mongoTemplate.getCollection("booking_events").countDocuments();
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/840/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(mongoTemplate.getCollection("booking_events").countDocuments()).isGreaterThan(before);
    }
    
    // --- RECOMMENDATIONS (TC85 - TC99) ---
    
    @Test void tc85_recommendationsHappyPath() {
        String t = registerAndLogin("aid85@t.io", "ATTENDEE");
        Long uid = jdbc.queryForObject("SELECT id FROM users WHERE email='aid85@t.io'", Long.class);
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=" + uid, HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    @Test void tc87_defaultLimitCapsAt5() {
        String t = registerAndLogin("adm87@t.io", "ADMIN");
        JsonNode res = parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=1", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody());
        assertThat(res.size()).isLessThanOrEqualTo(5);
    }
    
    @Test void tc88_noInteractionsEmptyList() {
        String t = registerAndLogin("u88@t.io", "ATTENDEE");
        Long uid = jdbc.queryForObject("SELECT id FROM users WHERE email='u88@t.io'", Long.class);
        assertThat(parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId="+uid, HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody()).size()).isEqualTo(0);
    }
    
    @Test void tc89_noSimilarUsersEmptyList() {
        String t = registerAndLogin("adm89@t.io", "ADMIN");
        assertThat(parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=1", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody()).size()).isEqualTo(0);
    }
    
    @Test void tc90_ownershipViolation403() {
        String tA = registerAndLogin("a@t.io", "ATTENDEE");
        registerAndLogin("b@t.io", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email='b@t.io'", Long.class);
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId="+idB, HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test void tc91_adminBypassOwnership() {
        String tAdm = registerAndLogin("adm91@t.io", "ADMIN");
        registerAndLogin("u91@t.io", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT id FROM users WHERE email='u91@t.io'", Long.class);
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId="+idU, HttpMethod.GET, new HttpEntity<>(authHeader(tAdm)), String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }
    
    @Test void tc92_nonExistentUser404() {
        String t = registerAndLogin("adm92@t.io", "ADMIN");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=999999", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    
    @Test void tc93_recsMissingAuth401() {
        assertThat(restTemplate.getForEntity(BOOKING_SVC + "/api/bookings/recommendations?userId=1", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test void tc94_recsInvalidAuth401() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer fake");
        assertThat(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=1", HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test void tc95_itemShapeRich() {
        String t = registerAndLogin("adm95@t.io", "ADMIN");
        JsonNode first = parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=1", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody()).get(0);
        if(first != null) {
            assertThat(first.has("eventId") || first.has("id")).isTrue();
            assertThat(first.has("name")).isTrue();
            assertThat(first.has("category")).isTrue();
            assertThat(first.has("score")).isTrue();
        }
    }
    
    @Test void tc96_cacheReturnsIdenticalRecs() {
        String t = registerAndLogin("adm96@t.io", "ADMIN");
        registerAndLogin("user96@t.io", "ATTENDEE"); // Ensure at least one user with interactions exists
        Long uid = jdbc.queryForObject("SELECT id FROM users WHERE email='user96@t.io'", Long.class);
        String url = BOOKING_SVC + "/api/bookings/recommendations?userId="+uid;
        String b1 = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody();
        String b2 = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody();
        assertThat(b1).isEqualTo(b2);
    }
    
    @Test void tc97_limitParamHonored() {
        String t = registerAndLogin("adm97@t.io", "ADMIN");
        JsonNode res = parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=1&limit=2", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class).getBody());
        assertThat(res.size()).isLessThanOrEqualTo(2);
    }

    @Test void tc98_excludeAlreadyAttended() {
        // 1) Setup: A and B both attend E1. B also attends E2 (Target).
        String tA = registerAndLogin("userA@t.io", "ATTENDEE");
        Long uidA = jdbc.queryForObject("SELECT id FROM users WHERE email='userA@t.io'", Long.class);
        String tB = registerAndLogin("userB@t.io", "ATTENDEE");
        Long uidB = jdbc.queryForObject("SELECT id FROM users WHERE email='userB@t.io'", Long.class);

        // Record attendance to establish similarity
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (981, "+uidA+", 1, 'COMPLETED', 't@t.io', NOW())");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (982, "+uidB+", 1, 'COMPLETED', 't@t.io', NOW())");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (983, "+uidB+", 2, 'COMPLETED', 't@t.io', NOW())");

        restTemplate.exchange(BOOKING_SVC + "/api/bookings/981/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/982/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(tB)), String.class);
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/983/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(tB)), String.class);

        // 2) Request recs for A. Should include E2 but EXCLUDE E1 (already attended)
        JsonNode recs = parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=" + uidA,
                HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class).getBody());

        for (JsonNode rec : recs) {
            assertThat(rec.get("eventId").asLong()).isNotEqualTo(1L);
        }
    }

    @Test void tc99_categoryEnrichedFromPg() {
        String tA = registerAndLogin("u99@t.io", "ATTENDEE");
        Long uidA = jdbc.queryForObject("SELECT id FROM users WHERE email='u99@t.io'", Long.class);

        // Seed target event in PostgreSQL with a specific category
        Long targetEid = insertEvent("EnrichedEvent", "CONCERT");

        // Seed similarity (Admin attends E1, User A attends E1, Admin attends Target)
        String tAdm = registerAndLogin("adm99@t.io", "ADMIN");
        Long uidAdm = jdbc.queryForObject("SELECT id FROM users WHERE email='adm99@t.io'", Long.class);

        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (991, "+uidA+", 1, 'COMPLETED', 't@t.io', NOW())");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (992, "+uidAdm+", 1, 'COMPLETED', 't@t.io', NOW())");
        jdbc.execute("INSERT INTO bookings (id, user_id, event_id, status, contact_email, booking_date) VALUES (993, "+uidAdm+", "+targetEid+", 'COMPLETED', 't@t.io', NOW())");

        restTemplate.exchange(BOOKING_SVC + "/api/bookings/991/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(tAdm)), String.class);
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/992/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(tAdm)), String.class);
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/993/record-attendance", HttpMethod.POST, new HttpEntity<>(authHeader(tAdm)), String.class);

        // Verify result contains category enriched from PostgreSQL
        JsonNode recs = parse(restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations?userId=" + uidA,
                HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class).getBody());

        assertThat(recs.get(0).get("category").asText()).isEqualTo("CONCERT");
    }
}