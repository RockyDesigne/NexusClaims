package com.titu.nexus_claims.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ClaimFormValidator {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Bucharest");
    private static final Pattern VIN_PATTERN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");
    private static final Set<String> CURRENCIES = Set.of("RON", "EUR", "USD");
    // Provisional minimums inferred for intake; not supplied by the screenshots.
    private static final Map<String, Map<String, String>> DAMAGED_OBJECT_REQUIRED_FIELDS = Map.of(
            "Injury", Map.of(
                    "injuredPersonName", "Injured person name is required.",
                    "description", "Injury description is required."
            ),
            "Other property", Map.of(
                    "category", "Property category is required.",
                    "ownerName", "Property owner name is required.",
                    "location", "Property location is required.",
                    "description", "Property and damage description is required."
            )
    );
    private static final Set<String> PROPERTY_CATEGORIES = Set.of("Building", "Fence or gate", "Road infrastructure", "Personal belongings", "Cargo", "Other");
    private static final Set<String> INJURED_PERSON_ROLES = Set.of("Driver", "Passenger", "Pedestrian", "Cyclist", "Other", "Unknown");
    private static final Set<String> ROMANIAN_CNP_COUNTIES = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
            "31", "32", "33", "34", "35", "36", "37", "38", "39", "40",
            "41", "42", "43", "44", "45", "46", "51", "52"
    );
    private static final Map<String, Map<String, Set<String>>> LOCATION_TREE = Map.of(
            "Romania", Map.of(
                    "Bucharest", Set.of("Bucharest"),
                    "Cluj", Set.of("Cluj-Napoca", "Turda"),
                    "Timis", Set.of("Timisoara", "Lugoj")
            ),
            "Hungary", Map.of(
                    "Budapest", Set.of("Budapest")
            )
    );

    public List<ClaimValidationError> validate(JsonNode claim) {
        ValidationErrors errors = new ValidationErrors();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        requireText(claim, "context.companyNetwork", "Company/network is required.", errors);
        requireText(claim, "context.product", "Product is required.", errors);
        if (!isArrayWithValues(at(claim, "context.characteristics"))) {
            errors.add("context.characteristics", "Select at least one claim characteristic.");
        }

        boolean materialDamage = arrayContains(claim, "context.characteristics", "Material damages");
        boolean injury = arrayContains(claim, "context.characteristics", "Injury");
        boolean regressCharacteristic = arrayContains(claim, "context.characteristics", "Regress")
                || arrayContains(claim, "context.characteristics", "External regress");
        if (!materialDamage && !injury) {
            errors.add("context.characteristics", "Select Material damages or Injury before saving.");
        }

        requireText(claim, "general.policyType", "Policy type is required.", errors);
        requireText(claim, "general.policySeries", "Policy series is required.", errors);
        requireText(claim, "general.policyNumber", "Policy number is required.", errors);
        requireText(claim, "general.notificationNumber", "Notification number is required.", errors);

        LocalDate claimDate = requiredDate(claim, "general.claimDate", "Claim date is required.", errors);
        LocalDate announcementDate = requiredDate(claim, "event.announcementDate", "Announcement date is required.", errors);
        LocalDate accidentDate = requiredDate(claim, "event.accidentDate", "Accident date is required.", errors);
        LocalDate assessmentDate = optionalDate(claim, "event.damageAssessmentDate", errors);
        LocalDate policyValidFrom = optionalDate(claim, "policySnapshot.validFrom", errors);
        LocalDate policyValidTo = optionalDate(claim, "policySnapshot.validTo", errors);

        if (claimDate != null && claimDate.isAfter(today)) {
            errors.add("general.claimDate", "Claim date must not be in the future.");
        }
        if (announcementDate != null && claimDate != null && announcementDate.isAfter(claimDate)) {
            errors.add("event.announcementDate", "Announcement date must be on or before claim date.");
            errors.add("general.claimDate", "Claim date must be on or after announcement date.");
        }
        if (accidentDate != null && announcementDate != null && accidentDate.isAfter(announcementDate)) {
            errors.add("event.accidentDate", "Accident date must be on or before announcement date.");
            errors.add("event.announcementDate", "Announcement date must be on or after accident date.");
        }
        if (assessmentDate != null && accidentDate != null && assessmentDate.isBefore(accidentDate)) {
            errors.add("event.damageAssessmentDate", "Damage assessment date must not be earlier than accident date.");
        }
        if (policyValidFrom != null && policyValidTo != null && policyValidFrom.isAfter(policyValidTo)) {
            errors.add("policySnapshot.validFrom", "Policy validity from must be on or before policy validity to.");
            errors.add("policySnapshot.validTo", "Policy validity to must be on or after policy validity from.");
        }
        if (accidentDate != null && policyValidFrom != null && policyValidTo != null
                && (accidentDate.isBefore(policyValidFrom) || accidentDate.isAfter(policyValidTo))
                && !bool(claim, "general.uncoveredRisk")) {
            errors.add("event.accidentDate", "Accident date must fall inside policy validity unless uncovered risk is selected.");
            errors.add("general.uncoveredRisk", "Select uncovered risk only when the exception is supported.");
        }

        requireText(claim, "event.accidentCountry", "Accident country is required.", errors);
        requireText(claim, "event.accidentCounty", "Accident county is required.", errors);
        requireText(claim, "event.accidentCity", "Accident city is required.", errors);
        validateLocation(claim, errors);
        requireText(claim, "event.numberOfCarsInvolved", "Number of cars involved is required.", errors);
        positiveInteger(claim, "event.numberOfCarsInvolved", "Number of cars involved must be a positive integer.", errors);
        if ("Amicable settlement".equals(text(claim, "event.accidentDocuments"))
                && isBlank(text(claim, "event.amicableSettlementNumber"))) {
            errors.add("event.amicableSettlementNumber", "Amicable settlement number is required.");
        }

        validateClaimant(claim, errors);
        validateDamagedObject(claim, today, accidentDate, regressCharacteristic, errors);
        validateDamages(claim, materialDamage, errors);
        validateValuation(claim, errors);

        return errors.list();
    }

    private void validateClaimant(JsonNode claim, ValidationErrors errors) {
        String type = text(claim, "claimant.type");
        requireText(claim, "claimant.type", "Claimant type is required.", errors);
        requireText(claim, "claimant.identifier", "Claimant identifier is required.", errors);
        requireText(claim, "claimant.address", "Claimant address is required.", errors);

        if ("LEGAL_ENTITY".equals(type)) {
            requireText(claim, "claimant.companyName", "Company name is required for legal entity claimants.", errors);
        } else {
            requireText(claim, "claimant.firstName", "First name is required.", errors);
            requireText(claim, "claimant.lastName", "Last name is required.", errors);
        }

        if ("ROMANIAN_NATURAL_PERSON".equals(type)) {
            validateCnp(text(claim, "claimant.identifier"), "claimant.identifier", errors);
        }

        String email = text(claim, "claimant.email");
        if (!isBlank(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            errors.add("claimant.email", "Email must use a valid email format.");
        }

        String phone = text(claim, "claimant.phone");
        if (!isBlank(phone) && !PHONE_PATTERN.matcher(phone).matches()) {
            errors.add("claimant.phone", "Phone must contain a valid international or local number.");
        }
    }

    private void validateDamagedObject(JsonNode claim, LocalDate today, LocalDate accidentDate, boolean regressCharacteristic, ValidationErrors errors) {
        String type = text(claim, "damagedObject.type");
        requireText(claim, "damagedObject.type", "Damaged object type is required.", errors);
        if (DAMAGED_OBJECT_REQUIRED_FIELDS.containsKey(type)) {
            String branch = "Injury".equals(type) ? "injury" : "property";
            DAMAGED_OBJECT_REQUIRED_FIELDS.get(type).forEach((field, message) ->
                    requireText(claim, "damagedObject." + branch + "." + field, message, errors));
            if ("Injury".equals(type)) {
                String role = text(claim, "damagedObject.injury.roleInAccident");
                if (!isBlank(role) && !INJURED_PERSON_ROLES.contains(role)) {
                    errors.add("damagedObject.injury.roleInAccident", "Choose a supported role in the accident.");
                }
                LocalDate treatmentDate = optionalDate(claim, "damagedObject.injury.firstTreatmentDate", errors);
                if (treatmentDate != null && (treatmentDate.isAfter(today)
                        || (accidentDate != null && treatmentDate.isBefore(accidentDate)))) {
                    errors.add("damagedObject.injury.firstTreatmentDate", "First treatment date must be on or after the accident and not in the future.");
                }
            } else {
                String category = text(claim, "damagedObject.property.category");
                if (!isBlank(category) && !PROPERTY_CATEGORIES.contains(category)) {
                    errors.add("damagedObject.property.category", "Choose a supported property category.");
                }
            }
            return;
        }
        if (!"Vehicle".equals(type)) {
            errors.add("damagedObject.type", "Choose Vehicle, Injury, or Other property.");
            return;
        }

        requireText(claim, "damagedObject.vehicle.registrationPlate", "Vehicle registration plate is required.", errors);
        requireText(claim, "damagedObject.vehicle.vin", "Chassis series/VIN is required.", errors);
        validateVin(text(claim, "damagedObject.vehicle.vin"), "damagedObject.vehicle.vin", errors);
        nonNegativeInteger(claim, "damagedObject.vehicle.mileage", "Mileage must be a non-negative integer.", errors);

        int year = integerValue(claim, "damagedObject.vehicle.manufactureYear", -1);
        if (year != -1 && (year < 1886 || year > Year.now(BUSINESS_ZONE).getValue() + 1)) {
            errors.add("damagedObject.vehicle.manufactureYear", "Manufacture year must be a plausible four-digit year.");
        }

        LocalDate firstRegistration = optionalDate(claim, "damagedObject.vehicle.firstRegistrationDate", errors);
        LocalDate insuredFrom = optionalDate(claim, "damagedObject.vehicle.insuredPeriodFrom", errors);
        LocalDate insuredTo = optionalDate(claim, "damagedObject.vehicle.insuredPeriodTo", errors);
        if (insuredFrom != null && insuredTo != null && insuredFrom.isAfter(insuredTo)) {
            errors.add("damagedObject.vehicle.insuredPeriodFrom", "Insured period from must be on or before insured period to.");
            errors.add("damagedObject.vehicle.insuredPeriodTo", "Insured period to must be on or after insured period from.");
        }
        LocalDate firstRegistrationLimit = accidentDate == null ? today : accidentDate;
        if (firstRegistration != null && firstRegistration.isAfter(firstRegistrationLimit)) {
            errors.add("damagedObject.vehicle.firstRegistrationDate", "First registration date must not be later than accident date or today.");
        }
        if (firstRegistration != null && year != -1 && year > firstRegistration.getYear()) {
            errors.add("damagedObject.vehicle.manufactureYear", "Manufacture year must not be later than first registration year.");
        }
        if (accidentDate != null && insuredFrom != null && insuredTo != null
                && (accidentDate.isBefore(insuredFrom) || accidentDate.isAfter(insuredTo))
                && !bool(claim, "general.uncoveredRisk")) {
            errors.add("event.accidentDate", "Accident date must fall inside insured period unless uncovered risk is selected.");
        }

        String regress = text(claim, "damagedObject.vehicle.regress");
        if (regressCharacteristic && isBlank(regress)) {
            errors.add("damagedObject.vehicle.regress", "Regress is required for regress claim characteristics.");
        }
        if (!isBlank(regress)) {
            BigDecimal percentage = decimal(claim, "damagedObject.vehicle.recoveryPercentage", errors);
            if (percentage == null) {
                errors.add("damagedObject.vehicle.recoveryPercentage", "Percentage for recovery is required when regress is selected.");
            } else if (percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.add("damagedObject.vehicle.recoveryPercentage", "Percentage for recovery must be between 0 and 100.");
            }
        }
    }

    private void validateDamages(JsonNode claim, boolean materialDamage, ValidationErrors errors) {
        JsonNode lines = at(claim, "damages.lines");
        if (materialDamage && (!lines.isArray() || lines.isEmpty())) {
            errors.add("damages.lines", "Add at least one damage line for a material-damage claim.");
        }
        if (lines.isArray()) {
            for (int i = 0; i < lines.size(); i++) {
                JsonNode line = lines.get(i);
                requireText(line, "name", "Damage name is required.", "damages.lines." + i + ".name", errors);
                requireText(line, "number", "Damage number is required.", "damages.lines." + i + ".number", errors);
                requireText(line, "symbol", "Damage part is required.", "damages.lines." + i + ".symbol", errors);
                requireText(line, "operation", "Damage operation is required.", "damages.lines." + i + ".operation", errors);
            }
        }
    }

    private void validateValuation(JsonNode claim, ValidationErrors errors) {
        validateMoney(claim, "valuation.initialReserve", errors);
        String initialAmount = text(claim, "valuation.initialReserve.amount");
        if (!isBlank(initialAmount) && !initialAmount.matches("\\d{1,12}(\\.\\d{1,2})?")) {
            errors.add("valuation.initialReserve.amount", "Initial reserve must be non-negative, with up to two decimal places and twelve digits before the decimal.");
        }
        validateMoney(claim, "valuation.vehicleMarketValue", errors);
        validateMoney(claim, "valuation.wreckedCarValue", errors);
        validateMoney(claim, "valuation.maximumCompensation", errors);
        optionalDate(claim, "valuation.requestDate", errors);
    }

    private void validateMoney(JsonNode claim, String path, ValidationErrors errors) {
        String amountText = text(claim, path + ".amount");
        String currency = text(claim, path + ".currency");
        boolean hasAmount = !isBlank(amountText);
        boolean hasCurrency = !isBlank(currency);
        if (hasAmount && !hasCurrency) {
            errors.add(path + ".currency", "Currency is required when an amount is entered.");
        }
        if (!hasAmount && hasCurrency) {
            errors.add(path + ".amount", "Amount is required when a currency is selected.");
        }
        if (hasAmount) {
            BigDecimal amount = decimal(claim, path + ".amount", errors);
            if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(path + ".amount", "Amount must be non-negative.");
            }
        }
        if (hasCurrency && !CURRENCIES.contains(currency)) {
            errors.add(path + ".currency", "Currency must be a supported ISO currency.");
        }
    }

    public void validateVin(String value, String path, ValidationErrors errors) {
        String vin = value == null ? "" : value.trim().toUpperCase();
        if (vin.length() != 17) {
            errors.add(path, "Chassis series/VIN must contain exactly 17 characters.");
            return;
        }
        if (!VIN_PATTERN.matcher(vin).matches()) {
            errors.add(path, "Chassis series/VIN may contain only A-H, J-N, P, R-Z, and digits.");
        }
    }

    public void validateCnp(String value, String path, ValidationErrors errors) {
        String cnp = value == null ? "" : value.trim();
        if (!cnp.matches("\\d{13}")) {
            errors.add(path, "CNP must contain exactly 13 digits.");
            return;
        }

        int sexCentury = Character.digit(cnp.charAt(0), 10);
        int yearPrefix = switch (sexCentury) {
            case 1, 2 -> 1900;
            case 3, 4 -> 1800;
            case 5, 6 -> 2000;
            case 7, 8, 9 -> 2000;
            default -> -1;
        };
        if (yearPrefix == -1) {
            errors.add(path, "CNP has an invalid sex/century digit.");
            return;
        }

        int year = yearPrefix + Integer.parseInt(cnp.substring(1, 3));
        int month = Integer.parseInt(cnp.substring(3, 5));
        int day = Integer.parseInt(cnp.substring(5, 7));
        try {
            LocalDate birthDate = LocalDate.of(year, month, day);
            if (birthDate.isAfter(LocalDate.now(BUSINESS_ZONE))) {
                errors.add(path, "CNP birth date must not be in the future.");
            }
        } catch (DateTimeException exception) {
            errors.add(path, "CNP must contain a real birth date.");
        }

        String county = cnp.substring(7, 9);
        if (!ROMANIAN_CNP_COUNTIES.contains(county)) {
            errors.add(path, "CNP county code is invalid.");
        }
        if ("000".equals(cnp.substring(9, 12))) {
            errors.add(path, "CNP sequence cannot be 000.");
        }

        String weights = "279146358279";
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += Character.digit(cnp.charAt(i), 10) * Character.digit(weights.charAt(i), 10);
        }
        int expected = sum % 11;
        if (expected == 10) {
            expected = 1;
        }
        int actual = Character.digit(cnp.charAt(12), 10);
        if (actual != expected) {
            errors.add(path, "CNP checksum is invalid.");
        }
    }

    private void validateLocation(JsonNode claim, ValidationErrors errors) {
        String country = text(claim, "event.accidentCountry");
        String county = text(claim, "event.accidentCounty");
        String city = text(claim, "event.accidentCity");
        if (isBlank(country) || isBlank(county) || isBlank(city)) {
            return;
        }
        Map<String, Set<String>> counties = LOCATION_TREE.get(country);
        if (counties == null) {
            errors.add("event.accidentCountry", "Selected country is not supported.");
            return;
        }
        Set<String> cities = counties.get(county);
        if (cities == null) {
            errors.add("event.accidentCounty", "Selected county does not belong to the selected country.");
            return;
        }
        if (!cities.contains(city)) {
            errors.add("event.accidentCity", "Selected city does not belong to the selected county.");
        }
    }

    private LocalDate requiredDate(JsonNode node, String path, String message, ValidationErrors errors) {
        String value = text(node, path);
        if (isBlank(value)) {
            errors.add(path, message);
            return null;
        }
        return parseDate(value, path, errors);
    }

    private LocalDate optionalDate(JsonNode node, String path, ValidationErrors errors) {
        String value = text(node, path);
        if (isBlank(value)) {
            return null;
        }
        return parseDate(value, path, errors);
    }

    private LocalDate parseDate(String value, String path, ValidationErrors errors) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            errors.add(path, "Enter a valid date.");
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String path, ValidationErrors errors) {
        String value = text(node, path);
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            errors.add(path, "Enter a valid decimal amount.");
            return null;
        }
    }

    private void positiveInteger(JsonNode node, String path, String message, ValidationErrors errors) {
        int value = integerValue(node, path, -1);
        if (value <= 0) {
            errors.add(path, message);
        }
    }

    private void nonNegativeInteger(JsonNode node, String path, String message, ValidationErrors errors) {
        String raw = text(node, path);
        if (isBlank(raw)) {
            return;
        }
        int value = integerValue(node, path, -1);
        if (value < 0) {
            errors.add(path, message);
        }
    }

    private int integerValue(JsonNode node, String path, int fallback) {
        String value = text(node, path);
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void requireText(JsonNode node, String path, String message, ValidationErrors errors) {
        requireText(node, path, message, path, errors);
    }

    private void requireText(JsonNode node, String sourcePath, String message, String errorPath, ValidationErrors errors) {
        if (isBlank(text(node, sourcePath))) {
            errors.add(errorPath, message);
        }
    }

    private boolean arrayContains(JsonNode node, String path, String value) {
        JsonNode array = at(node, path);
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isArrayWithValues(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty();
    }

    private boolean bool(JsonNode node, String path) {
        JsonNode value = at(node, path);
        return value != null && value.asBoolean(false);
    }

    private String text(JsonNode node, String path) {
        JsonNode value = at(node, path);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private JsonNode at(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class ValidationErrors {
        private final java.util.ArrayList<ClaimValidationError> errors = new java.util.ArrayList<>();

        public void add(String path, String message) {
            errors.add(new ClaimValidationError(path, message));
        }

        public List<ClaimValidationError> list() {
            return List.copyOf(errors);
        }
    }
}
