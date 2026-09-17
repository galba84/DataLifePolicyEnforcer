package com.sereda.datalifepolicyenforcer.execution;

import java.util.List;

public interface LifecyclePolicyExecutor {
    List<LifecycleExecutionPlan> execute(long policyId);
}

