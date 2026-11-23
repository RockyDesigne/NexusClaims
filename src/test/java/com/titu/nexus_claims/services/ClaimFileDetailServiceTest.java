package com.titu.nexus_claims.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.titu.nexus_claims.dtos.UpdateClaimFileRequest;
import com.titu.nexus_claims.models.ClaimFile;
import com.titu.nexus_claims.repositories.ClaimFileRepository;
import com.titu.nexus_claims.validation.ClaimFormValidator;
import com.titu.nexus_claims.validation.ClaimValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClaimFileDetailServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaimFileRepository repository = mock(ClaimFileRepository.class);
    private final ClaimNumberGenerator generator = mock(ClaimNumberGenerator.class);

    private ClaimFileService service(ClaimAccessPolicy.Role role) {
        return new ClaimFileService(repository, generator, new ClaimFormValidator(), mapper, new ClaimAccessPolicy(role), mock(ClaimReserveService.class), mock(ClaimTaskService.class));
    }

    @Test
    void loadsSavedPayloadAndStripsInactiveObjectBranches() throws Exception {
        ClaimFile file = file();
        ObjectNode payload = payload();
        payload.withObject("damagedObject").putObject("vehicle").put("vin", "HIDDEN-STALE");
        file.setClaimPayload(mapper.writeValueAsString(payload));
        when(repository.findById(7L)).thenReturn(Optional.of(file));

        var detail = service(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR).getClaim(7L);

        assertThat(detail.summary().claimNumber()).isEqualTo("CLM-TEST-7");
        assertThat(detail.claim().at("/damagedObject/injury/injuredPersonName").asText()).isEqualTo("Synthetic injured person");
        assertThat(detail.claim().at("/damagedObject/vehicle").isMissingNode()).isTrue();
        assertThat(detail.canEdit()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void summaryOnlySeedHasNoFabricatedIntakeData() {
        ClaimFile file = file();
        file.setClaimPayload("{}");
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        assertThat(service(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR).getClaim(7L).claim()).isNull();
    }

    @Test
    void missingFileReturns404() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR).getClaim(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void corruptStoredPayloadReturnsLoadError() {
        ClaimFile file = file();
        file.setClaimPayload("broken JSON");
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        assertThatThrownBy(() -> service(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR).getClaim(7L))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(500));
    }

    @Test
    void directorUpdatesExistingFileWithoutChangingIdentityStatusOrCreationMetadata() throws Exception {
        ClaimFile file = file();
        Instant created = file.getCreatedAt();
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectNode payload = payload();
        payload.put("claimNumber", "FORGED").put("status", "Closed").put("createdBy", "FORGED");
        payload.withObject("damagedObject").putObject("property").put("description", "HIDDEN-STALE");

        var result = service(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR).updateClaim(7L, new UpdateClaimFileRequest(payload));

        assertThat(result.summary().id()).isEqualTo(7L);
        assertThat(result.summary().claimNumber()).isEqualTo("CLM-TEST-7");
        assertThat(result.summary().status()).isEqualTo("Assessment");
        assertThat(result.summary().createdBy()).isEqualTo("Fictional Insurance Director");
        assertThat(result.summary().createdAt()).isEqualTo(created);
        assertThat(file.getIdempotencyKey()).isEqualTo("original-key");
        assertThat(result.summary().notificationNumber()).isEqualTo("UPDATED-1");
        assertThat(result.claim().at("/valuation/initialReserve/amount").asText()).isEqualTo("1234.50");
        assertThat(result.claim().at("/damagedObject/property").isMissingNode()).isTrue();
        verify(repository, times(1)).save(file);
        verifyNoInteractions(generator);
    }

    @Test
    void invalidUpdateDoesNotMutateOrSaveExistingFile() throws Exception {
        ClaimFile file = file();
        when(repository.findById(7L)).thenReturn(Optional.of(file));
        ObjectNode payload = payload();
        payload.withObject("damagedObject").withObject("injury").put("injuredPersonName", "");
        assertThatThrownBy(() -> service(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR).updateClaim(7L, new UpdateClaimFileRequest(payload)))
                .isInstanceOf(ClaimValidationException.class);
        assertThat(file.getNotificationNumber()).isEqualTo("ORIGINAL");
        assertThat(file.getClaimPayload()).isEqualTo("{}");
        verify(repository, never()).save(any());
    }

    @Test
    void viewerCanReadButCannotUpdateEvenWithAClientSuppliedDirectorRole() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(file()));
        ClaimFileService viewerService = service(ClaimAccessPolicy.Role.VIEWER);
        assertThat(viewerService.getClaim(7L).canEdit()).isFalse();
        ObjectNode forged = payload().put("role", "INSURANCE_DIRECTOR");
        assertThatThrownBy(() -> viewerService.updateClaim(7L, new UpdateClaimFileRequest(forged)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
        verify(repository, never()).save(any());
        verifyNoInteractions(generator);
    }

    private ClaimFile file() {
        return ClaimFile.builder().id(7L).claimNumber("CLM-TEST-7").idempotencyKey("original-key")
                .claimType("MTPL").status("Assessment").claimDate(LocalDate.parse("2026-01-10"))
                .announcementDate(LocalDate.parse("2026-01-10")).policyType("MTPL")
                .notificationNumber("ORIGINAL").selectedCharacteristics("Injury")
                .createdBy("Fictional Insurance Director").createdAt(Instant.parse("2026-01-10T10:00:00Z")).claimPayload("{}").build();
    }

    private ObjectNode payload() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {
                  "context":{"companyNetwork":"Nexus Mutual","product":"MTPL","characteristics":["Injury"]},
                  "general":{"claimDate":"2026-01-10","policyType":"MTPL","policySeries":"SYN","policyNumber":"100","notificationNumber":"UPDATED-1"},
                  "event":{"announcementDate":"2026-01-10","accidentDate":"2026-01-10","accidentCountry":"Romania","accidentCounty":"Bucharest","accidentCity":"Bucharest","numberOfCarsInvolved":"2"},
                  "claimant":{"type":"FOREIGN_NATURAL_PERSON","identifier":"SYN-1","firstName":"Synthetic","lastName":"Person","address":"Synthetic address"},
                  "damagedObject":{"type":"Injury","injury":{"injuredPersonName":"Synthetic injured person","description":"Reported bruising"}},
                  "damages":{"lines":[]},
                  "valuation":{"initialReserve":{"amount":"1234.50","currency":"EUR"}}
                }
                """);
    }
}
