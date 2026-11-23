package com.titu.nexus_claims.repositories;

import com.titu.nexus_claims.dtos.ClaimDocumentDTO;
import com.titu.nexus_claims.models.ClaimDocument;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    interface PreviewSource {
        Long getId();
        String getName();
        String getSha256();
        String getCategory();
    }
    Optional<PreviewSource> findPreviewSourceByIdAndClaimId(long id, long claimId);
    // List metadata only: attachment contents are fetched only for preview/download.
    @Query("select new com.titu.nexus_claims.dtos.ClaimDocumentDTO(d.id,d.name,d.contentType,d.category,d.size,d.tag,d.uploadedBy,d.uploadedAt) from ClaimDocument d where d.claim.id=:claimId order by d.uploadedAt desc,d.id desc")
    List<ClaimDocumentDTO> listMetadata(@Param("claimId") long claimId);
    Optional<ClaimDocument> findByClaimIdAndUploadKey(long claimId, String uploadKey);
    Optional<ClaimDocument> findByIdAndClaimId(long id, long claimId);
}
