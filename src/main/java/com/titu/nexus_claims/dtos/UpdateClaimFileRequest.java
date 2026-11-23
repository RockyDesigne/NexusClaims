package com.titu.nexus_claims.dtos;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateClaimFileRequest(JsonNode claim) {
}
