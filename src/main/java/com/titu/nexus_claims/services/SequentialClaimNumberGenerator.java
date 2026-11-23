package com.titu.nexus_claims.services;

import com.titu.nexus_claims.repositories.ClaimFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
@RequiredArgsConstructor
public class SequentialClaimNumberGenerator implements ClaimNumberGenerator {
    private final ClaimFileRepository claimFileRepository;

    @Override
    public String nextClaimNumber() {
        long nextId = claimFileRepository.findTopByOrderByIdDesc()
                .map(claim -> claim.getId() + 1)
                .orElse(1L);
        String candidate = format(nextId);

        while (claimFileRepository.existsByClaimNumber(candidate)) {
            nextId++;
            candidate = format(nextId);
        }

        return candidate;
    }

    private String format(long sequence) {
        return "CLM-" + Year.now().getValue() + "-" + String.format("%06d", sequence);
    }
}
