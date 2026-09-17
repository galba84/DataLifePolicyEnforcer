package com.sereda.datalifepolicyenforcer.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lifecycle_policy")
@Getter @Setter
public class LifecyclePolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Integer retentionDays;
    private String partitionStrategy;
    private Integer partitionIntervalDays;
    private boolean archiveEnabled;
    private Integer archiveAfterDays;
    private boolean deleteEnabled;
    private Integer deleteAfterDays;
    private String targetObjectStorage;
    private String executionSchedule;
    private boolean dryRun = true;
}

