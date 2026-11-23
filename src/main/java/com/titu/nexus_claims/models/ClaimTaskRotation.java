package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "claim_task_rotation")
@Getter @Setter @NoArgsConstructor
public class ClaimTaskRotation {
    @Id private Long id;
    private String lastAssignee;
    @Column(nullable = false) private long revision;
    public ClaimTaskRotation(Long id) { this.id = id; }
}
