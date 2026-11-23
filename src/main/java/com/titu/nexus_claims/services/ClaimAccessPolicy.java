package com.titu.nexus_claims.services;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Phase 1 actor stub. Replace with the authenticated actor when authentication is introduced. */
@Component
public class ClaimAccessPolicy {
    public enum Role { INSURANCE_DIRECTOR, CLAIMS_INSPECTOR, LOSS_ADJUSTER, VIEWER }

    private final Role currentRole;
    private final String currentUsername;

    public ClaimAccessPolicy(Role role) { this(role, ""); }

    @org.springframework.beans.factory.annotation.Autowired
    public ClaimAccessPolicy(@Value("${claims.actor-role:INSURANCE_DIRECTOR}") Role currentRole,
            @Value("${claims.actor-username:}") String username) {
        this.currentRole = currentRole;
        this.currentUsername = username == null || username.isBlank()
                ? (currentRole == Role.LOSS_ADJUSTER ? "demo-adjuster-alex" : "fictional-" + currentRole.name().toLowerCase(java.util.Locale.ROOT))
                : username.trim();
    }

    public String currentActorUsername() { return currentUsername; }

    public boolean canCurrentActorWorkTasks() {
        return canCurrentActorManageTasks() || currentRole == Role.LOSS_ADJUSTER;
    }

    public boolean canCurrentActorManageTasks() {
        return currentRole == Role.INSURANCE_DIRECTOR || currentRole == Role.CLAIMS_INSPECTOR;
    }

    public String currentActorName() {
        return switch (currentRole) {
            case INSURANCE_DIRECTOR -> "Fictional Insurance Director";
            case CLAIMS_INSPECTOR -> "Fictional Claims Inspector";
            case LOSS_ADJUSTER -> "Fictional Loss Adjuster";
            case VIEWER -> "Fictional Viewer";
        };
    }

    public boolean canEdit(Role role) {
        return role == Role.INSURANCE_DIRECTOR;
    }

    public boolean canCurrentActorEdit() {
        return canEdit(currentRole);
    }
}
