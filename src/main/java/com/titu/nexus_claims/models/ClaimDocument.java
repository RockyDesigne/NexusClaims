package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "claim_documents", uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "upload_key"}),
        indexes = @Index(columnList = "claim_id,uploaded_at,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false) private ClaimFile claim;
    @Column(name = "upload_key", nullable = false) private String uploadKey;
    @Column(nullable = false, length = 180) private String name;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private String category;
    @Column(nullable = false) private long size;
    @Column(nullable = false) private String tag;
    @Column(nullable = false) private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    @Column(nullable = false) private String sha256;
    // Use JDBC byte-array access; SQLite JDBC does not implement ResultSet.getBlob().
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "BLOB") private byte[] content;
}
