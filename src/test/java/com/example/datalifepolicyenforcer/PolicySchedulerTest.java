package com.example.datalifepolicyenforcer;

import com.example.datalifepolicyenforcer.execution.*;
import com.example.datalifepolicyenforcer.policy.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;

class PolicySchedulerTest {
    @Test void invokesExecutorWhenDueAndIsolatesTargetFailures() {
        PolicyService policies = mock(PolicyService.class);
        LifecyclePolicyExecutor executor = mock(LifecyclePolicyExecutor.class);
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Instant start = Instant.parse("2026-09-12T00:00:00Z");
        when(clock.instant()).thenReturn(start, start.plusSeconds(1), start.plusSeconds(1));
        LifecyclePolicy first = new LifecyclePolicy();
        first.setId(1L); first.setExecutionSchedule("* * * * * *");
        LifecyclePolicy second = new LifecyclePolicy();
        second.setId(2L); second.setExecutionSchedule("* * * * * *");
        when(policies.list()).thenReturn(List.of(first, second));
        when(executor.execute(1L)).thenThrow(new IllegalStateException("unavailable"));
        when(executor.execute(2L)).thenReturn(List.of());
        var scheduler = new PolicyScheduler(policies, executor, clock);
        scheduler.evaluateDuePolicies();
        verifyNoInteractions(executor);
        scheduler.evaluateDuePolicies();
        scheduler.evaluateDuePolicies();
        verify(executor, times(1)).execute(1L);
        verify(executor, times(1)).execute(2L);
    }
}

