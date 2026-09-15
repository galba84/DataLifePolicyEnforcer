package com.example.datalifepolicyenforcer;

import com.example.datalifepolicyenforcer.policy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class PolicyValidatorTest {
    private final PolicyValidator validator = new PolicyValidator();
    static Stream<PolicyDtos.Request> invalidPolicies() {
        return Stream.of(
            request(-1, "NONE", null, false, null, false, null, null, null, true),
            request(90, "HASH", 1, false, null, false, null, null, null, true),
            request(90, "RANGE", null, false, null, false, null, null, null, true),
            request(90, "NONE", 1, false, null, false, null, null, null, true),
            request(90, "RANGE", 1, true, 60, true, 30, "s3://archive", null, true),
            request(90, "RANGE", 1, true, null, false, null, "s3://archive", null, true),
            request(90, "RANGE", 1, true, 30, false, null, null, null, true),
            request(null, "NONE", null, false, null, true, null, null, null, true),
            request(90, "NONE", null, false, null, false, null, null, "invalid cron", true),
            request(90, "NONE", null, false, null, false, null, null, null, false),
            request(90, "NONE", null, false, null, false, 0, null, null, true)
        );
    }
    @ParameterizedTest @MethodSource("invalidPolicies")
    void rejectsInvalidPolicy(PolicyDtos.Request request) {
        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsDeclarativePolicyAndDefaultDryRun() {
        assertThatCode(() -> validator.validate(request(90, "RANGE", 1, true, 30, true, 90,
                "s3://archive", "0 0 2 * * *", null))).doesNotThrowAnyException();
    }
    static PolicyDtos.Request request(Integer retention, String strategy, Integer interval, boolean archive,
                                     Integer archiveAfter, boolean delete, Integer deleteAfter, String storage,
                                     String schedule, Boolean dryRun) {
        return new PolicyDtos.Request("test", retention, strategy, interval, archive, archiveAfter, delete,
                deleteAfter, storage, schedule, dryRun);
    }
}

