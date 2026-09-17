package com.sereda.datalifepolicyenforcer.execution;

import com.sereda.datalifepolicyenforcer.metadata.Metadata.TableDetails;
import com.sereda.datalifepolicyenforcer.policy.LifecyclePolicy;
import java.time.Instant;
import java.util.List;

public interface LifecyclePolicyEvaluator {
    List<LifecycleAction> evaluate(LifecyclePolicy policy, TableDetails metadata, Instant now);
}

