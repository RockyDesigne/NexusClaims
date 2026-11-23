package com.titu.nexus_claims.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClaimFileDTO(
        Long id,
        String claimNumber,
        String claimType,
        String status,
        LocalDate claimDate,
        LocalDate announcementDate,
        LocalDate accidentDate,
        String policyType,
        String policySeries,
        String policyNumber,
        String insuredName,
        String notificationNumber,
        String externalClaimNumber,
        String externalNotificationNumber,
        String claimantName,
        String insuredVehicleRegistration,
        String claimantVehicleRegistration,
        String insuranceCompany,
        String createdBy,
        List<String> selectedCharacteristics,
        Instant createdAt
) {
}
