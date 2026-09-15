package com.example.datalifepolicyenforcer.policy;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.example.datalifepolicyenforcer.policy.PolicyDtos.*;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {
    private final PolicyService service;
    @GetMapping public List<LifecyclePolicy> list() { return service.list(); }
    @GetMapping("/{id}") public LifecyclePolicy get(@PathVariable long id) { return service.require(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public LifecyclePolicy create(@Valid @RequestBody Request input) { return service.create(input); }
    @PutMapping("/{id}") public LifecyclePolicy update(@PathVariable long id, @Valid @RequestBody Request input) { return service.update(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
    @GetMapping("/{id}/assignments") public List<PolicyAssignment> assignments(@PathVariable long id) { return service.assignments(id); }
    @PostMapping("/{id}/assignments") @ResponseStatus(HttpStatus.CREATED)
    public PolicyAssignment assign(@PathVariable long id, @Valid @RequestBody AssignmentRequest input) { return service.assign(id, input); }
    @DeleteMapping("/{id}/assignments/{assignmentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable long id, @PathVariable long assignmentId) { service.unassign(id, assignmentId); }
}

