package com.titu.nexus_claims.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titu.nexus_claims.validation.ClaimFormValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoClaimFixturesTest {
    @Test
    void anExistingClaimsStackIsNeverResetOnStartup() {
        var repository = org.mockito.Mockito.mock(com.titu.nexus_claims.repositories.ClaimFileRepository.class);
        org.mockito.Mockito.when(repository.count()).thenReturn(1L);
        var service = new ClaimFileService(repository,
                org.mockito.Mockito.mock(ClaimNumberGenerator.class), new ClaimFormValidator(),
                new ObjectMapper(), new ClaimAccessPolicy(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR), org.mockito.Mockito.mock(ClaimReserveService.class), org.mockito.Mockito.mock(ClaimTaskService.class));
        service.seedClaimsStack();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).deleteAll();
    }

    @Test
    void includesOneCompleteValidClaimForEachObjectType() {
        var fixtures = DemoClaimFixtures.load(new ObjectMapper());
        var validator = new ClaimFormValidator();
        assertThat(fixtures).hasSize(3);
        assertThat(fixtures).extracting(example -> example.at("/claim/damagedObject/type").asText())
                .containsExactly("Vehicle", "Injury", "Other property");
        for (var example : fixtures) {
            var claim = example.path("claim");
            assertThat(validator.validate(claim)).isEmpty();
            assertThat(claim.path("damagedObject").size()).isEqualTo(2);
            assertThat(claim.at("/damages/lines").size()).isGreaterThanOrEqualTo(1);
            assertThat(example.toString()).doesNotContain("__CLAIM_DATE__", "__ACCIDENT_DATE__", "__VALID_FROM__");
            for (String section : java.util.List.of("general", "policySnapshot", "additionalServices", "event", "claimant", "damagedObject", "damages", "valuation", "liability")) {
                assertThat(claim.path(section).size()).as(section).isGreaterThan(0);
            }
            for (String field : java.util.List.of("initialReserve", "vehicleMarketValue", "wreckedCarValue", "maximumCompensation")) {
                assertThat(claim.at("/valuation/" + field + "/amount").isTextual()).isTrue();
                assertThat(claim.at("/valuation/" + field + "/currency").asText()).isEqualTo("RON");
            }
        }
    }
}
