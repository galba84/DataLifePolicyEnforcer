package com.example.datalifepolicyenforcer.execution;

import com.example.datalifepolicyenforcer.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
@ConditionalOnProperty(name = "lifecycle.scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PolicyScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyScheduler.class);
    private final PolicyService policies;
    private final LifecyclePolicyExecutor executor;
    private final Clock clock;
    private final Map<Long, Due> schedules = new HashMap<>();
    private record Due(String expression, ZonedDateTime next) {}

    @Scheduled(fixedDelay = 1000)
    public void evaluateDuePolicies() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);
        var configured = policies.list();
        schedules.keySet().retainAll(configured.stream().map(p -> p.getId()).toList());
        for (var policy : configured) {
            String expression = policy.getExecutionSchedule();
            if (expression == null || expression.isBlank()) { schedules.remove(policy.getId()); continue; }
            try {
                CronExpression cron = CronExpression.parse(expression);
                Due due = schedules.get(policy.getId());
                if (due == null || !due.expression().equals(expression)) {
                    schedules.put(policy.getId(), new Due(expression, cron.next(now)));
                } else if (due.next() != null && !due.next().isAfter(now)) {
                    // Advance first: a failing target must not cause repeated runs every second.
                    schedules.put(policy.getId(), new Due(expression, cron.next(now)));
                    var plans = executor.execute(policy.getId());
                    LOG.info("Scheduled dry-run evaluated policy {}: {} table plans", policy.getId(), plans.size());
                }
            } catch (RuntimeException ex) {
                LOG.warn("Scheduled dry-run failed for policy {}", policy.getId());
            }
        }
    }
}

