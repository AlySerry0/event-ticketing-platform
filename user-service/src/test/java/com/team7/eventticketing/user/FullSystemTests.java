package com.team7.eventticketing.user;

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

/**
 * FULL INTEGRATED TEST SUITE (TC01 - TC53)
 * Ports: 8081 (User), 8082 (Event), 8083 (Booking), 8084 (Ticket), 8085 (Sales)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullSystemTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public RestTemplate restTemplate() {
            RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) throws IOException {
                    return false; // Prevent RestTemplate from throwing exceptions on 4xx/5xx
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
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    // Service Base URLs
    private static final String USER_SVC = "http://localhost:8081";
    private static final String EVENT_SVC = "http://localhost:8082";
    private static final String BOOKING_SVC = "http://localhost:8083";
    private static final String TICKET_SVC = "http://localhost:8084";
    private static final String SALES_SVC = "http://localhost:8085";


    // --- Database Cleanup ---

    @BeforeEach
    void setup() {
        // Clear all relational data before every test
        // TRUNCATE is faster and resets IDs, CASCADE handles foreign keys.
        jdbc.execute("TRUNCATE TABLE users, events, bookings, tickets CASCADE");
    }

    private static final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    private String getNonce() { return String.valueOf(nonce.incrementAndGet()); }

    private HttpHeaders authHeader(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private JsonNode parse(String body) {
        try { return mapper.readTree(body); } catch (Exception e) { return null; }
    }

    private String el(String table, String col, String value) {
        try { String udt = jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_name = ? AND column_name = ?", String.class, table, col); if (udt != null && !udt.equals("varchar") && !udt.equals("text") && !udt.startsWith("int")) return "'" + value + "'::" + udt; } catch (Exception e) {}
        return "'" + value + "'";
    }

    private String registerUser(String email, String pwd, String role) {
        String phone = "01" + getNonce().substring(Math.max(0, getNonce().length() - 8));
        Map<String, String> body = Map.of("name", "User_" + getNonce(), "email", email, "password", pwd, "phone", phone);
        restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);
        if (role != null && !role.equals("ATTENDEE")) {
            jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, email);
        }
        ResponseEntity<String> loginRes = restTemplate.postForEntity(USER_SVC + "/api/auth/login",
                Map.of("email", email, "password", pwd), String.class);
        JsonNode node = parse(loginRes.getBody());
        return (node != null && node.has("token")) ? node.get("token").asText() : "";
    }

    // --- SECTION 1: AUTH CORE & REGISTRATION (TC01-TC05, TC09, TC14-TC16) ---

    @Test @Order(1) void tc01_registerHappyPath() {
        String email = "tc01_" + getNonce() + "@grader.testgen.io";
        String phone = "01" + getNonce().substring(Math.max(0, getNonce().length() - 8));
        Map<String, String> body = Map.of("name", "TC01 User", "email", email, "password", "TestPwd12026", "phone", phone);
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode node = parse(res.getBody());
        assertThat(node != null && node.has("id") && node.get("id").isNumber()).isTrue();
    }

    @Test @Order(2) void tc02_loginHappyPath() {
        String email = "tc02_" + getNonce() + "@test.io";
        registerUser(email, "TestPwd!2026", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login",
                Map.of("email", email, "password", "TestPwd!2026"), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(parse(res.getBody()).get("token").asText().split("\\.")).hasSize(3);
    }

    @Test @Order(4) void tc04_duplicateEmailReturns4xx() {
        String email = "dup_" + getNonce() + "@test.io";
        registerUser(email, "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register",
                Map.of("name", "U2", "email", email, "password", "P", "phone", getNonce()), String.class);
        assertThat(res.getStatusCode().value()).isBetween(400, 499);
    }

    @Test @Order(5) void tc05_wrongPasswordReturns401() {
        String email = "tc05_" + getNonce() + "@test.io";
        registerUser(email, "Correct", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login",
                Map.of("email", email, "password", "Wrong"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test @Order(9) void tc09_nonExistentEmailReturns401() {
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login",
                Map.of("email", "none_" + getNonce() + "@test.io", "password", "any"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test @Order(14) void tc14_missingEmailReturns4xx() {
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register",
                Map.of("name", "NoE", "password", "P"), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

        @Test @Order(15) void tc15_massAssignmentRoleRejected() {
            String email = "hacker_" + getNonce() + "@test.io";
            Map<String, String> body = new HashMap<>(Map.of("name", "H", "email", email, "password", "P", "phone", "011"));
            body.put("role", "ADMIN");
            ResponseEntity<String> response =
                    restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);

            assertThat(response.getStatusCode().is2xxSuccessful())
                    .withFailMessage("Registration failed. Status: %s, Body: %s",
                            response.getStatusCode(), response.getBody())
                    .isTrue();
            assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email)).isNotEqualTo("ADMIN");
        }

    @Test @Order(16) void tc16_emptyPasswordLoginFails() {
        String email = "tc16_" + getNonce() + "@test.io";
        registerUser(email, "valid", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login",
                Map.of("email", email, "password", ""), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isFalse();
    }

    // --- SECTION 2: SECURITY FILTER & JWT INTEGRITY (TC06-TC08, TC10-TC13) ---

    @Test void tc06_adminJwtAcceptedGlobally() {
        String token = registerUser("admin_" + getNonce() + "@test.io", "pwd", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc07_missingAuthRejected() {
        ResponseEntity<String> res = restTemplate.getForEntity(EVENT_SVC + "/api/events", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc08_tamperedJwtRejected() {
        String token = registerUser("tamp_" + getNonce() + "@test.io", "pwd", "ADMIN");
        String tampered = token.substring(0, token.lastIndexOf(".") + 1) + "fake";
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(authHeader(tampered)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc10_emptyBearerRejected() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer ");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc11_nonBearerSchemeRejected() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Basic dXNlcjpwYXNz");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc12_garbageTokenRejected() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer not.a.jwt");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc13_forgedRoleRejected() {
        String token = registerUser("f_" + getNonce() + "@test.io", "p", "ATTENDEE");
        String[] p = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(p[1])).replace("ATTENDEE", "ADMIN");
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + p[2];
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(authHeader(forged)), String.class);
        assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    // --- SECTION 3: USER CRUD, IDOR & ADMIN OVERRIDE (TC17-TC23, TC03) ---

    @Test void tc03_readOwnProfile() {
        String email = "tc03_" + getNonce() + "@test.io";
        String token = registerUser(email, "pwd", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + id, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc17_crossUserReadIDOR() {
        String tokenA = registerUser("a_" + getNonce() + "@test.io", "pwd", "ATTENDEE");
        String emailB = "b_" + getNonce() + "@test.io"; registerUser(emailB, "pwd", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, emailB);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idB, HttpMethod.GET, new HttpEntity<>(authHeader(tokenA)), String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    @Test void tc18_crossUserUpdateIDOR() {
        String tA = registerUser("a18_" + getNonce() + "@test.io", "p", "ATTENDEE");
        String eB = "b18_" + getNonce() + "@test.io"; registerUser(eB, "p", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eB);
        restTemplate.exchange(USER_SVC + "/api/users/" + idB, HttpMethod.PUT, new HttpEntity<>(Map.of("name","H"), authHeader(tA)), String.class);
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, idB)).isNotEqualTo("H");
    }

    @Test void tc19_crossUserDeleteIDOR() {
        String tA = registerUser("a19_" + getNonce() + "@test.io", "p", "ATTENDEE");
        String eB = "b19_" + getNonce() + "@test.io"; registerUser(eB, "p", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eB);
        restTemplate.exchange(USER_SVC + "/api/users/" + idB, HttpMethod.DELETE, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, idB)).isEqualTo(1);
    }

    @Test void tc20_ownerCanUpdateSelf() {
        String email = "tc20_" + getNonce() + "@test.io";
        String token = registerUser(email, "pwd", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name","U20", "email", email, "password", "pwd", "phone", getNonce()), authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc21_adminReadOverride() {
        String adminT = registerUser("adm21_" + getNonce() + "@test.io", "p", "ADMIN");
        registerUser("u21_" + getNonce() + "@test.io", "p", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idU, HttpMethod.GET, new HttpEntity<>(authHeader(adminT)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc22_adminUpdateOverride() {
        String adminT = registerUser("adm22_" + getNonce() + "@test.io", "p", "ADMIN");
        String eU = "u22_" + getNonce() + "@test.io"; registerUser(eU, "p", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eU);
        restTemplate.exchange(USER_SVC + "/api/users/" + idU, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name","U22", "email", eU, "password", "p", "phone", getNonce()), authHeader(adminT)), String.class);
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, idU)).isEqualTo("U22");
    }

    @Test void tc23_adminHardDeleteOverride() {
        String adminT = registerUser("adm23_" + getNonce() + "@test.io", "p", "ADMIN");
        registerUser("u23_" + getNonce() + "@test.io", "p", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        restTemplate.exchange(USER_SVC + "/api/users/" + idU, HttpMethod.DELETE, new HttpEntity<>(authHeader(adminT)), String.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, idU)).isEqualTo(0);
    }

    // --- SECTION 4: ACTIVITY FEED & PAGINATION (TC24-TC34) ---

    @Test void tc24_ownActivityFeed() {
        String email = "tc24_" + getNonce() + "@test.io";
        String token = registerUser(email, "pwd", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + id + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).has("content")).isTrue();
    }

    @Test void tc25_nonExistentActivity404() {
        String token = registerUser("adm25_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + Long.MAX_VALUE + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc26_negativeIdActivity4xx() {
        String token = registerUser("adm26_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/-1/activity", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc27_nonNumericActivity4xx() {
        String token = registerUser("adm27_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/abc/activity", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc28_sizeZeroActivity() {
        String token = registerUser("tc28_" + getNonce() + "@test.io", "p", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?size=0", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test void tc29_negativeSizeActivity4xx() {
        String token = registerUser("tc29_" + getNonce() + "@test.io", "p", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?size=-1", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc30_stringSizeActivity4xx() {
        String token = registerUser("tc30_" + getNonce() + "@test.io", "p", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?size=abc", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc31_crossUserActivityStrict403() {
        String tA = registerUser("a31_" + getNonce() + "@test.io", "p", "ATTENDEE");
        registerUser("b31_" + getNonce() + "@test.io", "p", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idB + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void tc32_adminReadAnyActivity() {
        String adminT = registerUser("adm32_" + getNonce() + "@test.io", "p", "ADMIN");
        registerUser("u32_" + getNonce() + "@test.io", "p", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idU + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(adminT)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc33_negativePageActivity4xx() {
        String token = registerUser("tc33_" + getNonce() + "@test.io", "p", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?page=-1", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc34_stringPageActivity4xx() {
        String token = registerUser("tc34_" + getNonce() + "@test.io", "p", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?page=abc", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    // --- SECTION 5: ELASTICSEARCH & EVENT MANAGEMENT (TC35-TC41) ---

    @Test void tc35_esSearchHappyPath() {
        String token = registerUser("adm35_" + getNonce() + "@test.io", "p", "ADMIN");
        String keyword = "EV_" + getNonce();
        jdbc.update("INSERT INTO events (name, venue, event_date, category, status, rating, total_ratings, details, created_at) VALUES (?, 'V', '2026-05-01T20:00:00', 'CONCERT', "+ el("events", "status", "UPCOMING") +", 0.0, 0,'{}', NOW())", keyword);
        Long id = jdbc.queryForObject("SELECT id FROM events WHERE name = ?", Long.class, keyword);
        restTemplate.exchange(EVENT_SVC + "/api/events/" + id + "/index", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?q=" + keyword, HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc36_esNoMatchEmptyList() {
        String token = registerUser("adm36_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?q=none_" + getNonce(), HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).size()).isEqualTo(0);
    }

    @Test void tc37_blankEsQuery400() {
        String token = registerUser("adm37_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?q=", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void tc38_manualIndex() {
        String token = registerUser("adm38_" + getNonce() + "@test.io", "p", "ADMIN");

        // Fixed insertion including all mandatory fields and returning the generated ID
        Long id = ((Number) jdbc.queryForObject(
                "INSERT INTO events (name, venue, event_date, category, status, rating, total_ratings, details, created_at) " +
                        "VALUES ('Manual Index Event', 'Test Venue', '2026-05-01T20:00:00', 'CONCERT', " +
                        el("events", "status", "UPCOMING") + ",0.0, 0,'{}', NOW()) RETURNING id",
                Long.class)).longValue();

        // Trigger the manual index endpoint for the newly created event
        ResponseEntity<String> res = restTemplate.exchange(
                EVENT_SVC + "/api/events/" + id + "/index",
                HttpMethod.POST,
                new HttpEntity<>(authHeader(token)),
                String.class
        );

        // Assert that the manual index trigger returns a successful 2xx status
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc39_indexNonExistent404() {
        String token = registerUser("adm39_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/999999/index", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc40_eventDashboardFields() {
        String token = registerUser("adm40_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/analytics/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        JsonNode node = parse(res.getBody());
        assertThat(node.has("totalEvents") && node.has("averageRating")).isTrue();
    }

    @Test void tc41_totalEventsMatch() {
        String token = registerUser("adm41_" + getNonce() + "@test.io", "p", "ADMIN");

        jdbc.update("INSERT INTO events (name, venue, event_date, category, status, rating, total_ratings, details, created_at) " +
                "VALUES ('E1', 'V', '2026-05-01T20:00:00', 'CONCERT', "+ el("events", "status", "UPCOMING") +", 0.0, 0,'{}', NOW())");
        jdbc.update("INSERT INTO events (name, venue, event_date, category, status, rating, total_ratings, details, created_at) " +
                        "VALUES ('E2', 'V', '2026-06-01T20:00:00', 'CONCERT', "+ el("events", "status", "UPCOMING") +", 0.0, 0,'{}', NOW())");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/analytics/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).get("totalEvents").asInt()).isGreaterThanOrEqualTo(2);
    }

    // --- SECTION 6: BOOKING & NEO4J GRAPH INTEGRATION (TC42-TC48) ---

    @Test void tc42_bookingDashboardFields() {
        String token = registerUser("adm42_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/analytics/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).has("totalBookings")).isTrue();
    }

    @Test void tc43_completionRateOne() {
        String token = registerUser("adm43_" + getNonce() + "@test.io", "p", "ADMIN");
        jdbc.execute("DELETE FROM bookings");

        // Using the mandatory columns and status helper from your successful example
        String insertSql = "INSERT INTO bookings (user_id, contact_email, status, booking_date) " +
                "VALUES (1, 'tc43@test.com', " + el("bookings", "status", "COMPLETED") + ", NOW())";

        // Seed two COMPLETED bookings as per spec requirements
        jdbc.execute(insertSql);
        jdbc.execute(insertSql);

        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/analytics/dashboard",
                HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);

        // Assert completionRate is 1.0 (100%) with 0.05 tolerance
        assertThat(parse(res.getBody()).get("completionRate").asDouble())
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test void tc44_recordAttendanceNeo4j() {
        String token = registerUser("adm44_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/1/attend", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc45_repeatAttendIncrements() {
        String token = registerUser("adm45_" + getNonce() + "@test.io", "p", "ADMIN");
        restTemplate.exchange(BOOKING_SVC + "/api/bookings/1/attend", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/1/attend", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).get("attendanceCount").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test void tc46_attendNonExistent404() {
        String token = registerUser("adm46_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/999999/attend", HttpMethod.POST, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc47_recommendationsHistory() {
        String token = registerUser("adm47_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations/1", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc48_coldStartRecommendations() {
        String t = registerUser("cold_" + getNonce() + "@test.io", "p", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(BOOKING_SVC + "/api/bookings/recommendations/" + id, HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(parse(res.getBody()).size()).isEqualTo(0);
    }

    // --- SECTION 7: TICKET, SALES & CASSANDRA INTEGRATION (TC49-TC53) ---

    @Test void tc49_salesDashboardFields() {
        String token = registerUser("adm49_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(SALES_SVC + "/api/tickets/analytics/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).has("totalTickets")).isTrue();
    }

    @Test void tc50_scanRateZero() {
        String token = registerUser("adm50_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(TICKET_SVC + "/api/tickets/analytics", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).get("scanRate").asDouble()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test void tc51_recordScanCassandra() {
        String token = registerUser("adm51_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(TICKET_SVC + "/api/tickets/1/scan", HttpMethod.POST,
                new HttpEntity<>(Map.of("scanType","CHECKED_IN"), authHeader(token)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc52_scanNonExistent404() {
        String token = registerUser("adm52_" + getNonce() + "@test.io", "p", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(TICKET_SVC + "/api/tickets/999999/scan", HttpMethod.POST,
                new HttpEntity<>(Map.of("scanType","CHECKED_IN"), authHeader(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc53_scanHistory() {
        String token = registerUser("adm53_" + getNonce() + "@test.io", "p", "ADMIN");
        restTemplate.exchange(TICKET_SVC + "/api/tickets/1/scan", HttpMethod.POST, new HttpEntity<>(Map.of("scanType","CHECKED_IN"), authHeader(token)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(TICKET_SVC + "/api/tickets/1/scan-history", HttpMethod.GET, new HttpEntity<>(authHeader(token)), String.class);
        assertThat(parse(res.getBody()).size()).isGreaterThanOrEqualTo(1);
    }
}