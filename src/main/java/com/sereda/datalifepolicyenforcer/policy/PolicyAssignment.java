package com.sereda.datalifepolicyenforcer.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "policy_assignment", uniqueConstraints = @UniqueConstraint(columnNames = {"database_id", "schema_name", "table_name"}))
@Getter @Setter
public class PolicyAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long policyId;
    private Long databaseId;
    private String schemaName;
    private String tableName;
}

