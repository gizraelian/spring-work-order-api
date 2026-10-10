package com.georgeizraelian.workorder.integration;

import com.georgeizraelian.workorder.WorkOrderApplication;
import com.georgeizraelian.workorder.model.WorkOrder;
import com.georgeizraelian.workorder.model.WorkOrderPriority;
import com.georgeizraelian.workorder.model.WorkOrderStatus;
import com.georgeizraelian.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaLifecycleIntegrationTest {
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.10-alpine")
            .withDatabaseName("schema_lifecycle").withUsername("fixture_user").withPassword("fixture_password");

    @BeforeAll
    static void startDisposablePostgres() { postgres.start(); }

    @AfterAll
    static void stopDisposablePostgres() { postgres.stop(); }

    @BeforeEach
    void resetOnlyDisposableSchema() throws Exception {
        assertThat(postgres.isRunning()).isTrue();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getCatalog()).isEqualTo("schema_lifecycle");
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void freshMigrationAndRestartPreserveSchemaHistoryAndInsertedData() {
        long id;
        Object installedOn;
        long tableOid;
        try (var first = start("postgres-integration")) {
            var jdbc = first.getBean(JdbcTemplate.class);
            assertThat(first.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success", Long.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_orders", Long.class)).isZero();
            id = first.getBean(WorkOrderRepository.class).saveAndFlush(new WorkOrder("Survives restart", null,
                    WorkOrderStatus.OPEN, WorkOrderPriority.HIGH, "fixture@example.com", null)).getId();
            installedOn = jdbc.queryForObject("SELECT installed_on FROM flyway_schema_history WHERE version='1'", Object.class);
            tableOid = jdbc.queryForObject("SELECT 'work_orders'::regclass::oid::bigint", Long.class);
        }
        try (var second = start("postgres-integration")) {
            var jdbc = second.getBean(JdbcTemplate.class);
            assertThat(second.getBean(WorkOrderRepository.class).findById(id).orElseThrow().getTitle())
                    .isEqualTo("Survives restart");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT installed_on FROM flyway_schema_history WHERE version='1'", Object.class))
                    .isEqualTo(installedOn);
            assertThat(jdbc.queryForObject("SELECT 'work_orders'::regclass::oid::bigint", Long.class)).isEqualTo(tableOid);
        }
    }

    @Test
    void fullProductionStartupRejectsMissingCredentialsAndUnapprovedMigrations() {
        assertThatThrownBy(() -> { try (var ignored = start("prod", "--app.security.password=")) { } })
                .hasStackTraceContaining("Explicit production configuration required: app.security.password");
        assertThatThrownBy(() -> { try (var ignored = start("prod", "--spring.flyway.enabled=true")) { } })
                .hasStackTraceContaining("Production migrations require separate approval");
    }

    @Test
    void missingSchemaFailsValidationWithoutMigrations() {
        assertThatThrownBy(() -> { try (var ignored = start("postgres-integration", "--spring.flyway.enabled=false")) { } })
                .hasStackTraceContaining("Schema-validation: missing table [work_orders]");
    }

    @Test
    void nonEmptyUnmanagedSchemaIsNotSilentlyBaselined() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unmanaged_fixture (id BIGINT)");
        }
        assertThatThrownBy(() -> { try (var ignored = start("postgres-integration")) { } })
                .hasStackTraceContaining("non-empty schema");
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertThat(jdbc.queryForObject("SELECT to_regclass('flyway_schema_history') IS NULL", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT to_regclass('unmanaged_fixture') IS NOT NULL", Boolean.class)).isTrue();
        }
    }

    @Test
    void demoSeedRequiresDevProfileAndOptInAndDoesNotDuplicateOnRestart() {
        try (var first = start("dev")) {
            assertThat(first.getBean(WorkOrderRepository.class).count()).isZero();
        }
        try (var seeded = start("dev", "--app.demo.seed=true")) {
            assertThat(seeded.getBean(WorkOrderRepository.class).count()).isEqualTo(4);
        }
        try (var restart = start("dev", "--app.demo.seed=true")) {
            assertThat(restart.getBean(WorkOrderRepository.class).count()).isEqualTo(4);
        }
        try (var production = start("prod", "--app.demo.seed=true")) {
            assertThat(production.containsBean("loadDemoWorkOrders")).isFalse();
            assertThat(production.getBean(WorkOrderRepository.class).count()).isEqualTo(4);
        }
    }

    @Test
    void migrationMetadataMatchesExpectedJpaColumnAndConstraintContract() {
        try (var app = start("postgres-integration")) {
            var jdbc = app.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForList("SELECT column_name || ':' || data_type || ':' || is_nullable FROM information_schema.columns "
                            + "WHERE table_schema='public' AND table_name='work_orders' ORDER BY column_name", String.class))
                    .containsExactly("created_at:timestamp without time zone:NO", "description:character varying:YES",
                            "due_date:date:YES", "id:bigint:NO", "priority:character varying:NO",
                            "requester_email:character varying:NO", "status:character varying:NO",
                            "title:character varying:NO", "updated_at:timestamp without time zone:NO");
            assertThat(jdbc.queryForList("SELECT character_maximum_length FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='work_orders' AND data_type='character varying' "
                    + "ORDER BY column_name", Integer.class)).containsExactly(2000, 10, 254, 20, 120);
            assertThat(jdbc.queryForObject("SELECT identity_generation FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='work_orders' AND column_name='id'", String.class))
                    .isEqualTo("BY DEFAULT");
            assertThat(jdbc.queryForList("SELECT datetime_precision FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='work_orders' AND column_name IN ('created_at','updated_at')",
                    Integer.class)).containsExactly(6, 6);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE conrelid='work_orders'::regclass "
                    + "AND contype='c'", Long.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO work_orders(title,status,priority,requester_email,created_at,updated_at) "
                    + "VALUES ('Invalid','UNKNOWN','HIGH','fixture@example.com',now(),now())"))
                    .hasMessageContaining("work_orders_status_check");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO work_orders(title,status,priority,requester_email,created_at,updated_at) "
                    + "VALUES ('Invalid','OPEN','CRITICAL','fixture@example.com',now(),now())"))
                    .hasMessageContaining("work_orders_priority_check");
        }
    }

    private ConfigurableApplicationContext start(String profile, String... overrides) {
        var args = new ArrayList<>(Arrays.asList("--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword(),
                "--app.security.username=fixture-user", "--app.security.password=fixture-password",
                "--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off"));
        for (String override : overrides) {
            String key = override.substring(0, override.indexOf('=') + 1);
            args.removeIf(argument -> argument.startsWith(key));
            args.add(override);
        }
        return new SpringApplicationBuilder(WorkOrderApplication.class).profiles(profile).run(args.toArray(String[]::new));
    }
}
