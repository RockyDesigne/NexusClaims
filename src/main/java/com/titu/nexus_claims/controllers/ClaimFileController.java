package com.titu.nexus_claims.controllers;

import com.titu.nexus_claims.dtos.ClaimFileDTO;
import com.titu.nexus_claims.dtos.ClaimFileDetailDTO;
import com.titu.nexus_claims.dtos.UpdateClaimFileRequest;
import com.titu.nexus_claims.dtos.ClaimStackSummaryDTO;
import com.titu.nexus_claims.dtos.CreateClaimFileRequest;
import com.titu.nexus_claims.dtos.StubIntegrationResponse;
import com.titu.nexus_claims.services.AidaVerificationProvider;
import com.titu.nexus_claims.services.ClaimFileService;
import com.titu.nexus_claims.services.PolicySnapshotProvider;
import com.titu.nexus_claims.validation.ClaimValidationError;
import com.titu.nexus_claims.validation.ClaimValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimFileController {
    private final ClaimFileService claimFileService;
    private final PolicySnapshotProvider policySnapshotProvider;
    private final AidaVerificationProvider aidaVerificationProvider;

    @GetMapping
    public ResponseEntity<ClaimStackSummaryDTO> getClaims(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(claimFileService.getClaims(query, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimFileDetailDTO> getClaim(@PathVariable long id) {
        return ResponseEntity.ok(claimFileService.getClaim(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimFileDetailDTO> updateClaim(@PathVariable long id, @RequestBody UpdateClaimFileRequest request) {
        return ResponseEntity.ok(claimFileService.updateClaim(id, request));
    }

    @PostMapping
    public ResponseEntity<ClaimFileDTO> createClaim(@RequestBody CreateClaimFileRequest request) {
        return ResponseEntity.ok(claimFileService.createClaim(request));
    }

    @GetMapping("/policy-snapshot")
    public ResponseEntity<StubIntegrationResponse> getPolicySnapshot(
            @RequestParam String policySeries,
            @RequestParam String policyNumber
    ) {
        return ResponseEntity.ok(policySnapshotProvider.lookup(policySeries, policyNumber));
    }

    @GetMapping("/aida-check")
    public ResponseEntity<StubIntegrationResponse> checkAida(
            @RequestParam(required = false) String registrationPlate,
            @RequestParam(required = false) String vin
    ) {
        return ResponseEntity.ok(aidaVerificationProvider.checkVehicle(registrationPlate, vin));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(ClaimValidationException.class)
    public ResponseEntity<List<ClaimValidationError>> handleClaimValidationError(ClaimValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getErrors());
    }
}
