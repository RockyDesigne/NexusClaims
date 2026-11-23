package com.titu.nexus_claims.dtos;
public record SettleClaimTaskRequest(Long expectedRevision, boolean finalPaymentConfirmed) {}
