package com.example.datalifepolicyenforcer.execution;

import com.example.datalifepolicyenforcer.metadata.MetadataReader;
import com.example.datalifepolicyenforcer.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DryRunPolicyExecutor implements LifecyclePolicyExecutor {
    private final PolicyService policies;
    private final MetadataReader metadata;
    private final LifecyclePolicyEvaluator evaluator;
    private final Clock clock;

    @Override
    public List<LifecycleExecutionPlan> execute(long policyId) {
        var policy = policies.require(policyId);
        Instant now = clock.instant();
        return policies.assignments(policyId).stream().map(a -> {
            var table = metadata.table(a.getDatabaseId(), a.getSchemaName(), a.getTableName());
            return new LifecycleExecutionPlan(policyId, a.getDatabaseId(), a.getSchemaName(), a.getTableName(),
                    now, true, evaluator.evaluate(policy, table, now));
        }).toList();
    }
}

