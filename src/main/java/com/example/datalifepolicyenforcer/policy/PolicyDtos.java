package com.example.datalifepolicyenforcer.policy;

import jakarta.validation.constraints.*;

public final class PolicyDtos {
    private PolicyDtos() {}
    public record Request(
            @NotBlank @Size(max = 200) String name,
            @Positive Integer retentionDays,
            @Pattern(regexp = "NONE|RANGE") String partitionStrategy,
            @Positive Integer partitionIntervalDays,
            boolean archiveEnabled,
            @Positive Integer archiveAfterDays,
            boolean deleteEnabled,
            @Positive Integer deleteAfterDays,
            @Size(max = 500) String targetObjectStorage,
            @Size(max = 100) String executionSchedule,
            Boolean dryRun) {}
    public record AssignmentRequest(@NotNull @Positive Long databaseId,
                                    @NotBlank @Size(max = 63) String schemaName,
                                    @NotBlank @Size(max = 63) String tableName) {}
}

