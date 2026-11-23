package com.titu.nexus_claims.dtos;

public record CreateReserveEntryRequest(String idempotencyKey, String category, String type,
                                       String amount, String currency, String details, Long expectedRevision) { }
