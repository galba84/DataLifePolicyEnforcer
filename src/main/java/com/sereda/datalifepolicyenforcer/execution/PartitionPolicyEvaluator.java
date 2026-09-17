package com.sereda.datalifepolicyenforcer.execution;

import com.sereda.datalifepolicyenforcer.metadata.Metadata.*;
import com.sereda.datalifepolicyenforcer.policy.LifecyclePolicy;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import static com.sereda.datalifepolicyenforcer.execution.ActionType.*;

@Component
public class PartitionPolicyEvaluator implements LifecyclePolicyEvaluator {
    // Accept only one literal per bound; default, composite, expression and infinite upper bounds stay KEEP.
    private static final Pattern RANGE = Pattern.compile("^FOR VALUES FROM \\((?:MINVALUE|'[^']+')\\) TO \\('([^']+)'\\)$");

    @Override
    public List<LifecycleAction> evaluate(LifecyclePolicy policy, TableDetails metadata, Instant now) {
        if (!metadata.table().partitioned()) {
            return List.of(new LifecycleAction(metadata.table().schema(), metadata.table().table(), null, null, KEEP,
                    "Table is not partitioned; row-level operations are not supported."));
        }
        if (metadata.partitions().isEmpty()) {
            return List.of(new LifecycleAction(metadata.table().schema(), metadata.table().table(), null, null, KEEP,
                    "No child partitions exist. Partition creation is declarative only in this version."));
        }
        return metadata.partitions().stream().map(partition -> evaluatePartition(policy, metadata, partition, now)).toList();
    }

    private LifecycleAction evaluatePartition(LifecyclePolicy policy, TableDetails metadata, Partition p, Instant now) {
        if (!metadata.temporalRangeKey())
            return action(p, null, KEEP, "Only single-column date/timestamp RANGE keys can be aged safely.");
        Instant upper = upperBound(p.bounds());
        if (upper == null)
            return action(p, null, KEEP, "Unknown, default, unbounded or unsupported partition bound; manual review required.");
        long age = Math.max(0, ChronoUnit.DAYS.between(upper, now));
        if (upper.isAfter(now)) return action(p, age, KEEP, "Partition includes current or future data.");
        Integer deleteDays = policy.getDeleteAfterDays() == null ? policy.getRetentionDays() : policy.getDeleteAfterDays();
        boolean archive = policy.isArchiveEnabled() && policy.getArchiveAfterDays() != null && age >= policy.getArchiveAfterDays();
        boolean drop = policy.isDeleteEnabled() && deleteDays != null && age >= deleteDays;
        if (archive && drop)
            return action(p, age, ARCHIVE_AND_DROP, "Archive and delete thresholds reached from the exclusive upper bound; archive must succeed before any future drop.");
        if (archive) return action(p, age, ARCHIVE, "Archive threshold reached from the exclusive upper bound.");
        if (drop) return action(p, age, DROP, "Delete threshold reached from the exclusive upper bound; archiving is disabled.");
        return action(p, age, KEEP, "No enabled lifecycle threshold reached from the exclusive upper bound.");
    }
    private LifecycleAction action(Partition p, Long age, ActionType type, String reason) {
        return new LifecycleAction(p.schema(), p.table(), p.bounds(), age, type, reason);
    }
    private Instant upperBound(String bounds) {
        if (bounds == null) return null;
        var matcher = RANGE.matcher(bounds);
        if (!matcher.matches()) return null;
        String value = matcher.group(1).replace(' ', 'T');
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (value.matches(".*[+-]\\d{2}$")) value += ":00";
            try { return OffsetDateTime.parse(value).toInstant(); }
            catch (DateTimeParseException ignored) { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC); }
        } catch (DateTimeException ex) {
            return null;
        }
    }
}

