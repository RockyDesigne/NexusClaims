package com.titu.nexus_claims.services;

import com.titu.nexus_claims.dtos.StubIntegrationResponse;

public interface AidaVerificationProvider {
    StubIntegrationResponse checkVehicle(String registrationPlate, String vin);
}
