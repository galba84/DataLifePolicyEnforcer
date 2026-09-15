package com.example.datalifepolicyenforcer.execution;

import com.example.datalifepolicyenforcer.metadata.Metadata.TableDetails;
import com.example.datalifepolicyenforcer.policy.LifecyclePolicy;
import java.time.Instant;
import java.util.List;

public interface LifecyclePolicyEvaluator {
    List<LifecycleAction> evaluate(LifecyclePolicy policy, TableDetails metadata, Instant now);
}

