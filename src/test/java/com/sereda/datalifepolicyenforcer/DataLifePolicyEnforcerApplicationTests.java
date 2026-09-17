package com.sereda.datalifepolicyenforcer;

import com.sereda.datalifepolicyenforcer.connection.*;
import com.sereda.datalifepolicyenforcer.execution.*;
import com.sereda.datalifepolicyenforcer.metadata.*;
import com.sereda.datalifepolicyenforcer.policy.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DataLifePolicyEnforcerApplicationTests {
    @Container static final PostgreSQLContainer control = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("control");
    @Container static final PostgreSQLContainer target = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("target").withInitScript("target-fixture.sql");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", control::getJdbcUrl);
        registry.add("spring.datasource.username", control::getUsername);
        registry.add("spring.datasource.password", control::getPassword);
    }
    @Autowired DatabaseConnectionService databases;
    @Autowired MetadataReader metadata;
    @Autowired TargetConnectionFactory connections;
    @Autowired PolicyService policies;
    @Autowired LifecyclePolicyExecutor executor;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    long databaseId;

    @BeforeEach void configureTarget() { databaseId = databases.create(connectionRequest()).id(); }
    private ConnectionDtos.Request connectionRequest() {
        return new ConnectionDtos.Request("target", target.getHost(), target.getMappedPort(5432), "target",
                "metadata_reader", "target-test-secret", "disable");
    }
    @Test void discoversSchemasTablesColumnsIndexesAndSizes() {
        assertThat(metadata.schemas(databaseId)).contains("telemetry", "public").noneMatch(s -> s.startsWith("pg_"));
        assertThat(metadata.tables(databaseId, "telemetry")).extracting(Metadata.TableSummary::table)
                .contains("events", "events_old", "plain", "odd'table");
        var details = metadata.table(databaseId, "telemetry", "events");
        assertThat(details.table().database()).isEqualTo("target");
        assertThat(details.table().partitioned()).isTrue();
        assertThat(details.columns()).extracting(Metadata.Column::name).containsExactly("id", "occurred_on", "payload");
        assertThat(details.columns().getFirst().nullable()).isFalse();
        assertThat(details.columns().get(2).defaultValue()).contains("sample");
        assertThat(details.primaryKeys()).containsExactly("id", "occurred_on");
        assertThat(details.indexes()).anyMatch(Metadata.Index::primary).anyMatch(i -> i.name().equals("events_payload_idx"));
        var leaf = metadata.table(databaseId, "telemetry", "events_old").table();
        assertThat(leaf.estimatedRows()).isEqualTo(1);
        assertThat(leaf.totalSize()).isPositive().isEqualTo(leaf.tableSize() + leaf.indexSize());
        assertThat(metadata.table(databaseId, "telemetry", "odd'table").columns().getFirst().name()).isEqualTo("odd column");
        assertThatThrownBy(() -> metadata.table(databaseId, "telemetry", "events' OR 1=1 --"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void discoversPartitionBoundsAndNestedStructure() {
        var details = metadata.table(databaseId, "telemetry", "events");
        assertThat(details.partitionStrategy()).isEqualTo("RANGE");
        assertThat(details.partitionKey()).isEqualTo("RANGE (occurred_on)");
        assertThat(details.temporalRangeKey()).isTrue();
        assertThat(details.partitions()).hasSize(4);
        assertThat(details.partitions()).anySatisfy(p -> {
            assertThat(p.table()).isEqualTo("events_old");
            assertThat(p.bounds()).isEqualTo("FOR VALUES FROM ('2025-01-01') TO ('2025-02-01')");
            assertThat(p.totalSize()).isPositive();
        });
        assertThat(metadata.table(databaseId, "telemetry", "nested").partitions().getFirst().partitioned()).isTrue();
        var nested = metadata.table(databaseId, "telemetry", "nested_2025");
        assertThat(nested.partitionStrategy()).isEqualTo("LIST");
        assertThat(nested.temporalRangeKey()).isFalse();
        assertThat(nested.partitions().getFirst().bounds()).isEqualTo("FOR VALUES IN (1)");
    }
    @Test void targetTransactionsRejectWritesEvenForPrivilegedAccounts() throws Exception {
        var config = databases.require(databaseId);
        config.setUsername(target.getUsername()); config.setPassword(target.getPassword());
        try (var connection = connections.open(config); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SHOW transaction_read_only")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("on");
            }
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM telemetry.events"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("read-only");
        }
    }
    @Test void dryRunPreservesDataCatalogAndControlIsolation() throws Exception {
        var before = snapshot();
        var policy = policies.create(PolicyValidatorTest.request(90, "RANGE", 1, true, 30, true, 90, "s3://archive", null, true));
        policies.assign(policy.getId(), new PolicyDtos.AssignmentRequest(databaseId, "telemetry", "events"));
        var plans = executor.execute(policy.getId());
        assertThat(plans).hasSize(1); assertThat(plans.getFirst().dryRun()).isTrue();
        assertThat(plans.getFirst().actions()).anyMatch(a -> a.table().equals("events_old") && a.action() == ActionType.ARCHIVE_AND_DROP);
        assertThat(plans.getFirst().actions()).anyMatch(a -> a.table().equals("events_default") && a.action() == ActionType.KEEP);
        assertThat(snapshot()).isEqualTo(before);
        try (var c = DriverManager.getConnection(target.getJdbcUrl(), target.getUsername(), target.getPassword());
             var s = c.createStatement(); var r = s.executeQuery("SELECT to_regclass('public.lifecycle_policy'), to_regclass('public.database_connection'), to_regclass('public.flyway_schema_history')")) {
            r.next(); assertThat(r.getString(1)).isNull(); assertThat(r.getString(2)).isNull(); assertThat(r.getString(3)).isNull();
        }
        policies.delete(policy.getId());
    }
    @Test void restProtectsCredentialsAndRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/databases")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/databases").with(user("admin")).contentType("application/json")
                .content(json.writeValueAsString(connectionRequest()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/databases").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("target-test-secret"))))
                .andExpect(jsonPath("$[0].password").doesNotExist()).andExpect(jsonPath("$[0].username").doesNotExist());
        mvc.perform(post("/api/databases").with(user("admin")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(connectionRequest()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.username").doesNotExist());
        mvc.perform(post("/api/databases/" + databaseId + "/test").with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reachable").value(true));
        var bad = connectionRequest();
        String invalid = json.writeValueAsString(bad).replace("\"port\":" + bad.port(), "\"port\":0");
        mvc.perform(post("/api/databases").with(user("admin")).with(csrf()).contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("target-test-secret"))));
    }
    @Test void policyRestCrudAssignmentValidationAndDryRun() throws Exception {
        var input = PolicyValidatorTest.request(90, "RANGE", 1, true, 30, true, 90, "s3://archive", null, true);
        String body = mvc.perform(post("/api/policies").with(user("admin")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        mvc.perform(put("/api/policies/" + id).with(user("admin")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isOk());
        String assignment = json.writeValueAsString(new PolicyDtos.AssignmentRequest(databaseId, "telemetry", "events"));
        mvc.perform(post("/api/policies/" + id + "/assignments").with(user("admin")).with(csrf())
                .contentType("application/json").content(assignment)).andExpect(status().isCreated());
        mvc.perform(post("/api/policies/" + id + "/assignments").with(user("admin")).with(csrf())
                .contentType("application/json").content(assignment)).andExpect(status().isConflict());
        mvc.perform(post("/api/policies/" + id + "/dry-run").with(user("admin")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].dryRun").value(true));
        mvc.perform(post("/api/policies").with(user("admin")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(input).replace("\"dryRun\":true", "\"dryRun\":false")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/policies/" + id).with(user("admin")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/policies/" + id).with(user("admin"))).andExpect(status().isNotFound());
    }
    @Test void servesUiAndOpenApi() throws Exception {
        mvc.perform(get("/").with(user("admin"))).andExpect(status().isOk());
        mvc.perform(get("/index.html").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dry-run execution plan")));
        mvc.perform(get("/app.js").with(user("admin"))).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs").with(user("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/policies/{id}/dry-run']").exists());
    }
    private List<String> snapshot() throws SQLException {
        List<String> state = new ArrayList<>();
        try (var c = DriverManager.getConnection(target.getJdbcUrl(), target.getUsername(), target.getPassword()); var s = c.createStatement()) {
            try (var rows = s.executeQuery("SELECT id, occurred_on, payload FROM telemetry.events ORDER BY id")) {
                while (rows.next()) state.add(rows.getLong(1) + "|" + rows.getString(2) + "|" + rows.getString(3));
            }
            try (var rows = s.executeQuery("""
                    SELECT c.oid, c.relname, pg_get_expr(c.relpartbound, c.oid)
                    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'telemetry' ORDER BY c.oid
                    """)) {
                while (rows.next()) state.add(rows.getLong(1) + "|" + rows.getString(2) + "|" + rows.getString(3));
            }
        }
        return state;
    }
}
