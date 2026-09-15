package com.example.datalifepolicyenforcer.execution;

public record LifecycleAction(String schema, String table, String partitionBounds,
                              Long ageDays, ActionType action, String reason) {}

