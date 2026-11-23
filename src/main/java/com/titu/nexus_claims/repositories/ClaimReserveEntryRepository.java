package com.titu.nexus_claims.repositories;

import com.titu.nexus_claims.models.ClaimReserveEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClaimReserveEntryRepository extends JpaRepository<ClaimReserveEntry, Long> {
    List<ClaimReserveEntry> findByClaimIdOrderByIdAsc(Long claimId);
    Optional<ClaimReserveEntry> findByClaimIdAndRequestKey(Long claimId, String requestKey);
}
