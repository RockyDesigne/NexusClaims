package com.titu.nexus_claims.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.datasource.hikari.maximum-pool-size=1"
})
@AutoConfigureMockMvc
class ClaimFileControllerTest {
    @Autowired private MockMvc mvc;

    @Test
    void retrievesCompleteSeedAndEditCapability() throws Exception {
        mvc.perform(get("/claims/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.claimNumber").value("CLM-" + java.time.Year.now().getValue() + "-000001"))
                .andExpect(jsonPath("$.claim.damagedObject.type").value("Vehicle"))
                .andExpect(jsonPath("$.claim.policySnapshot.ownerData").isNotEmpty())
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void completeDemoCanBeSavedWithoutLosingInsuredOrClaimantPlate() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var result = mvc.perform(get("/claims/1")).andReturn();
        var claim = mapper.readTree(result.getResponse().getContentAsString()).path("claim");
        var body = mapper.createObjectNode().set("claim", claim);
        mvc.perform(put("/claims/1").contentType("application/json").content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.insuredVehicleRegistration").value("B-901-DMO"))
                .andExpect(jsonPath("$.summary.claimantVehicleRegistration").value("B-101-DMO"))
                .andExpect(jsonPath("$.claim.damagedObject.type").value("Vehicle"));
    }

    @Test
    void unknownClaimReturns404() throws Exception {
        mvc.perform(get("/claims/999999")).andExpect(status().isNotFound());
    }

    @Test
    void browserCanPreflightAnUpdate() throws Exception {
        mvc.perform(options("/claims/1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT"));
    }

    @Test
    void updateValidationReturnsInlineErrorPaths() throws Exception {
        mvc.perform(put("/claims/1").contentType("application/json").content("{\"claim\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[0].path").value("context.companyNetwork"));
    }
}
