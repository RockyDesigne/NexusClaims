package com.titu.nexus_claims.services;

import com.titu.nexus_claims.dtos.StubIntegrationResponse;

public interface PolicySnapshotProvider {
    StubIntegrationResponse lookup(String policySeries, String policyNumber);
}
