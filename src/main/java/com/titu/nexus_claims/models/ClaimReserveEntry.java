package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claim_reserve_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "request_key"}),
        indexes = @Index(columnList = "claim_id,id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimReserveEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false) private ClaimFile claim;
    @Column(name = "request_key", nullable = false) private String requestKey;
    @Column(nullable = false) private String category;
    @Column(nullable = false) private String reserveType;
    @Column(nullable = false) private String operation;
    @Column(nullable = false) private LocalDate date;
    // SQLite numeric affinity can coerce decimals to floating point. Store canonical decimal strings.
    @Column(nullable = false, columnDefinition = "TEXT") private String amount;
    @Column(nullable = false) private String currency;
    @Column(nullable = false, length = 1000) private String details;
    @Column(nullable = false) private String createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false, columnDefinition = "TEXT") private String reserveValue;
    @Column(nullable = false, columnDefinition = "TEXT") private String totalValue;
}
