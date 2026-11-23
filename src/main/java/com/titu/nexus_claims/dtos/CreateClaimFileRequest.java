package com.titu.nexus_claims.dtos;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateClaimFileRequest(
        String idempotencyKey,
        JsonNode claim
) {
}
