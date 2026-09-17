package com.sereda.datalifepolicyenforcer.execution;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
public class ExecutionConfiguration {
    @Bean public Clock clock() { return Clock.systemUTC(); }
}

