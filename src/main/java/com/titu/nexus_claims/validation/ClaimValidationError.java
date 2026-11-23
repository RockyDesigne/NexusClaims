package com.titu.nexus_claims.validation;

public record ClaimValidationError(
        String path,
        String message
) {
}
