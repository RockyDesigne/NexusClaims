package com.titu.nexus_claims.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.models.*;
import com.titu.nexus_claims.repositories.*;
import com.titu.nexus_claims.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClaimReserveService {
    public static final Set<String> CATEGORIES = Set.of("RESERVE", "RECOVERY_RESERVE");
    public static final Set<String> TYPES = Set.of("MATERIAL_DAMAGE", "INJURY", "TECHNICAL_OR_MEDICAL_EXPERTS");
    public static final Set<String> CURRENCIES = Set.of("RON", "EUR", "USD");
    private static final BigDecimal MAXIMUM_BALANCE = new BigDecimal("999999999999.99");
    private static final String ACTOR = "Fictional Insurance Director";
    private final ClaimFileRepository claims;
    private final ClaimReserveLedgerRepository ledgers;
    private final ClaimReserveEntryRepository entries;
    private final ClaimAccessPolicy access;
    private final ObjectMapper mapper;

    @Transactional
    public void initialize(long claimId) { initialize(findClaim(claimId)); }

    private ClaimReserveLedger initialize(ClaimFile claim) {
        var existing = ledgers.findById(claim.getId());
        if (existing.isPresent()) return existing.get();
        var ledger = ledgers.saveAndFlush(new ClaimReserveLedger(claim.getId()));
        try {
            var payload = mapper.readTree(claim.getClaimPayload() == null ? "{}" : claim.getClaimPayload());
            if (payload == null) return ledger;
            var initial = payload.at("/valuation/initialReserve");
            String raw = initial.path("amount").asText("");
            String currency = initial.path("currency").asText("");
            if (!raw.isBlank() && CURRENCIES.contains(currency)) {
                BigDecimal amount = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
                if (amount.signum() < 0 || amount.compareTo(MAXIMUM_BALANCE) > 0) throw new IllegalArgumentException("Invalid initial reserve.");
                String type = "Injury".equals(payload.at("/damagedObject/type").asText()) ? "INJURY" : "MATERIAL_DAMAGE";
                entries.save(ClaimReserveEntry.builder().claim(claim).requestKey("initial-reserve")
                        .category("RESERVE").reserveType(type).operation("INITIAL")
                        .date(claim.getClaimDate()).amount(amount.toPlainString()).currency(currency)
                        .details("Initial reserve recorded at claim intake.").createdBy(claim.getCreatedBy())
                        .createdAt(claim.getCreatedAt()).reserveValue(amount.toPlainString()).totalValue(amount.toPlainString()).build());
            }
        } catch (JsonProcessingException | IllegalArgumentException | ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to initialize the claim's reserve history.", exception);
        }
        return ledger;
    }

    @Transactional
    public ClaimReservesDTO getReserves(long claimId) {
        ClaimFile claim = findClaim(claimId);
        return summary(claim, initialize(claim));
    }

    @Transactional
    public ClaimReservesDTO addEntry(long claimId, CreateReserveEntryRequest request) {
        if (!access.canCurrentActorEdit()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to change reserves.");
        ClaimFile claim = findClaim(claimId);
        ClaimReserveLedger ledger = initialize(claim);
        List<ClaimValidationError> errors = validate(request);
        if (!errors.isEmpty()) throw new ClaimValidationException(errors);
        BigDecimal amount = new BigDecimal(request.amount().trim()).setScale(2, RoundingMode.UNNECESSARY);
        var duplicate = entries.findByClaimIdAndRequestKey(claimId, request.idempotencyKey().trim());
        if (duplicate.isPresent()) {
            var saved = duplicate.get();
            if (!saved.getCategory().equals(request.category()) || !saved.getReserveType().equals(request.type())
                    || !saved.getCurrency().equals(request.currency()) || new BigDecimal(saved.getAmount()).compareTo(amount) != 0
                    || !saved.getDetails().equals(request.details().trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This request key has already been used for a different reserve change.");
            }
            return summary(claim, ledger);
        }
        if (request.expectedRevision() != ledger.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserve history changed. Reload the latest balances before saving again.");
        }
        var history = entries.findByClaimIdOrderByIdAsc(claimId);
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : history) {
            if (entry.getCategory().equals(request.category()) && entry.getCurrency().equals(request.currency())) {
                BigDecimal delta = new BigDecimal(entry.getAmount());
                total = total.add(delta);
                if (entry.getReserveType().equals(request.type())) balance = balance.add(delta);
            }
        }
        balance = balance.add(amount);
        total = total.add(amount);
        if (balance.signum() < 0) throw new ClaimValidationException(List.of(new ClaimValidationError("amount", "The change would make this reserve type's balance negative.")));
        if (total.compareTo(MAXIMUM_BALANCE) > 0) throw new ClaimValidationException(List.of(new ClaimValidationError("amount", "The resulting reserve exceeds the supported amount.")));
        // Flush a versioned ledger update before inserting the immutable entry. Stale concurrent writes fail as one transaction.
        ledger.setChangeCount(ledger.getChangeCount() + 1);
        ledgers.saveAndFlush(ledger);
        entries.saveAndFlush(ClaimReserveEntry.builder().claim(claim).requestKey(request.idempotencyKey().trim())
                .category(request.category()).reserveType(request.type()).operation(amount.signum() > 0 ? "INCREASE" : "DECREASE")
                .date(LocalDate.now(ClaimFormValidator.BUSINESS_ZONE)).amount(amount.toPlainString()).currency(request.currency())
                .details(request.details().trim()).createdBy(ACTOR).createdAt(Instant.now())
                .reserveValue(balance.setScale(2).toPlainString()).totalValue(total.setScale(2).toPlainString()).build());
        return summary(claim, ledger);
    }

    private List<ClaimValidationError> validate(CreateReserveEntryRequest request) {
        List<ClaimValidationError> errors = new ArrayList<>();
        if (request == null) return List.of(new ClaimValidationError("amount", "Reserve change is required."));
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 100)
            errors.add(new ClaimValidationError("idempotencyKey", "A valid request key is required."));
        if (!CATEGORIES.contains(request.category() == null ? "" : request.category())) errors.add(new ClaimValidationError("category", "Choose a reserve category."));
        if (!TYPES.contains(request.type() == null ? "" : request.type())) errors.add(new ClaimValidationError("type", "Choose a reserve type."));
        if (!CURRENCIES.contains(request.currency() == null ? "" : request.currency())) errors.add(new ClaimValidationError("currency", "Choose RON, EUR, or USD."));
        String amount = request.amount() == null ? "" : request.amount().trim();
        if (!amount.matches("-?(0|[1-9]\\d{0,11})(\\.\\d{1,2})?") || new BigDecimal(amount).signum() == 0)
            errors.add(new ClaimValidationError("amount", "Enter a non-zero decimal change with up to two decimal places (maximum 12 digits before the decimal)."));
        if (request.details() == null || request.details().isBlank() || request.details().trim().length() > 1000)
            errors.add(new ClaimValidationError("details", "Enter a reason for the change (up to 1,000 characters)."));
        if (request.expectedRevision() == null || request.expectedRevision() < 0) errors.add(new ClaimValidationError("expectedRevision", "Reload reserve history before saving."));
        return errors;
    }

    private ClaimReservesDTO summary(ClaimFile claim, ClaimReserveLedger ledger) {
        var history = entries.findByClaimIdOrderByIdAsc(claim.getId());
        Map<List<String>, BigDecimal> balances = new LinkedHashMap<>();
        Map<List<String>, BigDecimal> totals = new LinkedHashMap<>();
        for (var entry : history) {
            BigDecimal amount = new BigDecimal(entry.getAmount());
            balances.merge(List.of(entry.getCategory(), entry.getReserveType(), entry.getCurrency()), amount, BigDecimal::add);
            totals.merge(List.of(entry.getCategory(), entry.getCurrency()), amount, BigDecimal::add);
        }
        return new ClaimReservesDTO(claim.getId(), claim.getClaimNumber(), access.canCurrentActorEdit(), ledger.getRevision(),
                history.stream().map(entry -> new ClaimReservesDTO.Entry(entry.getId(), entry.getCategory(), entry.getReserveType(), entry.getOperation(), entry.getDate(), entry.getAmount(), entry.getCurrency(), entry.getDetails(), entry.getCreatedBy(), entry.getCreatedAt(), entry.getReserveValue(), entry.getTotalValue())).toList(),
                balances.entrySet().stream().map(entry -> new ClaimReservesDTO.Balance(entry.getKey().get(0), entry.getKey().get(1), entry.getValue().setScale(2).toPlainString(), entry.getKey().get(2))).toList(),
                totals.entrySet().stream().map(entry -> new ClaimReservesDTO.Total(entry.getKey().get(0), entry.getValue().setScale(2).toPlainString(), entry.getKey().get(1))).toList());
    }

    private ClaimFile findClaim(long id) {
        return claims.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim file was not found."));
    }
}
