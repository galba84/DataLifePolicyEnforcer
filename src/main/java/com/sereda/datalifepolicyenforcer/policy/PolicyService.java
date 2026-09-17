package com.sereda.datalifepolicyenforcer.policy;

import com.sereda.datalifepolicyenforcer.connection.DatabaseConnectionService;
import com.sereda.datalifepolicyenforcer.metadata.MetadataReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import static com.sereda.datalifepolicyenforcer.policy.PolicyDtos.*;

@Service
@RequiredArgsConstructor
public class PolicyService {
    private final LifecyclePolicyRepository policies;
    private final PolicyAssignmentRepository assignments;
    private final PolicyValidator validator;
    private final DatabaseConnectionService databases;
    private final MetadataReader metadata;

    public List<LifecyclePolicy> list() { return policies.findAll(); }
    public LifecyclePolicy require(long id) {
        return policies.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
    }
    @Transactional public LifecyclePolicy create(Request input) { return save(new LifecyclePolicy(), input); }
    @Transactional public LifecyclePolicy update(long id, Request input) { return save(require(id), input); }
    private LifecyclePolicy save(LifecyclePolicy p, Request input) {
        validator.validate(input);
        p.setName(input.name()); p.setRetentionDays(input.retentionDays());
        p.setPartitionStrategy(input.partitionStrategy() == null ? "NONE" : input.partitionStrategy());
        p.setPartitionIntervalDays(input.partitionIntervalDays()); p.setArchiveEnabled(input.archiveEnabled());
        p.setArchiveAfterDays(input.archiveAfterDays()); p.setDeleteEnabled(input.deleteEnabled());
        p.setDeleteAfterDays(input.deleteAfterDays()); p.setTargetObjectStorage(input.targetObjectStorage());
        p.setExecutionSchedule(input.executionSchedule()); p.setDryRun(true);
        return policies.save(p);
    }
    @Transactional public void delete(long id) {
        LifecyclePolicy policy = require(id);
        assignments.deleteByPolicyId(id); assignments.flush(); policies.delete(policy);
    }
    public List<PolicyAssignment> assignments(long id) { require(id); return assignments.findByPolicyId(id); }
    @Transactional public PolicyAssignment assign(long id, AssignmentRequest input) {
        require(id); databases.require(input.databaseId());
        metadata.table(input.databaseId(), input.schemaName(), input.tableName());
        PolicyAssignment a = new PolicyAssignment();
        a.setPolicyId(id); a.setDatabaseId(input.databaseId());
        a.setSchemaName(input.schemaName()); a.setTableName(input.tableName());
        return assignments.saveAndFlush(a);
    }
    @Transactional public void unassign(long id, long assignmentId) {
        require(id);
        PolicyAssignment a = assignments.findById(assignmentId)
                .filter(value -> value.getPolicyId().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        assignments.delete(a);
    }
}

