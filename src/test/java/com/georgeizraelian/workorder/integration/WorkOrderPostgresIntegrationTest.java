package com.georgeizraelian.workorder.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.georgeizraelian.workorder.WorkOrderApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WorkOrderApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(WorkOrderPostgresIntegrationTest.PostgresConfiguration.class)
class WorkOrderPostgresIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            // Spring Boot starts this mandatory container before datasource creation and stops it last.
            return new PostgreSQLContainer<>("postgres:16.10-alpine")
                    .withDatabaseName("workorder_integration")
                    .withUsername("integration_user")
                    .withPassword("integration_password");
        }
    }

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString(
            "integration-user:integration-password".getBytes(StandardCharsets.UTF_8));

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void isolateFixturesInVerifiedContainerDatabase() throws Exception {
        // Guard destructive fixture cleanup: never clear an H2, host or deployed database.
        try (var connection = dataSource.getConnection()) {
            assertThat(postgres.isRunning()).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo("workorder_integration");
        }
        // The full application's startup runner may insert demo rows. No test assumes their count/IDs.
        jdbc.update("DELETE FROM work_orders");
    }

    @Test
    void unauthorizedWritesDoNotChangePersistedData() throws Exception {
        var response = request("POST", "/api/work-orders", createBody("Unauthorized", "HIGH"), false);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isPresent();
        assertThat(rowCount()).isZero();

        long id = create("Protected record", "HIGH");
        var patch = request("PATCH", "/api/work-orders/" + id + "/status",
                "{\"status\":\"COMPLETED\"}", false);
        assertThat(patch.statusCode()).isEqualTo(401);
        assertThat(persistedStatus(id)).isEqualTo("OPEN");
    }

    @Test
    void invalidAuthorizedCreateReturnsFieldErrorsWithoutPersisting() throws Exception {
        var response = request("POST", "/api/work-orders",
                "{\"title\":\" \",\"priority\":null,\"requesterEmail\":\"not-an-email\"}", true);
        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode error = json(response);
        assertThat(error.path("status").asInt()).isEqualTo(400);
        assertThat(error.path("path").asText()).isEqualTo("/api/work-orders");
        for (String field : new String[]{"title", "priority", "requesterEmail"}) {
            assertThat(error.path("validationErrors").path(field).asText()).isNotBlank();
        }
        assertThat(rowCount()).isZero();
    }

    @Test
    void createsUpdatesAndRetrievesCommittedWorkOrder() throws Exception {
        var response = request("POST", "/api/work-orders", createBody("Repair scanner", "HIGH"), true);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        JsonNode created = json(response);
        long id = created.path("id").asLong();
        assertThat(id).isPositive();
        assertThat(response.headers().firstValue("Location")).contains("/api/work-orders/" + id);
        assertThat(created.path("title").asText()).isEqualTo("Repair scanner");
        assertThat(created.path("description").asText()).isEqualTo("Deterministic integration fixture");
        assertThat(created.path("status").asText()).isEqualTo("OPEN");
        assertThat(created.path("priority").asText()).isEqualTo("HIGH");
        assertThat(created.path("requesterEmail").asText()).isEqualTo("integration@example.com");
        assertThat(created.path("dueDate").asText()).isEqualTo("2030-01-15");
        assertThat(created.path("createdAt").asText()).isNotBlank();
        assertThat(created.path("updatedAt").asText()).isNotBlank();
        assertThat(rowCount()).isEqualTo(1);
        assertThat(persistedStatus(id)).isEqualTo("OPEN");

        var updated = request("PATCH", "/api/work-orders/" + id + "/status",
                "{\"status\":\"IN_PROGRESS\"}", true);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(updated).path("status").asText()).isEqualTo("IN_PROGRESS");
        // Separate JDBC connection/request verifies committed state, not a cached/mock response.
        assertThat(persistedStatus(id)).isEqualTo("IN_PROGRESS");
        var retrieved = request("GET", "/api/work-orders/" + id, null, false);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        JsonNode persisted = json(retrieved);
        ObjectNode retrievedFields = ((ObjectNode) persisted).deepCopy();
        ObjectNode updatedFields = ((ObjectNode) json(updated)).deepCopy();
        assertThat(retrievedFields.remove(List.of("createdAt", "updatedAt")))
                .isEqualTo(updatedFields.remove(List.of("createdAt", "updatedAt")));
        for (String timestamp : List.of("createdAt", "updatedAt")) {
            // PostgreSQL timestamp(6) rounds nanoseconds from the immediate write response.
            assertThat(Duration.between(LocalDateTime.parse(json(updated).path(timestamp).asText()),
                    LocalDateTime.parse(persisted.path(timestamp).asText())).abs())
                    .isLessThan(Duration.ofNanos(1000));
        }
        LocalDateTime storedUpdatedAt = jdbc.queryForObject("SELECT updated_at FROM work_orders WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), id);
        LocalDateTime storedCreatedAt = jdbc.queryForObject("SELECT created_at FROM work_orders WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), id);
        assertThat(LocalDateTime.parse(persisted.path("updatedAt").asText())).isEqualTo(storedUpdatedAt);
        assertThat(LocalDateTime.parse(persisted.path("createdAt").asText())).isEqualTo(storedCreatedAt);
        assertThat(Duration.between(LocalDateTime.parse(created.path("createdAt").asText()),
                LocalDateTime.parse(persisted.path("createdAt").asText())).abs())
                .isLessThan(Duration.ofNanos(1000));
    }

    @Test
    void malformedAuthorizedStatusDoesNotAlterPersistedRecord() throws Exception {
        long id = create("Invalid status fixture", "LOW");
        var response = request("PATCH", "/api/work-orders/" + id + "/status",
                "{\"status\":\"NOT_A_STATUS\"}", true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).path("message").asText()).isEqualTo("Request contains an invalid value");
        assertThat(persistedStatus(id)).isEqualTo("OPEN");
    }

    @Test
    void combinedFiltersReturnDeterministicNonOverlappingPages() throws Exception {
        long first = create("First matching order", "HIGH");
        create("Priority mismatch", "LOW");
        long second = create("Second matching order", "HIGH");
        long completed = create("Status mismatch", "HIGH");
        assertThat(request("PATCH", "/api/work-orders/" + completed + "/status",
                "{\"status\":\"COMPLETED\"}", true).statusCode()).isEqualTo(200);
        long third = create("Third matching order", "HIGH");
        assertThat(rowCount()).isEqualTo(5);

        JsonNode page0 = page(0);
        assertThat(page0.path("content").size()).isEqualTo(2);
        assertThat(page0.path("content").get(0).path("id").asLong()).isEqualTo(first);
        assertThat(page0.path("content").get(1).path("id").asLong()).isEqualTo(second);
        JsonNode page1 = page(1);
        assertThat(page1.path("content").size()).isEqualTo(1);
        assertThat(page1.path("content").get(0).path("id").asLong()).isEqualTo(third);
        assertThat(page(2).path("content")).isEmpty();
    }

    private JsonNode page(int number) throws Exception {
        var response = request("GET", "/api/work-orders?status=OPEN&priority=HIGH&size=2&sort=id,asc&page="
                + number, null, false);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = json(response);
        assertThat(page.path("page").path("number").asInt()).isEqualTo(number);
        assertThat(page.path("page").path("size").asInt()).isEqualTo(2);
        assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(3);
        assertThat(page.path("page").path("totalPages").asInt()).isEqualTo(2);
        for (JsonNode order : page.path("content")) {
            assertThat(order.path("status").asText()).isEqualTo("OPEN");
            assertThat(order.path("priority").asText()).isEqualTo("HIGH");
        }
        return page;
    }

    private long create(String title, String priority) throws Exception {
        var response = request("POST", "/api/work-orders", createBody(title, priority), true);
        assertThat(response.statusCode()).isEqualTo(201);
        long id = json(response).path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }

    private String createBody(String title, String priority) throws Exception {
        var body = objectMapper.createObjectNode();
        body.put("title", title);
        body.put("description", "Deterministic integration fixture");
        body.put("priority", priority);
        body.put("requesterEmail", "integration@example.com");
        body.put("dueDate", "2030-01-15");
        return objectMapper.writeValueAsString(body);
    }

    private HttpResponse<String> request(String method, String path, String body, boolean authenticated)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
        if (authenticated) {
            builder.header("Authorization", AUTHORIZATION);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private long rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Long.class);
    }

    private String persistedStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM work_orders WHERE id = ?", String.class, id);
    }
}
