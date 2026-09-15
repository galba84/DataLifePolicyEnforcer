package com.example.datalifepolicyenforcer.connection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import static com.example.datalifepolicyenforcer.connection.ConnectionDtos.*;

@Service
@RequiredArgsConstructor
public class DatabaseConnectionService {
    private final DatabaseConnectionRepository repository;
    private final TargetConnectionFactory connections;

    public List<Response> list() { return repository.findAll().stream().map(ConnectionDtos::response).toList(); }
    public DatabaseConnection require(long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Database not found"));
    }
    @Transactional
    public Response create(Request input) { return save(new DatabaseConnection(), input); }
    @Transactional
    public Response update(long id, Request input) { return save(require(id), input); }
    private Response save(DatabaseConnection c, Request input) {
        c.setName(input.name()); c.setHost(input.host()); c.setPort(input.port());
        c.setDatabase(input.database()); c.setUsername(input.username()); c.setPassword(input.password());
        c.setSslMode(input.sslMode()); c.setStatus("UNKNOWN"); c.setLastTestedAt(null);
        return response(repository.save(c));
    }
    @Transactional
    public void delete(long id) { repository.delete(require(id)); repository.flush(); }
    @Transactional
    public TestResult test(long id) {
        DatabaseConnection config = require(id);
        boolean reachable;
        try (Connection c = connections.open(config); Statement statement = c.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet result = statement.executeQuery("SELECT 1")) { reachable = result.next(); }
        } catch (SQLException ex) {
            reachable = false;
        }
        Instant now = Instant.now();
        config.setStatus(reachable ? "CONNECTED" : "UNREACHABLE"); config.setLastTestedAt(now);
        repository.save(config);
        return new TestResult(reachable, reachable ? "Read-only connection succeeded." : "Connection failed. Check configuration and permissions.", now);
    }
}

