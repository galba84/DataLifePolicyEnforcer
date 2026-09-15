package com.example.datalifepolicyenforcer.connection;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.example.datalifepolicyenforcer.connection.ConnectionDtos.*;

@RestController
@RequestMapping("/api/databases")
@RequiredArgsConstructor
public class DatabaseConnectionController {
    private final DatabaseConnectionService service;
    @GetMapping public List<Response> list() { return service.list(); }
    @GetMapping("/{id}") public Response get(@PathVariable long id) { return ConnectionDtos.response(service.require(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request input) { return service.create(input); }
    @PutMapping("/{id}") public Response update(@PathVariable long id, @Valid @RequestBody Request input) { return service.update(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
    @PostMapping("/{id}/test") public TestResult test(@PathVariable long id) { return service.test(id); }
}

