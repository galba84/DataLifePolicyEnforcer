package com.example.datalifepolicyenforcer.policy;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import static com.example.datalifepolicyenforcer.policy.PolicyDtos.Request;

@Component
public class PolicyValidator {
    public void validate(Request p) {
        positive(p.retentionDays()); positive(p.partitionIntervalDays());
        positive(p.archiveAfterDays()); positive(p.deleteAfterDays());
        String strategy = p.partitionStrategy() == null ? "NONE" : p.partitionStrategy();
        if (!strategy.equals("NONE") && !strategy.equals("RANGE"))
            throw new IllegalArgumentException("Supported partition strategies are NONE and RANGE.");
        if (strategy.equals("RANGE") && p.partitionIntervalDays() == null)
            throw new IllegalArgumentException("RANGE partitioning requires partitionIntervalDays.");
        if (strategy.equals("NONE") && p.partitionIntervalDays() != null)
            throw new IllegalArgumentException("A partition interval requires RANGE strategy.");
        if (p.archiveEnabled() && (p.archiveAfterDays() == null || p.targetObjectStorage() == null || p.targetObjectStorage().isBlank()))
            throw new IllegalArgumentException("Archiving requires archiveAfterDays and targetObjectStorage.");
        Integer deletion = p.deleteAfterDays() == null ? p.retentionDays() : p.deleteAfterDays();
        if (p.deleteEnabled() && deletion == null)
            throw new IllegalArgumentException("Deletion requires deleteAfterDays or retentionDays.");
        if (p.archiveAfterDays() != null && deletion != null && deletion < p.archiveAfterDays())
            throw new IllegalArgumentException("Delete period cannot be shorter than archive period.");
        if (p.executionSchedule() != null && !p.executionSchedule().isBlank()
                && !CronExpression.isValidExpression(p.executionSchedule()))
            throw new IllegalArgumentException("Execution schedule must be a valid six-field Spring cron expression.");
        if (Boolean.FALSE.equals(p.dryRun()))
            throw new IllegalArgumentException("Only dry-run mode is supported in this version.");
    }
    private void positive(Integer value) {
        if (value != null && value <= 0) throw new IllegalArgumentException("Policy periods must be positive numbers of days.");
    }
}

