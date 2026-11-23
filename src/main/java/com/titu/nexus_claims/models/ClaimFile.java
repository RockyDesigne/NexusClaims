package com.titu.nexus_claims.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Entity
@org.hibernate.annotations.DynamicUpdate
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claim_files")
public class ClaimFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String claimNumber;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String claimType;

    @Column(nullable = false)
    private String status;

    private Instant closedAt;
    private String closedBy;
    private Long closureTaskId;

    @Column(nullable = false)
    private LocalDate claimDate;

    @Column(nullable = false)
    private LocalDate announcementDate;

    private LocalDate accidentDate;

    @Column(nullable = false)
    private String policyType;

    private String policySeries;

    private String policyNumber;

    private String insuredName;

    private String notificationNumber;

    private String externalClaimNumber;

    private String externalNotificationNumber;

    private String claimantName;

    private String insuredVehicleRegistration;

    private String claimantVehicleRegistration;

    private String insuranceCompany;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private String selectedCharacteristics;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String claimPayload;
}
