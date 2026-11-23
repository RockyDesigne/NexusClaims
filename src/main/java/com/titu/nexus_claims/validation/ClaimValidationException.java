package com.titu.nexus_claims.validation;

import java.util.List;

public class ClaimValidationException extends RuntimeException {
    private final List<ClaimValidationError> errors;

    public ClaimValidationException(List<ClaimValidationError> errors) {
        super("Claim validation failed.");
        this.errors = errors;
    }

    public List<ClaimValidationError> getErrors() {
        return errors;
    }
}
