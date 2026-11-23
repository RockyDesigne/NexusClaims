package com.titu.nexus_claims.dtos;
public record HandoffClaimTaskRequest(String idempotencyKey, Long expectedRevision, String type, String description, String assignee) {}
