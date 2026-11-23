package com.titu.nexus_claims.repositories;
import com.titu.nexus_claims.models.ClaimTaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ClaimTaskActivityRepository extends JpaRepository<ClaimTaskActivity, Long> {
    List<ClaimTaskActivity> findByTaskClaimIdOrderByIdDesc(long claimId);
}
