package com.titu.nexus_claims.dtos;
public record CreateClaimTaskRequest(String idempotencyKey, String title, String type, String description,
        String dueDate, String assignee, Long currentTaskId, Long expectedRevision) {
    public CreateClaimTaskRequest(String idempotencyKey, String title, String type, String description, String dueDate, String assignee) {
        this(idempotencyKey, title, type, description, dueDate, assignee, null, null);
    }
}
