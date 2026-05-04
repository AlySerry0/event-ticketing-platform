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
                    return false; // Manually handle status codes
                }
            });
            return restTemplate;
        }
        @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired private RestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private static final String USER_SVC = "http://localhost:8081";
    private static final String EVENT_SVC = "http://localhost:8082";
    private static final String BOOKING_SVC = "http://localhost:8083";

    private static final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());
    private String getNonce() { return String.valueOf(nonce.incrementAndGet()); }

    @BeforeEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE users, events, bookings CASCADE");
        mongoTemplate.dropCollection("event_events");
    }

    // --- INSERTION HELPERS ---

    private String el(String table, String col, String value) {
        try { String udt = jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_name = ? AND column_name = ?", String.class, table, col); if (udt != null && !udt.equals("varchar") && !udt.equals("text") && !udt.startsWith("int")) return "'" + value + "'::" + udt; } catch (Exception e) {}
        return "'" + value + "'";
    }

    private Long insertEvent(String name, String status, double rating) {
        return ((Number) jdbc.queryForObject(
                "INSERT INTO events (name, venue, event_date, category, status, rating, total_ratings, details, created_at) " +
                        "VALUES ('"+name+"','V','2026-05-01T20:00:00','CONCERT',"+el("events","status",status)+", "+rating+", 1,'{}', NOW()) RETURNING id",
                Long.class)).longValue();
    }

    private String registerUser(String email, String pwd, String role) {
        String phone = "01" + getNonce().substring(Math.max(0, getNonce().length() - 8));
        Map<String, String> body = Map.of("name", "U", "email", email, "password", pwd, "phone", phone);

        // Perform Registration
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);

        if (role != null && !role.equals("ATTENDEE")) {
            jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, email);
        }

        // Fallback: If registration failed or didn't return a token, perform Login
        ResponseEntity<String> loginRes = restTemplate.postForEntity(USER_SVC + "/api/auth/login",
                Map.of("email", email, "password", pwd), String.class);

        JsonNode loginNode = parse(loginRes.getBody());
        return (loginNode != null && loginNode.has("token")) ? loginNode.get("token").asText() : "";
    }

    private JsonNode parse(String body) {
        if (body == null || body.trim().isBlank() || body.trim().equals("null")) return null;
        try {
            return mapper.readTree(body.trim());
        } catch (Exception e) {
            return null;
        }
    }    private HttpHeaders authHeader(String t) { HttpHeaders h = new HttpHeaders(); h.setBearerAuth(t); return h; }

    // --- SECTION 1: AUTH & REGISTRATION (TC01 - TC05) ---

    @Test @Order(1) void tc01_registerReturnsJwt() {
        String n = getNonce();
        Map<String, Object> body = Map.of("name", "TC01 User", "email", "tc01_"+n+"@grader.testgen.io", "password", "TestPwd!2026", "phone", "01"+n.substring(0,8));
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode node = parse(res.getBody());
        assertThat(node.get("token").asText()).isNotBlank();
        assertThat(node.get("expiresIn").asLong()).isGreaterThan(0);
    }

    @Test @Order(2) void tc02_loginReturns3SegmentJwt() {
        String n = getNonce(); String e = "tc02_"+n+"@t.io";
        registerUser(e, "TestPwd!2026", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login", Map.of("email",e,"password","TestPwd!2026"), String.class);
        System.out.println(res.getBody());
        System.out.println(res.getStatusCode());
        assertThat(parse(res.getBody()).get("token").asText().split("\\.")).hasSize(3);
    }

    @Test @Order(3) void tc03_readOwnProfileReturnsJson() {
        String n = getNonce(); String e = "tc03_"+n+"@t.io";
        String t = registerUser(e, "P", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, e);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + id, HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(parse(res.getBody()).isObject()).isTrue();
    }

    @Test @Order(4) void tc04_duplicateEmailReturns4xx() {
        String e = "dup_"+getNonce()+"@t.io"; registerUser(e, "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register", Map.of("name","U2","email",e,"password","P","phone",getNonce()), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test @Order(5) void tc05_wrongPasswordReturns401() {
        String e = "tc05_"+getNonce()+"@t.io"; registerUser(e, "Correct", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login", Map.of("email",e,"password","Wrong"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- SECTION 2: SECURITY FILTERS (TC06 - TC13) ---

    @Test void tc06_adminJwtAcceptedOnNonUserCrud() {
        String t = registerUser("adm"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc07_missingAuthReturns401() {
        ResponseEntity<String> res = restTemplate.getForEntity(EVENT_SVC + "/api/events", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc08_tamperedSignatureRejected() {
        String t = registerUser("tamp"+getNonce()+"@t.io", "P", "ADMIN");
        String tampered = t.substring(0, t.lastIndexOf(".") + 1) + "fake";
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(authHeader(tampered)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc09_nonExistentEmailReturns401() {
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login", Map.of("email","ghost@t.io","password","any"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc10_emptyBearerReturns401() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer ");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc11_basicSchemeReturns401() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Basic dXNlcjpwYXNz");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc12_garbageTokenReturns401() {
        HttpHeaders h = new HttpHeaders(); h.set("Authorization", "Bearer not_jwt");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc13_forgedRoleClaimRejected() {
        String t = registerUser("f_"+getNonce()+"@t.io", "P", "ATTENDEE");
        String forged = t.split("\\.")[0] + "." + Base64.getUrlEncoder().encodeToString("{\"role\":\"ADMIN\"}".getBytes()) + "." + t.split("\\.")[2];
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events", HttpMethod.GET, new HttpEntity<>(authHeader(forged)), String.class);
        assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    // --- SECTION 3: VALIDATION & IDOR (TC14 - TC23) ---

    @Test void tc14_missingEmailReturns4xx() {
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/register", Map.of("name","N","password","P"), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc15_massAssignmentRoleRejected() {
        String e = "h"+getNonce()+"@t.io";
        Map<String, Object> body = new HashMap<>(Map.of("name","H","email",e,"password","P","phone","01"+getNonce().substring(0,8)));
        body.put("role", "ADMIN");
        restTemplate.postForEntity(USER_SVC + "/api/auth/register", body, String.class);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, e)).isNotEqualTo("ADMIN");
    }

    @Test void tc16_emptyPasswordReturnsNot2xx() {
        String e = "e"+getNonce()+"@t.io"; registerUser(e, "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.postForEntity(USER_SVC + "/api/auth/login", Map.of("email",e,"password",""), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test void tc17_crossUserReadRejected() {
        String tA = registerUser("a"+getNonce()+"@t.io", "P", "ATTENDEE");
        String eB = "b"+getNonce()+"@t.io"; registerUser(eB, "P", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eB);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idB, HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    @Test void tc18_crossUserUpdateRejected() {
        String tA = registerUser("a18"+getNonce()+"@t.io", "P", "ATTENDEE");
        String eB = "b18"+getNonce()+"@t.io"; registerUser(eB, "P", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eB);
        restTemplate.exchange(USER_SVC + "/api/users/" + idB, HttpMethod.PUT, new HttpEntity<>(Map.of("name","H","email",eB,"password","P","phone","0"), authHeader(tA)), String.class);
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, idB)).isEqualTo("B");
    }

    @Test void tc19_crossUserDeleteRejected() {
        String tA = registerUser("a19"+getNonce()+"@t.io", "P", "ATTENDEE");
        String eB = "b19"+getNonce()+"@t.io"; registerUser(eB, "P", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eB);
        restTemplate.exchange(USER_SVC + "/api/users/" + idB, HttpMethod.DELETE, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, idB)).isEqualTo(1);
    }

    @Test void tc20_ownerCanUpdateSelf() {
        String e = "u20"+getNonce()+"@t.io"; String t = registerUser(e, "P", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, e);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + id, HttpMethod.PUT, new HttpEntity<>(Map.of("name","U20","email",e,"password","P","phone","1"), authHeader(t)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc21_adminReadAnyUser() {
        String tA = registerUser("adm21"+getNonce()+"@t.io", "P", "ADMIN");
        registerUser("u21"+getNonce()+"@t.io", "P", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idU, HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc22_adminUpdateAnyUser() {
        String tA = registerUser("adm22"+getNonce()+"@t.io", "P", "ADMIN");
        String eU = "u22"+getNonce()+"@t.io"; registerUser(eU, "P", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, eU);
        restTemplate.exchange(USER_SVC + "/api/users/" + idU, HttpMethod.PUT, new HttpEntity<>(Map.of("name","U22","email",eU,"password","P","phone","2"), authHeader(tA)), String.class);
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, idU)).isEqualTo("U22");
    }

    @Test void tc23_adminHardDeleteAnyUser() {
        // 1) Register attendee and capture their specific ID using their email
        String attendeeEmail = "u23" + getNonce() + "@t.io";
        registerUser(attendeeEmail, "P", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, attendeeEmail);

        // 2) Obtain an admin token
        String tA = registerUser("adm23" + getNonce() + "@t.io", "P", "ADMIN");
        System.out.println("Admin token: " + tA);
        // 3) Admin DELETE attendee
        ResponseEntity<String> delRes = restTemplate.exchange(
                USER_SVC + "/api/users/" + idU,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeader(tA)),
                String.class
        );
        System.out.println(delRes.getBody());
        assertThat(delRes.getStatusCode().is2xxSuccessful()).isTrue();

        // 4) Verify attendee is physically removed from DB
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, idU)).isEqualTo(0);

        // 5) GET after successful DELETE must return 404
        ResponseEntity<String> res = restTemplate.exchange(
                USER_SVC + "/api/users/" + idU,
                HttpMethod.GET,
                new HttpEntity<>(authHeader(tA)),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- SECTION 4: ACTIVITY & PAGINATION (TC24 - TC34) ---

    @Test void tc24_ownActivityReturnsPaginated() {
        String e = "u24"+getNonce()+"@t.io"; String t = registerUser(e, "P", "ATTENDEE");
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, e);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + id + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        JsonNode body = parse(res.getBody());
        assertThat(body.has("content") && body.has("page") && body.has("totalElements")).isTrue();
    }

    @Test void tc25_nonExistentUserActivity404() {
        String tA = registerUser("adm25"+getNonce()+"@t.io", "P", "ADMIN");
        System.out.println("Admin token: " + tA);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + Long.MAX_VALUE + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        System.out.println(res.getBody());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc26_negativeIdActivity4xx() {
        String tA = registerUser("adm26"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/-1/activity", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc27_stringIdActivity4xx() {
        String tA = registerUser("adm27"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/abc/activity", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc28_sizeZeroActivityNot5xx() {
        String t = registerUser("u28"+getNonce()+"@t.io", "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?size=0", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(res.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test void tc29_negativeSizeActivity4xx() {
        String t = registerUser("u29"+getNonce()+"@t.io", "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?size=-1", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc30_stringSizeActivity4xx() {
        String t = registerUser("u30"+getNonce()+"@t.io", "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?size=abc", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc31_crossUserActivityStrict403() {
        String tA = registerUser("a31"+getNonce()+"@t.io", "P", "ATTENDEE");
        registerUser("b31"+getNonce()+"@t.io", "P", "ATTENDEE");
        Long idB = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idB + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void tc32_adminReadAnyActivity2xx() {
        String tA = registerUser("adm32"+getNonce()+"@t.io", "P", "ADMIN");
        registerUser("u32"+getNonce()+"@t.io", "P", "ATTENDEE");
        Long idU = jdbc.queryForObject("SELECT max(id) FROM users", Long.class);
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/" + idU + "/activity", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc33_negativePageActivity4xx() {
        String t = registerUser("u33"+getNonce()+"@t.io", "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?page=-1", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc34_stringPageActivity4xx() {
        String t = registerUser("u34"+getNonce()+"@t.io", "P", "ATTENDEE");
        ResponseEntity<String> res = restTemplate.exchange(USER_SVC + "/api/users/1/activity?page=abc", HttpMethod.GET, new HttpEntity<>(authHeader(t)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    // --- SECTION 5: ELASTICSEARCH (TC35 - TC47) ---

    @Test void tc35_esSearchHappyPath() {
        String tA = registerUser("adm35"+getNonce()+"@t.io", "P", "ADMIN");
        String k = "Jazz"+getNonce(); Long id = insertEvent(k, "UPCOMING", 4.0);
        restTemplate.exchange(EVENT_SVC + "/api/events/"+id+"/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?query="+k, HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc36_esNoTokenReturns401() {
        ResponseEntity<String> res = restTemplate.getForEntity(EVENT_SVC + "/api/events/search/full-text?query=test", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void tc37_exactMatchCategorical() {
        String tA = registerUser("adm37"+getNonce()+"@t.io", "P", "ADMIN");
        insertEvent("E37", "UPCOMING", 0.0);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?category=CONCERT", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(parse(res.getBody()).get(0).get("category").asText()).isEqualTo("CONCERT");
    }

    @Test void tc38_exactMatchStatus() {
        String tA = registerUser("adm38"+getNonce()+"@t.io", "P", "ADMIN");
        insertEvent("E38", "UPCOMING", 0.0);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?status=UPCOMING", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(parse(res.getBody()).get(0).get("status").asText()).isEqualTo("UPCOMING");
    }

    @Test void tc39_ratingRangeFilter() {
        String tA = registerUser("adm39"+getNonce()+"@t.io", "P", "ADMIN");
        insertEvent("E39", "UPCOMING", 4.5);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?minRating=4.0&maxRating=5.0", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(parse(res.getBody()).get(0).get("rating").asDouble()).isBetween(4.0, 5.0);
    }

    @Test void tc40_invertedRatingReturns4xx() {
        String tA = registerUser("adm40"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?minRating=5.0&maxRating=3.0", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void tc41_noMatchReturnsEmpty() {
        String tA = registerUser("adm41"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?query=TC41NoMatch", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(parse(res.getBody()).size()).isEqualTo(0);
    }

    @Test void tc42_relevanceSorted() {
        String tA = registerUser("adm42"+getNonce()+"@t.io", "P", "ADMIN");
        String word = "Word"+getNonce();
        Long idA = insertEvent(word + " Kitchen", "UPCOMING", 0.0);
        Long idB = insertEvent("Other", "UPCOMING", 0.0);
        jdbc.update(
                "UPDATE events " +
                        "SET details = jsonb_set(details, '{description}', to_jsonb(?::text), true) " +
                        "WHERE id = ?",
                "Best " + word, idB
        );
        restTemplate.exchange(EVENT_SVC + "/api/events/"+idA+"/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        restTemplate.exchange(EVENT_SVC + "/api/events/"+idB+"/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        JsonNode res = parse(restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?query=" + word, HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class).getBody());
        assertThat(res.get(0).get("id").asLong()).isEqualTo(idA);
    }

    @Test void tc43_manualIndex2xx() {
        String tA = registerUser("adm43"+getNonce()+"@t.io", "P", "ADMIN");
        Long id = insertEvent("T43", "UPCOMING", 0.0);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/"+id+"/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test void tc44_esFieldsMatchPg() {
        String tA = registerUser("adm44"+getNonce()+"@t.io", "P", "ADMIN");
        String name = "Sig"+getNonce(); Long id = insertEvent(name, "UPCOMING", 0.0);
        restTemplate.exchange(EVENT_SVC + "/api/events/"+id+"/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        JsonNode hit = parse(restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?query="+name, HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class).getBody()).get(0);
        assertThat(hit.get("name").asText()).isEqualTo(name);
    }

    @Test void tc45_putAutoReindex() {
        String tA = registerUser("adm45"+getNonce()+"@t.io", "P", "ADMIN");
        Long id = insertEvent("Old", "UPCOMING", 0.0);
        restTemplate.exchange(EVENT_SVC + "/api/events/"+id+"/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        Map<String, String> body = Map.of("name","NewN","venue","V","eventDate","2026-05-01T20:00:00","category","CONCERT","status","UPCOMING");
        restTemplate.exchange(EVENT_SVC + "/api/events/"+id, HttpMethod.PUT, new HttpEntity<>(body, authHeader(tA)), String.class);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/search/full-text?query=NewN", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(parse(res.getBody()).size()).isGreaterThanOrEqualTo(1);
    }

    @Test void tc46_index404MaxId() {
        String tA = registerUser("adm46"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/" + Long.MAX_VALUE + "/index", HttpMethod.POST, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc47_index401NoToken() {
        ResponseEntity<String> res = restTemplate.postForEntity(EVENT_SVC + "/api/events/1/index", null, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- SECTION 6: DASHBOARDS (TC48 - TC53) ---

    @Test void tc48_dashboardReturnsDto() {
        String tA = registerUser("adm48"+getNonce()+"@t.io", "P", "ADMIN");
        Long id = insertEvent("D", "UPCOMING", 0.0);
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/"+id+"/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        JsonNode body = parse(res.getBody());
        assertThat(body.has("totalBookings") && body.has("totalRevenue")).isTrue();
    }

    @Test void tc49_dashboardMatchPgAggregates() {
        String tA = registerUser("adm49"+getNonce()+"@t.io", "P", "ADMIN");
        Long eid = insertEvent("A", "UPCOMING", 0.0);
        jdbc.execute("INSERT INTO bookings (user_id, event_id, status, total_amount, contact_email, booking_date) VALUES (1, "+eid+", 'COMPLETED', 150.0, 't@t.io', NOW())");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/"+eid+"/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(parse(res.getBody()).get("totalRevenue").asDouble()).isGreaterThanOrEqualTo(150.0);
    }

    @Test void tc50_dashboardMongoAudit() {
        String tA = registerUser("adm50"+getNonce()+"@t.io", "P", "ADMIN");
        Long eid = insertEvent("M", "UPCOMING", 0.0);
        long pre = mongoTemplate.getCollection("event_events").countDocuments();
        restTemplate.exchange(EVENT_SVC + "/api/events/"+eid+"/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(mongoTemplate.getCollection("event_events").countDocuments()).isGreaterThan(pre);
    }

    @Test void tc51_dashboard404MaxId() {
        String tA = registerUser("adm51"+getNonce()+"@t.io", "P", "ADMIN");
        ResponseEntity<String> res = restTemplate.exchange(EVENT_SVC + "/api/events/" + Long.MAX_VALUE + "/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void tc52_dashboardZeroForNoOrders() {
        String tA = registerUser("adm52"+getNonce()+"@t.io", "P", "ADMIN");
        Long eid = insertEvent("Z", "UPCOMING", 0.0);
        JsonNode dash = parse(restTemplate.exchange(EVENT_SVC + "/api/events/"+eid+"/dashboard", HttpMethod.GET, new HttpEntity<>(authHeader(tA)), String.class).getBody());
        assertThat(dash.get("totalBookings").asInt()).isEqualTo(0);
    }

    @Test void tc53_dashboard401NoToken() {
        ResponseEntity<String> res = restTemplate.getForEntity(EVENT_SVC + "/api/events/1/dashboard", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}