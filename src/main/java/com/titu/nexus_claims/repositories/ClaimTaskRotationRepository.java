package com.titu.nexus_claims.repositories;
import com.titu.nexus_claims.models.ClaimTaskRotation;
import org.springframework.data.jpa.repository.*;
public interface ClaimTaskRotationRepository extends JpaRepository<ClaimTaskRotation, Long> {
    // Obtain SQLite's write lock before reading balances/tasks/cursor. Shared across server instances.
    @Modifying(flushAutomatically = true)
    @Query("update ClaimTaskRotation r set r.revision = r.revision + 1 where r.id = 1")
    int lockRotation();
}
