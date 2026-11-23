package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "claim_document_previews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ClaimDocumentPreview {
    @Id private Long documentId;
    @Column(nullable = false) private String sourceHash;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "BLOB") private byte[] png;
}
