package com.example.datalifepolicyenforcer.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ExecutionController {
    private final LifecyclePolicyExecutor executor;
    @PostMapping("/api/policies/{id}/dry-run")
    public List<LifecycleExecutionPlan> dryRun(@PathVariable long id) { return executor.execute(id); }
}

