package com.adelaide.sttapp;

import com.adelaide.sttapp.service.AppStatsService;
import com.adelaide.sttapp.service.ShutdownService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WHY: the REST API rubric's Distinction band requires "full and correct
 * error handling as per the YAML spec on all your API calls" - this
 * explicitly includes the admin/stats endpoints, not just /transcribe.
 * These tests check the response SHAPE against the real YAML spec
 * (additionalProperties: false - exact field names, nothing extra) and
 * the shutdown endpoint's 202/409 status-code contract, WITHOUT ever
 * triggering a real JVM exit: ShutdownService is replaced with a Mockito
 * stub via @MockBean, so tryInitiateShutdown() never actually calls
 * System.exit - see ShutdownService's Javadoc for why that separation
 * exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AppStatsService stats;

    @MockBean
    private ShutdownService shutdownService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void uptimeResponseMatchesYamlSpecExactly() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/admin/uptime", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());

        // additionalProperties: false in the YAML - exactly these 3 fields, nothing else.
        assertTrue(body.has("utcServerStart"));
        assertTrue(body.has("utcNow"));
        assertTrue(body.has("serverUptimeSeconds"));
        assertEquals(3, body.size(), "UptimeResponse must contain exactly 3 fields per the YAML spec");
        assertTrue(body.get("serverUptimeSeconds").isNumber());
    }

    @Test
    void globalStatsResponseMatchesYamlSpecExactly() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/global/stats", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());

        assertTrue(body.has("inputTokens"));
        assertTrue(body.has("outputTokens"));
        assertEquals(2, body.size(), "GlobalStatsResponse must contain exactly 2 fields per the YAML spec");
    }

    @Test
    void shutdownReturns202ThenConflictOnSecondCallWithoutEverExitingTheJvm() {
        Mockito.when(shutdownService.tryInitiateShutdown()).thenReturn(true, false);

        ResponseEntity<String> first = restTemplate.postForEntity("/api/v1/admin/shutdown", null, String.class);
        assertEquals(HttpStatus.ACCEPTED, first.getStatusCode());
        assertTrue(first.getBody() != null && first.getBody().contains("Graceful shutdown requested."));

        ResponseEntity<String> second = restTemplate.postForEntity("/api/v1/admin/shutdown", null, String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertTrue(second.getBody() != null && second.getBody().contains("already in progress"));

        // Proves the JVM really is still alive and serving requests - if the
        // real shutdown mechanism had fired, this call itself would fail.
        ResponseEntity<String> stillAlive = restTemplate.getForEntity("/api/v1/admin/uptime", String.class);
        assertEquals(HttpStatus.OK, stillAlive.getStatusCode());
    }
}
