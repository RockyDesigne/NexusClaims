package com.titu.nexus_claims.services;

import com.titu.nexus_claims.repositories.ClaimFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaimReserveInitializer implements ApplicationRunner {
    private final ClaimFileRepository claims;
    private final ClaimReserveService reserves;
    @Override public void run(ApplicationArguments arguments) {
        claims.findAll().forEach(claim -> reserves.initialize(claim.getId()));
    }
}
