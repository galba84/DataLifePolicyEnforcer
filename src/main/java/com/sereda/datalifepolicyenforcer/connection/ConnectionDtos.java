package com.sereda.datalifepolicyenforcer.connection;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ConnectionDtos {
    private ConnectionDtos() {}
    public record Request(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._:-]+") @Size(max = 253) String host,
            @Min(1) @Max(65535) int port,
            @NotBlank @Size(max = 200) String database,
            @NotBlank @Size(max = 200) String username,
            @NotNull @Size(max = 4096) String password,
            @NotBlank @Pattern(regexp = "disable|allow|prefer|require|verify-ca|verify-full") String sslMode) {}
    public record Response(Long id, String name, String host, int port, String database,
                           String sslMode, String status, Instant lastTestedAt) {}
    public record TestResult(boolean reachable, String message, Instant testedAt) {}
    public static Response response(DatabaseConnection c) {
        return new Response(c.getId(), c.getName(), c.getHost(), c.getPort(), c.getDatabase(),
                c.getSslMode(), c.getStatus(), c.getLastTestedAt());
    }
}

