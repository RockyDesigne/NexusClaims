package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "claim_tasks", uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "request_key"}))
@Getter @Setter @NoArgsConstructor
public class ClaimTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "claim_id", nullable = false) private ClaimFile claim;
    @Column(name = "request_key", nullable = false) private String requestKey;
    @Column(nullable = false) private String requestedAssignee;
    private String createdByUsername;
    private Long predecessorTaskId;
    private String closureReason;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String type;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String initialDescription;
    @Column(nullable = false) private String status;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "assignee", nullable = false) private ClaimStaffAccount assignee;
    private LocalDate dueDate;
    // Nullable columns allow upgrading existing SQLite rows without invented timestamps.
    private String assignedBy;
    private Instant assignedAt;
    private Instant closedAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private String createdBy;
    @Column(nullable = false) private boolean automaticAssignment;
    @Column(nullable = false) private long revision;
}
