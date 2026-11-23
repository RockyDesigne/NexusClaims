package com.titu.nexus_claims.repositories;

import com.titu.nexus_claims.models.ClaimFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClaimFileRepository extends JpaRepository<ClaimFile, Long> {
    Optional<ClaimFile> findTopByOrderByIdDesc();

    boolean existsByClaimNumber(String claimNumber);

    Optional<ClaimFile> findByIdempotencyKey(String idempotencyKey);
}
