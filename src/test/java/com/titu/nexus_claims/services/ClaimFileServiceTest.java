package com.titu.nexus_claims.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titu.nexus_claims.dtos.CreateClaimFileRequest;
import com.titu.nexus_claims.models.ClaimFile;
import com.titu.nexus_claims.repositories.ClaimFileRepository;
import com.titu.nexus_claims.validation.ClaimFormValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimFileServiceTest {
    @Test
    void savesOnlySelectedObjectBranchEvenWhenClientSendsStaleHiddenData() throws Exception {
        for (String type : List.of("Vehicle", "Injury", "Other property")) {
            ObjectMapper mapper = new ObjectMapper();
            ClaimFileRepository repository = mock(ClaimFileRepository.class);
            ClaimNumberGenerator generator = mock(ClaimNumberGenerator.class);
            ClaimFormValidator validator = mock(ClaimFormValidator.class);
            ClaimFileService service = new ClaimFileService(repository, generator, validator, mapper, new ClaimAccessPolicy(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR), org.mockito.Mockito.mock(ClaimReserveService.class), org.mockito.Mockito.mock(ClaimTaskService.class));
            var payload = mapper.readTree("""
                    {"context":{"characteristics":["Injury"]}, "damagedObject":{
                    "type":"Vehicle", "vehicle":{"registrationPlate":"SYNTHETIC"},
                    "injury":{"injuredPersonName":"Synthetic person","description":"Reported injury"},
                    "property":{"ownerName":"Synthetic owner","description":"Damaged fence"}}}
                    """);
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("damagedObject")).put("type", type);
            when(repository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
            when(validator.validate(any())).thenReturn(List.of());
            when(generator.nextClaimNumber()).thenReturn("CLM-TEST-0001");
            when(repository.save(any())).thenAnswer(invocation -> {
                ClaimFile saved = invocation.getArgument(0);
                saved.setId(7L);
                return saved;
            });

            var result = service.createClaim(new CreateClaimFileRequest("new-key", payload));

            ArgumentCaptor<ClaimFile> saved = ArgumentCaptor.forClass(ClaimFile.class);
            verify(repository).save(saved.capture());
            var object = mapper.readTree(saved.getValue().getClaimPayload()).path("damagedObject");
            String branch = "Vehicle".equals(type) ? "vehicle" : "Injury".equals(type) ? "injury" : "property";
            assertThat(object.size()).isEqualTo(2);
            assertThat(object.has(branch)).isTrue();
            assertThat(result.status()).isEqualTo("Open");
            assertThat(payload.path("damagedObject").size()).isEqualTo(4);
            if (!"Vehicle".equals(type)) assertThat(result.insuredVehicleRegistration()).isEmpty();
        }
    }

    @Test
    void duplicateIdempotencyKeyReturnsExistingClaimWithoutSavingAgain() {
        ClaimFileRepository repository = mock(ClaimFileRepository.class);
        ClaimNumberGenerator numberGenerator = mock(ClaimNumberGenerator.class);
        ClaimFormValidator validator = mock(ClaimFormValidator.class);
        ClaimFileService service = new ClaimFileService(repository, numberGenerator, validator, new ObjectMapper(), new ClaimAccessPolicy(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR), org.mockito.Mockito.mock(ClaimReserveService.class), org.mockito.Mockito.mock(ClaimTaskService.class));
        ClaimFile existingClaim = ClaimFile.builder()
                .id(1L)
                .claimNumber("CLM-2026-000010")
                .idempotencyKey("same-key")
                .claimType("MTPL")
                .status("Open")
                .claimDate(LocalDate.parse("2026-07-19"))
                .announcementDate(LocalDate.parse("2026-07-19"))
                .policyType("MTPL")
                .policySeries("NX")
                .policyNumber("100")
                .insuredName("Synthetic insured")
                .notificationNumber("NOT-100")
                .externalClaimNumber("")
                .externalNotificationNumber("")
                .claimantName("Synthetic claimant")
                .insuredVehicleRegistration("B-100-NCX")
                .claimantVehicleRegistration("")
                .insuranceCompany("Nexus Mutual")
                .createdBy("Fictional Insurance Director")
                .selectedCharacteristics("Material damages")
                .createdAt(Instant.parse("2026-07-19T10:00:00Z"))
                .claimPayload("{}")
                .build();

        when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existingClaim));

        var response = service.createClaim(new CreateClaimFileRequest("same-key", new ObjectMapper().createObjectNode()));

        assertThat(response.claimNumber()).isEqualTo("CLM-2026-000010");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
