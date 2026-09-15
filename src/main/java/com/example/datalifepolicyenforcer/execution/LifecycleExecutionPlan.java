package com.example.datalifepolicyenforcer.execution;

import java.time.Instant;
import java.util.List;

public record LifecycleExecutionPlan(long policyId, long databaseId, String schema, String table,
                                     Instant evaluatedAt, boolean dryRun, List<LifecycleAction> actions) {}

