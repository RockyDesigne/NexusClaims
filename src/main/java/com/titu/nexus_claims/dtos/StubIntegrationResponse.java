package com.titu.nexus_claims.dtos;

import java.util.Map;

public record StubIntegrationResponse(
        String status,
        String message,
        Map<String, String> data
) {
}
