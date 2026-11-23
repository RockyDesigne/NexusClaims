package com.titu.nexus_claims.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.repositories.*;
import com.titu.nexus_claims.validation.ClaimValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:", "spring.datasource.hikari.maximum-pool-size=1"})
@Transactional
class ClaimReserveServiceTest {
    @Autowired private ClaimReserveService service;
    @Autowired private ClaimFileService files;
    @Autowired private ClaimFileRepository claims;
    @Autowired private ClaimReserveLedgerRepository ledgers;
    @Autowired private ClaimReserveEntryRepository entries;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private CreateReserveEntryRequest change(String category, String type, String amount, String currency, long revision) {
        return new CreateReserveEntryRequest(UUID.randomUUID().toString(), category, type, amount, currency, "Fictional test adjustment", revision);
    }

    @Test
    void initializationPreservesTheIntakeReserveAndRunsOnce() {
        var original = service.getReserves(1L);
        service.initialize(1L);
        var repeated = service.getReserves(1L);
        assertThat(original.entries()).hasSize(1);
        assertThat(repeated.entries()).isEqualTo(original.entries());
        assertThat(original.entries().getFirst().operation()).isEqualTo("INITIAL");
        assertThat(original.entries().getFirst().amount()).isEqualTo("4500.00");
        assertThat(original.entries().getFirst().user()).isEqualTo("Fictional Insurance Director");
        assertThat(service.getReserves(2L).entries().getFirst().type()).isEqualTo("INJURY");
    }

    @Test
    void usesExactDecimalArithmeticAndTextStorage() {
        var first = service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "0.10", "RON", 0));
        var second = service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "0.20", "RON", first.revision()));
        assertThat(second.totals().getFirst().amount()).isEqualTo("4500.30");
        assertThat(second.entries().getLast().reserveValue()).isEqualTo("4500.30");
        assertThat(jdbc.queryForObject("select typeof(amount) from claim_reserve_entries order by id desc limit 1", String.class)).isEqualTo("text");
        assertThat(files.getClaim(1L).claim().at("/valuation/initialReserve/amount").asText()).isEqualTo("4500.00");
    }

    @Test
    void decreaseKeepsPriorEntriesAndRecordsResultingBalance() {
        var result = service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "-500.25", "RON", 0));
        assertThat(result.entries()).hasSize(2);
        assertThat(result.entries().getFirst().amount()).isEqualTo("4500.00");
        assertThat(result.entries().getLast().operation()).isEqualTo("DECREASE");
        assertThat(result.entries().getLast().reserveValue()).isEqualTo("3999.75");
        assertThat(result.totals().getFirst().amount()).isEqualTo("3999.75");
    }

    @Test
    void reserveTypesCurrenciesAndRecoveryRemainSeparate() {
        var experts = service.addEntry(1L, change("RESERVE", "TECHNICAL_OR_MEDICAL_EXPERTS", "120.00", "RON", 0));
        var euros = service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "50.10", "EUR", experts.revision()));
        var recovery = service.addEntry(1L, change("RECOVERY_RESERVE", "MATERIAL_DAMAGE", "150.00", "RON", euros.revision()));
        assertThat(recovery.totals()).containsExactly(
                new ClaimReservesDTO.Total("RESERVE", "4620.00", "RON"),
                new ClaimReservesDTO.Total("RESERVE", "50.10", "EUR"),
                new ClaimReservesDTO.Total("RECOVERY_RESERVE", "150.00", "RON"));
        assertThat(service.getReserves(2L).entries()).hasSize(1);
    }

    @Test
    void cannotDecreaseAnUnfundedTypeOrMakeBalanceNegative() {
        assertThatThrownBy(() -> service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "-4500.01", "RON", 0))).isInstanceOf(ClaimValidationException.class);
        assertThatThrownBy(() -> service.addEntry(1L, change("RESERVE", "TECHNICAL_OR_MEDICAL_EXPERTS", "-1.00", "RON", 0))).isInstanceOf(ClaimValidationException.class);
        assertThatThrownBy(() -> service.addEntry(1L, change("RECOVERY_RESERVE", "MATERIAL_DAMAGE", "-1.00", "RON", 0))).isInstanceOf(ClaimValidationException.class);
        assertThat(service.getReserves(1L).entries()).hasSize(1);
    }

    @Test
    void rejectsZeroMalformedOverprecisionAndUnsupportedCurrency() {
        for (String value : new String[]{"0", "-0.00", "1.001", "NaN", "1e2", "1000000000000.00", ""}) {
            assertThatThrownBy(() -> service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", value, "RON", 0))).isInstanceOf(ClaimValidationException.class);
        }
        assertThatThrownBy(() -> service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "1.00", "XYZ", 0))).isInstanceOf(ClaimValidationException.class);
        assertThat(service.getReserves(1L).entries()).hasSize(1);
    }

    @Test
    void identicalRetryIsIdempotentAndChangedRequestKeyReuseIsRejected() {
        var request = change("RESERVE", "MATERIAL_DAMAGE", "125.00", "RON", 0);
        var first = service.addEntry(1L, request);
        var second = service.addEntry(1L, request);
        assertThat(second.revision()).isEqualTo(first.revision());
        assertThat(second.entries()).hasSize(2);
        var different = new CreateReserveEntryRequest(request.idempotencyKey(), request.category(), request.type(), "126.00", request.currency(), request.details(), first.revision());
        assertThatThrownBy(() -> service.addEntry(1L, different)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void staleRevisionCannotOverwriteNewHistory() {
        service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "100.00", "RON", 0));
        assertThatThrownBy(() -> service.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "200.00", "RON", 0)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        assertThat(service.getReserves(1L).totals().getFirst().amount()).isEqualTo("4600.00");
    }

    @Test
    void viewerCanReadButCannotAppendChanges() {
        var viewer = new ClaimReserveService(claims, ledgers, entries, new ClaimAccessPolicy(ClaimAccessPolicy.Role.VIEWER), mapper);
        assertThat(viewer.getReserves(1L).canEdit()).isFalse();
        assertThatThrownBy(() -> viewer.addEntry(1L, change("RESERVE", "MATERIAL_DAMAGE", "100.00", "RON", 0)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void initialReserveCannotBeRewrittenFromTheIntakeEditor() {
        var detail = files.getClaim(1L);
        var payload = detail.claim().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.at("/valuation/initialReserve")).put("amount", "9999.00");
        assertThatThrownBy(() -> files.updateClaim(1L, new UpdateClaimFileRequest(payload))).isInstanceOf(ClaimValidationException.class);
        assertThat(service.getReserves(1L).entries()).hasSize(1);
    }

    @Test
    void invalidInitialPrecisionIsRejectedBeforeCreatingAClaimOrLedger() {
        var payload = DemoClaimFixtures.load(mapper).getFirst().path("claim").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.at("/valuation/initialReserve")).put("amount", "1.001");
        long count = claims.count();
        assertThatThrownBy(() -> files.createClaim(new CreateClaimFileRequest("invalid-initial-test", payload)))
                .isInstanceOf(ClaimValidationException.class);
        assertThat(claims.count()).isEqualTo(count);
        assertThat(ledgers.count()).isEqualTo(count);
    }

    @Test
    void missingClaimDoesNotCreateALedger() {
        assertThatThrownBy(() -> service.getReserves(999999L)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        assertThat(ledgers.existsById(999999L)).isFalse();
    }
}
