package com.titu.nexus_claims.dtos;

import com.fasterxml.jackson.databind.JsonNode;

public record ClaimFileDetailDTO(ClaimFileDTO summary, JsonNode claim, boolean canEdit) {
}
