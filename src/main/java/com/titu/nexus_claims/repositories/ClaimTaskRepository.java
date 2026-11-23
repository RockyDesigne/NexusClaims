package com.titu.nexus_claims.repositories;
import com.titu.nexus_claims.models.ClaimTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClaimTaskRepository extends JpaRepository<ClaimTask, Long> {
    List<ClaimTask> findByClaimIdOrderByCreatedAtDescIdDesc(long claimId);
    Optional<ClaimTask> findByClaimIdAndRequestKey(long claimId, String requestKey);
    Optional<ClaimTask> findByIdAndClaimId(long id, long claimId);
    long countByClaimIdAndStatusNot(long claimId, String status);
}
