package com.titu.nexus_claims.dtos;
public record UpdateClaimTaskRequest(Long expectedRevision, String assignee, String status, String description) {
    public UpdateClaimTaskRequest(Long expectedRevision, String assignee, String status) {
        this(expectedRevision, assignee, status, null);
    }
}
