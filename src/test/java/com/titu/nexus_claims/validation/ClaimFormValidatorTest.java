package com.titu.nexus_claims.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimFormValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClaimFormValidator validator = new ClaimFormValidator();

    @Test
    void acceptsValidClaimWithEqualDateBoundariesVinAndSyntheticCnp() throws Exception {
        List<ClaimValidationError> errors = validator.validate(validClaim());

        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsAnnouncementDateAfterClaimDate() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "event", "announcementDate", "2026-07-20");
        set(claim, "general", "claimDate", "2026-07-19");

        assertThat(paths(validator.validate(claim))).contains("event.announcementDate", "general.claimDate");
    }

    @Test
    void rejectsAccidentDateAfterAnnouncementDate() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "event", "accidentDate", "2026-07-20");
        set(claim, "event", "announcementDate", "2026-07-19");

        assertThat(paths(validator.validate(claim))).contains("event.accidentDate", "event.announcementDate");
    }

    @Test
    void rejectsDamageAssessmentBeforeAccident() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "event", "damageAssessmentDate", "2026-07-18");
        set(claim, "event", "accidentDate", "2026-07-19");

        assertThat(paths(validator.validate(claim))).contains("event.damageAssessmentDate");
    }

    @Test
    void rejectsInvalidPolicyAndInsuredPeriods() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "policySnapshot", "validFrom", "2026-08-01");
        set(claim, "policySnapshot", "validTo", "2026-07-01");
        setNested(claim, "damagedObject", "vehicle", "insuredPeriodFrom", "2026-08-01");
        setNested(claim, "damagedObject", "vehicle", "insuredPeriodTo", "2026-07-01");

        assertThat(paths(validator.validate(claim))).contains(
                "policySnapshot.validFrom",
                "policySnapshot.validTo",
                "damagedObject.vehicle.insuredPeriodFrom",
                "damagedObject.vehicle.insuredPeriodTo"
        );
    }

    @Test
    void rejectsVinWrongLengthAndForbiddenCharacters() throws Exception {
        ObjectNode claim = validClaim();
        setNested(claim, "damagedObject", "vehicle", "vin", "WP0ZZZ99ZTS39212");
        assertThat(paths(validator.validate(claim))).contains("damagedObject.vehicle.vin");

        setNested(claim, "damagedObject", "vehicle", "vin", "WPOZZZ99ZTS39212Q");
        assertThat(paths(validator.validate(claim))).contains("damagedObject.vehicle.vin");
    }

    @Test
    void rejectsCnpWrongLengthImpossibleDateBadSequenceAndBadChecksum() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "claimant", "identifier", "123");
        assertThat(paths(validator.validate(claim))).contains("claimant.identifier");

        set(claim, "claimant", "identifier", "1960230010019");
        assertThat(messages(validator.validate(claim))).anyMatch(message -> message.contains("real birth date"));

        set(claim, "claimant", "identifier", "1960529010009");
        assertThat(messages(validator.validate(claim))).anyMatch(message -> message.contains("sequence"));

        set(claim, "claimant", "identifier", "1960529010019");
        assertThat(messages(validator.validate(claim))).anyMatch(message -> message.contains("checksum"));
    }

    @Test
    void foreignClaimantIdentifierIsNotValidatedAsCnp() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "claimant", "type", "FOREIGN_NATURAL_PERSON");
        set(claim, "claimant", "identifier", "AB-123");

        assertThat(paths(validator.validate(claim))).doesNotContain("claimant.identifier");
    }

    @Test
    void rejectsMoneyPairErrorsAndNegativeValues() throws Exception {
        ObjectNode claim = validClaim();
        setNested(claim, "valuation", "initialReserve", "amount", "100");
        setNested(claim, "valuation", "initialReserve", "currency", "");
        setNested(claim, "valuation", "vehicleMarketValue", "currency", "RON");
        setNested(claim, "valuation", "wreckedCarValue", "amount", "-1");
        setNested(claim, "valuation", "wreckedCarValue", "currency", "RON");

        assertThat(paths(validator.validate(claim))).contains(
                "valuation.initialReserve.currency",
                "valuation.vehicleMarketValue.amount",
                "valuation.wreckedCarValue.amount"
        );
    }

    @Test
    void rejectsRegressWithoutPercentageAndInvalidPercentage() throws Exception {
        ObjectNode claim = validClaim();
        setNested(claim, "damagedObject", "vehicle", "regress", "Regress");
        setNested(claim, "damagedObject", "vehicle", "recoveryPercentage", "");
        assertThat(paths(validator.validate(claim))).contains("damagedObject.vehicle.recoveryPercentage");

        setNested(claim, "damagedObject", "vehicle", "recoveryPercentage", "120");
        assertThat(paths(validator.validate(claim))).contains("damagedObject.vehicle.recoveryPercentage");
    }

    @Test
    void injuryRequiresNameAndDescriptionWithoutVehicleFields() throws Exception {
        ObjectNode claim = validClaim();
        ObjectNode object = (ObjectNode) claim.path("damagedObject");
        object.put("type", "Injury");
        object.remove("vehicle");
        object.putObject("injury");
        assertThat(paths(validator.validate(claim))).containsExactlyInAnyOrder(
                "damagedObject.injury.injuredPersonName", "damagedObject.injury.description");
        object.withObject("injury").put("injuredPersonName", "Synthetic injured person").put("description", "Reported bruising");
        assertThat(validator.validate(claim)).isEmpty();
    }

    @Test
    void validatesOptionalTreatmentDateAndRole() throws Exception {
        ObjectNode claim = validClaim();
        ObjectNode object = (ObjectNode) claim.path("damagedObject");
        object.put("type", "Injury");
        ObjectNode injury = object.putObject("injury").put("injuredPersonName", "Synthetic person").put("description", "Reported injury");
        for (String date : List.of("2026-07-18", "2999-01-01", "2026-02-30", "invalid")) {
            injury.put("firstTreatmentDate", date);
            assertThat(paths(validator.validate(claim))).contains("damagedObject.injury.firstTreatmentDate");
        }
        injury.put("firstTreatmentDate", "2026-07-19").put("roleInAccident", "Passenger");
        assertThat(validator.validate(claim)).isEmpty();
        injury.put("roleInAccident", "unsupported");
        assertThat(paths(validator.validate(claim))).containsExactly("damagedObject.injury.roleInAccident");
    }

    @Test
    void propertyRequiresCategoryOwnerLocationAndDescription() throws Exception {
        ObjectNode claim = validClaim();
        ObjectNode object = (ObjectNode) claim.path("damagedObject");
        object.put("type", "Other property");
        object.remove("vehicle");
        ObjectNode property = object.putObject("property");
        assertThat(paths(validator.validate(claim))).containsExactlyInAnyOrder(
                "damagedObject.property.category", "damagedObject.property.ownerName",
                "damagedObject.property.location", "damagedObject.property.description");
        property.put("category", "Fence or gate").put("ownerName", "Synthetic owner")
                .put("location", "Synthetic address").put("description", "Damaged fence");
        assertThat(validator.validate(claim)).isEmpty();
        property.put("category", "unsupported");
        assertThat(paths(validator.validate(claim))).containsExactly("damagedObject.property.category");
    }

    @Test
    void rejectsUnknownDamagedObjectType() throws Exception {
        ObjectNode claim = validClaim();
        set(claim, "damagedObject", "type", "Unknown");
        assertThat(paths(validator.validate(claim))).contains("damagedObject.type");
    }

    private ObjectNode validClaim() throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {
                  "context": {"companyNetwork": "Nexus Mutual", "product": "MTPL", "characteristics": ["Material damages"]},
                  "general": {"claimDate": "2026-07-19", "policyType": "MTPL", "policySeries": "NX", "policyNumber": "123456", "notificationNumber": "NOT-1", "uncoveredRisk": false},
                  "policySnapshot": {"validFrom": "2026-01-01", "validTo": "2026-12-31"},
                  "event": {"announcementDate": "2026-07-19", "damageAssessmentDate": "2026-07-20", "accidentDate": "2026-07-19", "accidentCountry": "Romania", "accidentCounty": "Bucharest", "accidentCity": "Bucharest", "numberOfCarsInvolved": "2", "accidentDocuments": "Police report"},
                  "claimant": {"type": "ROMANIAN_NATURAL_PERSON", "identifier": "1960529010018", "firstName": "Test", "lastName": "Claimant", "companyName": "", "address": "Synthetic address", "phone": "+40700111222", "email": "claimant@example.test"},
                  "damagedObject": {"type": "Vehicle", "vehicle": {"registrationPlate": "B-100-NCX", "vin": "WP0ZZZ99ZTS392124", "mileage": "12000", "manufactureYear": "2020", "firstRegistrationDate": "2020-06-01", "insuredPeriodFrom": "2026-01-01", "insuredPeriodTo": "2026-12-31", "regress": "", "recoveryPercentage": ""}},
                  "damages": {"lines": [{"name": "Front bumper", "number": "1", "symbol": "FB", "operation": "Repair"}]},
                  "valuation": {"initialReserve": {"amount": "1000.00", "currency": "RON"}, "vehicleMarketValue": {"amount": "", "currency": ""}, "wreckedCarValue": {"amount": "", "currency": ""}, "maximumCompensation": {"amount": "", "currency": ""}},
                  "liability": {"guiltyStatus": "Insured driver", "damagedVehicleDriver": "Synthetic driver"}
                }
                """);
    }

    private void set(ObjectNode claim, String object, String field, String value) {
        ((ObjectNode) claim.path(object)).put(field, value);
    }

    private void setNested(ObjectNode claim, String object, String nested, String field, String value) {
        ((ObjectNode) claim.path(object).path(nested)).put(field, value);
    }

    private List<String> paths(List<ClaimValidationError> errors) {
        return errors.stream().map(ClaimValidationError::path).toList();
    }

    private List<String> messages(List<ClaimValidationError> errors) {
        return errors.stream().map(ClaimValidationError::message).toList();
    }
}
