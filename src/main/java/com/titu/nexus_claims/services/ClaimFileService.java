package com.titu.nexus_claims.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.titu.nexus_claims.dtos.ClaimFileDTO;
import com.titu.nexus_claims.dtos.ClaimFileDetailDTO;
import com.titu.nexus_claims.dtos.UpdateClaimFileRequest;
import com.titu.nexus_claims.dtos.ClaimStackSummaryDTO;
import com.titu.nexus_claims.dtos.CreateClaimFileRequest;
import com.titu.nexus_claims.models.ClaimFile;
import com.titu.nexus_claims.repositories.ClaimFileRepository;
import com.titu.nexus_claims.validation.ClaimFormValidator;
import com.titu.nexus_claims.validation.ClaimValidationError;
import com.titu.nexus_claims.validation.ClaimValidationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaimFileService {
    private static final String SEEDED_ACTOR = "Fictional Insurance Director";
    private static final String OPEN_STATUS = "Open";

    private final ClaimFileRepository claimFileRepository;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final ClaimFormValidator claimFormValidator;
    private final ObjectMapper objectMapper;
    private final ClaimAccessPolicy claimAccessPolicy;
    private final ClaimReserveService claimReserves;
    private final ClaimTaskService claimTasks;

    @PostConstruct
    @Transactional
    public void seedClaimsStack() {
        if (claimFileRepository.count() > 0) {
            return;
        }

        List<JsonNode> examples = DemoClaimFixtures.load(objectMapper);
        for (JsonNode example : examples) {
            List<ClaimValidationError> errors = claimFormValidator.validate(example.path("claim"));
            if (!errors.isEmpty()) throw new IllegalStateException("Invalid fictional demo claim: " + errors);
        }
        for (JsonNode example : examples) {
            ClaimFile file = newClaim(example.path("claim"), example.path("idempotencyKey").asText());
            file.setInsuredVehicleRegistration(example.path("insuredVehicleRegistration").asText());
            file.setClaimantVehicleRegistration(example.path("claimantVehicleRegistration").asText());
            ClaimFile saved = claimFileRepository.save(file);
            claimReserves.initialize(saved.getId());
        claimTasks.ensureOpenTask(saved.getId());
        }
    }

    @Transactional(readOnly = true)
    public ClaimStackSummaryDTO getClaims(String query, String status) {
        List<ClaimFileDTO> claims = claimFileRepository.findAll()
                .stream()
                .filter(claim -> matchesStatus(claim, status))
                .filter(claim -> matchesQuery(claim, query))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toDto)
                .toList();

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        claimFileRepository.findAll().forEach(claim ->
                statusCounts.merge(claim.getStatus(), 1L, Long::sum)
        );

        return new ClaimStackSummaryDTO(claims, statusCounts);
    }

    @Transactional(readOnly = true)
    public ClaimFileDetailDTO getClaim(long id) {
        return detail(findClaim(id));
    }

    @Transactional
    public ClaimFileDetailDTO updateClaim(long id, UpdateClaimFileRequest request) {
        if (!claimAccessPolicy.canCurrentActorEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit claims.");
        }
        ClaimFile existing = findClaim(id);
        if (request == null || request.claim() == null || !request.claim().isObject()) {
            throw new ClaimValidationException(List.of(new ClaimValidationError("claim", "Claim payload is required.")));
        }
        JsonNode claim = sanitizeDamagedObject(request.claim());
        List<ClaimValidationError> errors = claimFormValidator.validate(claim);
        if (!errors.isEmpty()) throw new ClaimValidationException(errors);
        preserveInitialReserve(existing, claim);

        existing.setClaimDate(date(claim, "general.claimDate"));
        existing.setAnnouncementDate(date(claim, "event.announcementDate"));
        existing.setAccidentDate(dateOrNull(claim, "event.accidentDate"));
        existing.setClaimType(text(claim, "context.product", "MTPL"));
        existing.setPolicyType(text(claim, "general.policyType", "MTPL"));
        existing.setPolicySeries(text(claim, "general.policySeries", ""));
        existing.setPolicyNumber(text(claim, "general.policyNumber", ""));
        existing.setInsuredName(text(claim, "policySnapshot.ownerData", ""));
        existing.setNotificationNumber(text(claim, "general.notificationNumber", ""));
        existing.setExternalClaimNumber(text(claim, "general.externalClaimNumber", ""));
        existing.setExternalNotificationNumber(text(claim, "general.externalNotificationNumber", ""));
        existing.setClaimantName(claimantName(claim));
        // The damaged vehicle belongs in the claimant column; preserve the insured policy vehicle.
        existing.setClaimantVehicleRegistration(text(claim, "damagedObject.vehicle.registrationPlate", ""));
        existing.setInsuranceCompany(text(claim, "context.companyNetwork", "Nexus Mutual"));
        existing.setSelectedCharacteristics(String.join(",", characteristics(claim)));
        existing.setClaimPayload(toJson(claim));
        // Claim number, status, idempotency key, creation date, and creator are immutable here.
        return detail(claimFileRepository.save(existing));
    }

    private void preserveInitialReserve(ClaimFile existing, JsonNode updated) {
        try {
            JsonNode saved = objectMapper.readTree(existing.getClaimPayload() == null ? "{}" : existing.getClaimPayload());
            if (saved == null) return;
            JsonNode original = saved.at("/valuation/initialReserve");
            JsonNode proposed = updated.at("/valuation/initialReserve");
            if (!original.isObject()) return;
            String oldAmount = original.path("amount").asText("");
            String newAmount = proposed.path("amount").asText("");
            boolean sameAmount = oldAmount.isBlank() ? newAmount.isBlank()
                    : !newAmount.isBlank() && new java.math.BigDecimal(oldAmount).compareTo(new java.math.BigDecimal(newAmount)) == 0;
            if (!sameAmount || !original.path("currency").asText("").equals(proposed.path("currency").asText(""))) {
                throw new ClaimValidationException(List.of(new ClaimValidationError("valuation.initialReserve.amount",
                        "Initial reserve is fixed after creation. Use the Reserves tab to record reserve changes.")));
            }
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the original reserve snapshot.", exception);
        }
    }

    private ClaimFile findClaim(long id) {
        return claimFileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim file was not found."));
    }

    private ClaimFileDetailDTO detail(ClaimFile claim) {
        JsonNode payload = null;
        if (!isBlank(claim.getClaimPayload())) {
            try {
                payload = objectMapper.readTree(claim.getClaimPayload());
                if (payload == null || payload.isNull() || (payload.isObject() && payload.isEmpty())) payload = null;
                else if (!payload.isObject()) throw new IllegalStateException("Stored claim data is not an object.");
            } catch (JsonProcessingException | IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load saved claim details.", exception);
            }
        }
        return new ClaimFileDetailDTO(toDto(claim), payload == null ? null : sanitizeDamagedObject(payload), claimAccessPolicy.canCurrentActorEdit());
    }

    @Transactional
    public ClaimFileDTO createClaim(CreateClaimFileRequest request) {
        if (request == null || isBlank(request.idempotencyKey())) {
            throw new ClaimValidationException(List.of(new ClaimValidationError("idempotencyKey", "Idempotency key is required.")));
        }
        if (request.claim() == null || request.claim().isNull()) {
            throw new ClaimValidationException(List.of(new ClaimValidationError("claim", "Claim payload is required.")));
        }

        var existingClaim = claimFileRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingClaim.isPresent()) {
            return toDto(existingClaim.get());
        }

        JsonNode claim = sanitizeDamagedObject(request.claim());
        List<ClaimValidationError> validationErrors = claimFormValidator.validate(claim);
        if (!validationErrors.isEmpty()) {
            throw new ClaimValidationException(validationErrors);
        }


        ClaimFile saved = claimFileRepository.save(newClaim(claim, request.idempotencyKey()));
        claimReserves.initialize(saved.getId());
        claimTasks.ensureOpenTask(saved.getId());
        return toDto(saved);
    }

    private ClaimFile newClaim(JsonNode claim, String idempotencyKey) {
        return ClaimFile.builder()
                .claimNumber(claimNumberGenerator.nextClaimNumber())
                .idempotencyKey(idempotencyKey)
                .claimType(text(claim, "context.product", "MTPL"))
                .status(OPEN_STATUS)
                .claimDate(date(claim, "general.claimDate"))
                .announcementDate(date(claim, "event.announcementDate"))
                .accidentDate(dateOrNull(claim, "event.accidentDate"))
                .policyType(text(claim, "general.policyType", "MTPL"))
                .policySeries(text(claim, "general.policySeries", ""))
                .policyNumber(text(claim, "general.policyNumber", ""))
                .insuredName(text(claim, "policySnapshot.ownerData", ""))
                .notificationNumber(text(claim, "general.notificationNumber", ""))
                .externalClaimNumber(text(claim, "general.externalClaimNumber", ""))
                .externalNotificationNumber(text(claim, "general.externalNotificationNumber", ""))
                .claimantName(claimantName(claim))
                .insuredVehicleRegistration("")
                .claimantVehicleRegistration(text(claim, "damagedObject.vehicle.registrationPlate", ""))
                .insuranceCompany(text(claim, "context.companyNetwork", "Nexus Mutual"))
                .createdBy(SEEDED_ACTOR)
                .selectedCharacteristics(String.join(",", characteristics(claim)))
                .createdAt(Instant.now())
                .claimPayload(toJson(claim))
                .build();
    }

    private JsonNode sanitizeDamagedObject(JsonNode payload) {
        JsonNode copy = payload.deepCopy();
        if (copy instanceof ObjectNode root && root.path("damagedObject") instanceof ObjectNode object) {
            String branch = switch (object.path("type").asText()) {
                case "Vehicle" -> "vehicle";
                case "Injury" -> "injury";
                case "Other property" -> "property";
                default -> "";
            };
            for (String candidate : List.of("vehicle", "injury", "property")) {
                if (!candidate.equals(branch)) object.remove(candidate);
            }
        }
        return copy;
    }

    private ClaimFileDTO toDto(ClaimFile claim) {
        return new ClaimFileDTO(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getClaimType(),
                claim.getStatus(),
                claim.getClaimDate(),
                claim.getAnnouncementDate(),
                claim.getAccidentDate(),
                claim.getPolicyType(),
                claim.getPolicySeries(),
                claim.getPolicyNumber(),
                claim.getInsuredName(),
                claim.getNotificationNumber(),
                claim.getExternalClaimNumber(),
                claim.getExternalNotificationNumber(),
                claim.getClaimantName(),
                claim.getInsuredVehicleRegistration(),
                claim.getClaimantVehicleRegistration(),
                claim.getInsuranceCompany(),
                claim.getCreatedBy(),
                Arrays.stream(claim.getSelectedCharacteristics().split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList(),
                claim.getCreatedAt()
        );
    }

    private String toJson(JsonNode claim) {
        try {
            return objectMapper.writeValueAsString(claim);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Claim payload cannot be serialized.");
        }
    }

    private List<String> characteristics(JsonNode claim) {
        JsonNode values = node(claim, "context.characteristics");
        if (!values.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> characteristics = new java.util.ArrayList<>();
        for (JsonNode value : values) {
            characteristics.add(value.asText());
        }
        return characteristics;
    }

    private String claimantName(JsonNode claim) {
        if ("LEGAL_ENTITY".equals(text(claim, "claimant.type", ""))) {
            return text(claim, "claimant.companyName", "");
        }
        return (text(claim, "claimant.firstName", "") + " " + text(claim, "claimant.lastName", "")).trim();
    }

    private LocalDate date(JsonNode claim, String path) {
        return LocalDate.parse(text(claim, path, LocalDate.now().toString()));
    }

    private LocalDate dateOrNull(JsonNode claim, String path) {
        String value = text(claim, path, "");
        return isBlank(value) ? null : LocalDate.parse(value);
    }

    private String text(JsonNode claim, String path, String fallback) {
        JsonNode value = node(claim, path);
        if (value == null || value.isMissingNode() || value.isNull() || isBlank(value.asText())) {
            return fallback;
        }
        return value.asText().trim();
    }

    private JsonNode node(JsonNode claim, String path) {
        JsonNode current = claim;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private boolean matchesStatus(ClaimFile claim, String status) {
        return isBlank(status) || "All".equalsIgnoreCase(status) || claim.getStatus().equalsIgnoreCase(status);
    }

    private boolean matchesQuery(ClaimFile claim, String query) {
        if (isBlank(query)) {
            return true;
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return contains(claim.getClaimNumber(), normalizedQuery)
                || contains(claim.getInsuredName(), normalizedQuery)
                || contains(claim.getClaimantName(), normalizedQuery)
                || contains(claim.getNotificationNumber(), normalizedQuery)
                || contains(claim.getInsuredVehicleRegistration(), normalizedQuery)
                || contains(claim.getClaimantVehicleRegistration(), normalizedQuery);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
