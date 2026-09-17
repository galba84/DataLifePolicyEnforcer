package com.sereda.datalifepolicyenforcer;

import com.sereda.datalifepolicyenforcer.execution.*;
import com.sereda.datalifepolicyenforcer.metadata.Metadata.*;
import com.sereda.datalifepolicyenforcer.policy.LifecyclePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.sereda.datalifepolicyenforcer.execution.ActionType.*;

class PartitionPolicyEvaluatorTest {
    private final PartitionPolicyEvaluator evaluator = new PartitionPolicyEvaluator();
    private final Instant now = Instant.parse("2026-09-12T00:00:00Z");
    private static final String OLD = "FOR VALUES FROM ('2025-01-01') TO ('2025-02-01')";
    @ParameterizedTest @CsvSource({"false,false,KEEP", "true,false,ARCHIVE", "false,true,DROP", "true,true,ARCHIVE_AND_DROP"})
    void evaluatesEveryDecision(boolean archive, boolean delete, ActionType expected) {
        var action = evaluator.evaluate(policy(archive, delete), table(true, true, OLD), now).getFirst();
        assertThat(action.action()).isEqualTo(expected);
        assertThat(action.ageDays()).isPositive();
        assertThat(action.reason()).isNotBlank();
    }
    @ParameterizedTest @ValueSource(strings = {"DEFAULT", "FOR VALUES FROM ('2025-01-01') TO (MAXVALUE)",
            "FOR VALUES IN ('2025-01-01')", "FOR VALUES FROM ('2025-01-01', 1) TO ('2025-02-01', 2)",
            "FOR VALUES FROM ('bad') TO ('not a date')"})
    void unknownBoundsAreKept(String bounds) {
        var action = evaluator.evaluate(policy(true, true), table(true, true, bounds), now).getFirst();
        assertThat(action.action()).isEqualTo(KEEP); assertThat(action.ageDays()).isNull();
    }
    @Test void respectsExactThresholdAndFutureData() {
        var p = policy(true, true);
        String bound = "FOR VALUES FROM ('2026-05-01') TO ('2026-06-14')";
        assertThat(evaluator.evaluate(p, table(true, true, bound), now).getFirst().action()).isEqualTo(ARCHIVE_AND_DROP);
        assertThat(evaluator.evaluate(p, table(true, true, bound), now.minusSeconds(1)).getFirst().action()).isEqualTo(ARCHIVE);
        assertThat(evaluator.evaluate(p, table(true, true, "FOR VALUES FROM ('2026-09-01') TO ('2026-10-01')"), now)
                .getFirst().action()).isEqualTo(KEEP);
    }
    @ParameterizedTest @ValueSource(strings = {"2025-02-01 00:00:00", "2025-02-01 00:00:00+00", "2025-02-01 00:00:00+02:00"})
    void supportsTimestampBounds(String upper) {
        assertThat(evaluator.evaluate(policy(true, true), table(true, true,
                "FOR VALUES FROM ('2025-01-01') TO ('" + upper + "')"), now).getFirst().action()).isEqualTo(ARCHIVE_AND_DROP);
    }
    @Test void nonTemporalAndNonPartitionedTablesAreKept() {
        assertThat(evaluator.evaluate(policy(true, true), table(true, false, OLD), now).getFirst().action()).isEqualTo(KEEP);
        assertThat(evaluator.evaluate(policy(true, true), table(false, false, OLD), now).getFirst().action()).isEqualTo(KEEP);
    }
    @Test void retentionIsDeletionFallback() {
        var p = policy(false, true); p.setDeleteAfterDays(null);
        assertThat(evaluator.evaluate(p, table(true, true, OLD), now).getFirst().action()).isEqualTo(DROP);
    }
    private LifecyclePolicy policy(boolean archive, boolean delete) {
        var p = new LifecyclePolicy(); p.setRetentionDays(90); p.setArchiveEnabled(archive);
        p.setArchiveAfterDays(30); p.setDeleteEnabled(delete); p.setDeleteAfterDays(90); return p;
    }
    private TableDetails table(boolean partitioned, boolean temporal, String bound) {
        return new TableDetails(new TableSummary("target", "telemetry", "events", 2, 0, 0, 0, partitioned, false),
                List.of(), List.of(), List.of(), "RANGE", "RANGE (occurred_on)", temporal,
                List.of(new Partition("telemetry", "events_old", bound, 0, 0, 0, false)));
    }
}

