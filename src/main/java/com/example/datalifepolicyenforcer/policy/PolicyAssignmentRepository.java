package com.example.datalifepolicyenforcer.policy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PolicyAssignmentRepository extends JpaRepository<PolicyAssignment, Long> {
    List<PolicyAssignment> findByPolicyId(long policyId);
    void deleteByPolicyId(long policyId);
}

