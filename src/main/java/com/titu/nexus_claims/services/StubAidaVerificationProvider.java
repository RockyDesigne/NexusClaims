package com.titu.nexus_claims.services;

import com.titu.nexus_claims.dtos.StubIntegrationResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StubAidaVerificationProvider implements AidaVerificationProvider {
    @Override
    public StubIntegrationResponse checkVehicle(String registrationPlate, String vin) {
        return new StubIntegrationResponse(
                "STUB",
                "AIDA verification is stubbed. No external vehicle verification has been performed.",
                Map.of(
                        "registrationPlate", registrationPlate == null ? "" : registrationPlate,
                        "vin", vin == null ? "" : vin
                )
        );
    }
}
