package com.titu.nexus_claims.models;

import jakarta.persistence.*;
import lombok.*;

/** Fictional claims staff directory; authentication remains outside this prototype. */
@Entity @Table(name = "claim_staff_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ClaimStaffAccount {
    @Id private String username;
    @Column(nullable = false) private String displayName;
    @Column(nullable = false) private String role;
    @Column(nullable = false) private boolean active;
}
