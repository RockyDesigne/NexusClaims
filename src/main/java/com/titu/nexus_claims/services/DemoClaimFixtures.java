package com.titu.nexus_claims.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.titu.nexus_claims.validation.ClaimFormValidator;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Complete, explicitly fictional intake examples. Only used to seed an empty claims stack. */
public final class DemoClaimFixtures {
    private DemoClaimFixtures() { }

    public static List<JsonNode> load(ObjectMapper mapper) {
        LocalDate today = LocalDate.now(ClaimFormValidator.BUSINESS_ZONE);
        try (var input = new ClassPathResource("demo/claims.json").getInputStream()) {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__CLAIM_DATE__", today.minusDays(4).toString())
                    .replace("__ACCIDENT_DATE__", today.minusDays(7).toString())
                    .replace("__ANNOUNCEMENT_DATE__", today.minusDays(6).toString())
                    .replace("__ASSESSMENT_DATE__", today.minusDays(4).toString())
                    .replace("__REQUEST_DATE__", today.minusDays(3).toString())
                    .replace("__ISSUE_DATE__", today.minusDays(70).toString())
                    .replace("__VALID_FROM__", today.minusDays(60).toString())
                    .replace("__VALID_TO__", today.plusDays(300).toString());
            List<JsonNode> fixtures = new ArrayList<>();
            mapper.readTree(json).forEach(fixtures::add);
            return List.copyOf(fixtures);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load fictional demo claim fixtures.", exception);
        }
    }
}
