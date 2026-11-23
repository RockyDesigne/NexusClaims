package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "claim_task_activities")
@Getter @Setter @NoArgsConstructor
public class ClaimTaskActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "task_id", nullable = false) private ClaimTask task;
    @Column(nullable = false) private String details;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private Instant createdAt;
}
