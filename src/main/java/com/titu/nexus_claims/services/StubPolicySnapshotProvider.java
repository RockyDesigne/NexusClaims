package com.titu.nexus_claims.services;

import com.titu.nexus_claims.dtos.StubIntegrationResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StubPolicySnapshotProvider implements PolicySnapshotProvider {
    @Override
    public StubIntegrationResponse lookup(String policySeries, String policyNumber) {
        return new StubIntegrationResponse(
                "STUB",
                "Policy lookup is stubbed until the policy-system API contract is available.",
                Map.of(
                        "issueDate", "2026-01-15",
                        "ownerData", "Fictional owner from local stub",
                        "userData", "Fictional policy user from local stub",
                        "vehicleData", "Fictional MTPL vehicle from local stub",
                        "validFrom", "2026-01-15",
                        "validTo", "2027-01-14",
                        "directSettlementEligible", "true"
                )
        );
    }
}
