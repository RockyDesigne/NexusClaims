package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "claim_reserve_ledgers")
@Getter @Setter @NoArgsConstructor
public class ClaimReserveLedger {
    @Id private Long claimId;
    @Version private long revision;
    @Column(nullable = false) private long changeCount;

    public ClaimReserveLedger(Long claimId) { this.claimId = claimId; }
}
