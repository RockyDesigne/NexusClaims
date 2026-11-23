export type ClaimantType = 'ROMANIAN_NATURAL_PERSON' | 'FOREIGN_NATURAL_PERSON' | 'LEGAL_ENTITY';
export type DamagedObjectType = 'Vehicle' | 'Injury' | 'Other property';

export interface MoneyValue {
  amount: string;
  currency: string;
}

export interface DamageLine {
  id: string;
  name: string;
  number: string;
  symbol: string;
  operation: string;
}

export interface ClaimFormModel {
  context: {
    companyNetwork: string;
    product: string;
    characteristics: string[];
  };
  general: {
    claimDate: string;
    policyType: string;
    policySeries: string;
    policyNumber: string;
    notificationNumber: string;
    externalClaimNumber: string;
    externalNotificationNumber: string;
    sharedFault: boolean;
    suspiciousClaim: boolean;
    externalClaim: boolean;
    directSettlement: boolean;
    insuredVehicleAlienated: boolean;
    uncoveredRisk: boolean;
    onlineClaim: boolean;
  };
  policySnapshot: {
    issueDate: string;
    ownerData: string;
    userData: string;
    vehicleData: string;
    validFrom: string;
    validTo: string;
    directSettlementEligible: string;
    lookupStatus: string;
  };
  additionalServices: {
    audatex: boolean;
    dat: boolean;
    auction: boolean;
  };
  event: {
    announcementDate: string;
    damageAssessmentDate: string;
    damageAssessmentCounty: string;
    accidentDate: string;
    accidentTime: string;
    accidentLocation: string;
    accidentCounty: string;
    accidentCity: string;
    accidentCountry: string;
    accidentPlace: string;
    description: string;
    accidentVisibility: string;
    roadCondition: string;
    accidentDocuments: string;
    amicableSettlementNumber: string;
    remarksInspectionReport: string;
    additionalInvestigations: string;
    numberOfCarsInvolved: string;
    directEmailNoPriorPhone: boolean;
    knownEmailOrIntermediary: boolean;
    thirdPartyOrAutoServiceNotification: boolean;
    compensationRequestedAtNotification: boolean;
    differentCityOrRepairShopAssessment: boolean;
  };
  claimant: {
    type: ClaimantType;
    identifier: string;
    firstName: string;
    lastName: string;
    companyName: string;
    address: string;
    phone: string;
    email: string;
  };
  damagedObject: {
    type: DamagedObjectType;
    vehicle: {
      registrationPlate: string;
      vin: string;
      category: string;
      make: string;
      model: string;
      registrationType: string;
      mileage: string;
      manufactureYear: string;
      vehicleColor: string;
      firstRegistrationDate: string;
      insuredPeriodFrom: string;
      insuredPeriodTo: string;
      regress: string;
      recoveryPercentage: string;
      aidaStatus: string;
    };
    injury: {
      injuredPersonName: string;
      roleInAccident: string;
      description: string;
      bodyArea: string;
      medicalProvider: string;
      firstTreatmentDate: string;
      medicalDocumentReference: string;
    };
    property: {
      category: string;
      ownerName: string;
      location: string;
      identifier: string;
      description: string;
    };
  };
  damages: {
    lines: DamageLine[];
    documents: string;
    accidentType: string;
    damageType: string;
    damageReason: string;
    solutionMode: string;
    preexistingDamages: string;
  };
  valuation: {
    initialReserve: MoneyValue;
    vehicleMarketValue: MoneyValue;
    wreckedCarValue: MoneyValue;
    maximumCompensation: MoneyValue;
    requestDate: string;
  };
  liability: {
    guiltyStatus: string;
    damagedVehicleDriver: string;
  };
}

// Keep all branches in the editing draft so switching tabs preserves entered data.
// The API payload contains exactly one branch, selected by the type discriminator.
export type DamagedObject =
  | { type: 'Vehicle'; vehicle: ClaimFormModel['damagedObject']['vehicle'] }
  | { type: 'Injury'; injury: ClaimFormModel['damagedObject']['injury'] }
  | { type: 'Other property'; property: ClaimFormModel['damagedObject']['property'] };

export type ClaimSubmission = Omit<ClaimFormModel, 'damagedObject'> & { damagedObject: DamagedObject };

// Provisional intake minimums; screenshots do not show either branch expanded.
export const DAMAGED_OBJECT_REQUIRED_FIELDS = {
  Injury: {
    injuredPersonName: 'Injured person name is required.',
    description: 'Injury description is required.',
  },
  'Other property': {
    category: 'Property category is required.',
    ownerName: 'Property owner name is required.',
    location: 'Property location is required.',
    description: 'Property and damage description is required.',
  },
} as const;

export const INJURED_PERSON_ROLES = ['', 'Driver', 'Passenger', 'Pedestrian', 'Cyclist', 'Other', 'Unknown'];
export const PROPERTY_CATEGORIES = ['', 'Building', 'Fence or gate', 'Road infrastructure', 'Personal belongings', 'Cargo', 'Other'];

export interface ValidationError {
  path: string;
  message: string;
}

export const CHARACTERISTICS = ['Material damages', 'Injury', 'Direct settlement', 'Regress', 'External', 'External regress'];
export const CURRENCIES = ['', 'RON', 'EUR', 'USD'];
export const LOCATION_TREE: Record<string, Record<string, string[]>> = {
  Romania: {
    Bucharest: ['Bucharest'],
    Cluj: ['Cluj-Napoca', 'Turda'],
    Timis: ['Timisoara', 'Lugoj'],
  },
  Hungary: {
    Budapest: ['Budapest'],
  },
};

const CNP_COUNTIES = new Set([
  '01', '02', '03', '04', '05', '06', '07', '08', '09', '10',
  '11', '12', '13', '14', '15', '16', '17', '18', '19', '20',
  '21', '22', '23', '24', '25', '26', '27', '28', '29', '30',
  '31', '32', '33', '34', '35', '36', '37', '38', '39', '40',
  '41', '42', '43', '44', '45', '46', '51', '52',
]);

export function todayInBusinessZone() {
  const now = new Date();
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Bucharest',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

export function createInitialClaim(characteristics: string[] = ['Material damages']): ClaimFormModel {
  const today = todayInBusinessZone();
  return {
    context: {
      companyNetwork: 'Nexus Mutual',
      product: 'MTPL',
      characteristics,
    },
    general: {
      claimDate: today,
      policyType: 'MTPL',
      policySeries: '',
      policyNumber: '',
      notificationNumber: '',
      externalClaimNumber: '',
      externalNotificationNumber: '',
      sharedFault: false,
      suspiciousClaim: false,
      externalClaim: characteristics.includes('External'),
      directSettlement: characteristics.includes('Direct settlement'),
      insuredVehicleAlienated: false,
      uncoveredRisk: false,
      onlineClaim: false,
    },
    policySnapshot: {
      issueDate: '',
      ownerData: '',
      userData: '',
      vehicleData: '',
      validFrom: '',
      validTo: '',
      directSettlementEligible: '',
      lookupStatus: 'Policy lookup is a local stub until the policy API contract is available.',
    },
    additionalServices: { audatex: false, dat: false, auction: false },
    event: {
      announcementDate: today,
      damageAssessmentDate: '',
      damageAssessmentCounty: '',
      accidentDate: today,
      accidentTime: '',
      accidentLocation: '',
      accidentCounty: '',
      accidentCity: '',
      accidentCountry: 'Romania',
      accidentPlace: '',
      description: '',
      accidentVisibility: '',
      roadCondition: '',
      accidentDocuments: '',
      amicableSettlementNumber: '',
      remarksInspectionReport: '',
      additionalInvestigations: '',
      numberOfCarsInvolved: '2',
      directEmailNoPriorPhone: false,
      knownEmailOrIntermediary: false,
      thirdPartyOrAutoServiceNotification: false,
      compensationRequestedAtNotification: false,
      differentCityOrRepairShopAssessment: false,
    },
    claimant: {
      type: 'ROMANIAN_NATURAL_PERSON',
      identifier: '',
      firstName: '',
      lastName: '',
      companyName: '',
      address: '',
      phone: '',
      email: '',
    },
    damagedObject: {
      type: characteristics.includes('Injury') && !characteristics.includes('Material damages') ? 'Injury' : 'Vehicle',
      vehicle: {
        registrationPlate: '',
        vin: '',
        category: '',
        make: '',
        model: '',
        registrationType: '',
        mileage: '',
        manufactureYear: '',
        vehicleColor: '',
        firstRegistrationDate: '',
        insuredPeriodFrom: '',
        insuredPeriodTo: '',
        regress: characteristics.includes('Regress') || characteristics.includes('External regress') ? 'Regress' : '',
        recoveryPercentage: '',
        aidaStatus: 'AIDA is stubbed. No external verification has been performed.',
      },
      injury: { injuredPersonName: '', roleInAccident: '', description: '', bodyArea: '', medicalProvider: '', firstTreatmentDate: '', medicalDocumentReference: '' },
      property: { category: '', ownerName: '', location: '', identifier: '', description: '' },
    },
    damages: {
      lines: [],
      documents: '',
      accidentType: characteristics.includes('Material damages') ? 'Material damage' : '',
      damageType: '',
      damageReason: '',
      solutionMode: '',
      preexistingDamages: '',
    },
    valuation: {
      initialReserve: { amount: '', currency: '' },
      vehicleMarketValue: { amount: '', currency: '' },
      wreckedCarValue: { amount: '', currency: '' },
      maximumCompensation: { amount: '', currency: '' },
      requestDate: '',
    },
    liability: {
      guiltyStatus: '',
      damagedVehicleDriver: '',
    },
  };
}

export function sanitizeClaimForSubmit(claim: ClaimFormModel): ClaimSubmission {
  const trimmed = structuredClone(claim);
  trimStrings(trimmed);
  const draft = trimmed.damagedObject;
  let damagedObject: DamagedObject;
  switch (draft.type) {
    case 'Vehicle':
      draft.vehicle.vin = draft.vehicle.vin.toUpperCase();
      damagedObject = { type: 'Vehicle', vehicle: draft.vehicle };
      break;
    case 'Injury':
      damagedObject = { type: 'Injury', injury: draft.injury };
      break;
    case 'Other property':
      damagedObject = { type: 'Other property', property: draft.property };
      break;
  }
  if (trimmed.claimant.type === 'LEGAL_ENTITY') {
    trimmed.claimant.firstName = '';
    trimmed.claimant.lastName = '';
  } else {
    trimmed.claimant.companyName = '';
  }
  return { ...trimmed, damagedObject };
}

export function validateClaim(claim: ClaimFormModel | ClaimSubmission): ValidationError[] {
  const errors: ValidationError[] = [];
  const add = (path: string, message: string) => errors.push({ path, message });
  const today = todayInBusinessZone();
  const materialDamage = claim.context.characteristics.includes('Material damages');
  const regressCharacteristic = claim.context.characteristics.includes('Regress') || claim.context.characteristics.includes('External regress');

  required(claim.context.companyNetwork, 'context.companyNetwork', 'Company/network is required.', add);
  required(claim.context.product, 'context.product', 'Product is required.', add);
  if (!claim.context.characteristics.includes('Material damages') && !claim.context.characteristics.includes('Injury')) {
    add('context.characteristics', 'Select Material damages or Injury before saving.');
  }

  required(claim.general.policyType, 'general.policyType', 'Policy type is required.', add);
  required(claim.general.policySeries, 'general.policySeries', 'Policy series is required.', add);
  required(claim.general.policyNumber, 'general.policyNumber', 'Policy number is required.', add);
  required(claim.general.notificationNumber, 'general.notificationNumber', 'Notification number is required.', add);
  required(claim.general.claimDate, 'general.claimDate', 'Claim date is required.', add);
  required(claim.event.announcementDate, 'event.announcementDate', 'Announcement date is required.', add);
  required(claim.event.accidentDate, 'event.accidentDate', 'Accident date is required.', add);

  if (claim.general.claimDate > today) add('general.claimDate', 'Claim date must not be in the future.');
  if (claim.event.announcementDate && claim.general.claimDate && claim.event.announcementDate > claim.general.claimDate) {
    add('event.announcementDate', 'Announcement date must be on or before claim date.');
    add('general.claimDate', 'Claim date must be on or after announcement date.');
  }
  if (claim.event.accidentDate && claim.event.announcementDate && claim.event.accidentDate > claim.event.announcementDate) {
    add('event.accidentDate', 'Accident date must be on or before announcement date.');
    add('event.announcementDate', 'Announcement date must be on or after accident date.');
  }
  if (claim.event.damageAssessmentDate && claim.event.accidentDate && claim.event.damageAssessmentDate < claim.event.accidentDate) {
    add('event.damageAssessmentDate', 'Damage assessment date must not be earlier than accident date.');
  }
  if (claim.policySnapshot.validFrom && claim.policySnapshot.validTo && claim.policySnapshot.validFrom > claim.policySnapshot.validTo) {
    add('policySnapshot.validFrom', 'Policy validity from must be on or before policy validity to.');
    add('policySnapshot.validTo', 'Policy validity to must be on or after policy validity from.');
  }
  if (claim.event.accidentDate && claim.policySnapshot.validFrom && claim.policySnapshot.validTo && !claim.general.uncoveredRisk) {
    if (claim.event.accidentDate < claim.policySnapshot.validFrom || claim.event.accidentDate > claim.policySnapshot.validTo) {
      add('event.accidentDate', 'Accident date must fall inside policy validity unless uncovered risk is selected.');
    }
  }

  required(claim.event.accidentCountry, 'event.accidentCountry', 'Accident country is required.', add);
  required(claim.event.accidentCounty, 'event.accidentCounty', 'Accident county is required.', add);
  required(claim.event.accidentCity, 'event.accidentCity', 'Accident city is required.', add);
  validateLocation(claim, add);
  positiveInteger(claim.event.numberOfCarsInvolved, 'event.numberOfCarsInvolved', 'Number of cars involved must be a positive integer.', add);
  if (claim.event.accidentDocuments === 'Amicable settlement') {
    required(claim.event.amicableSettlementNumber, 'event.amicableSettlementNumber', 'Amicable settlement number is required.', add);
  }

  validateClaimant(claim, add);
  validateDamagedObject(claim, regressCharacteristic, today, add);
  if (materialDamage && claim.damages.lines.length === 0) {
    add('damages.lines', 'Add at least one damage line for a material-damage claim.');
  }
  claim.damages.lines.forEach((line, index) => {
    required(line.name, `damages.lines.${index}.name`, 'Damage name is required.', add);
    required(line.number, `damages.lines.${index}.number`, 'Damage number is required.', add);
    required(line.symbol, `damages.lines.${index}.symbol`, 'Damage part is required.', add);
    required(line.operation, `damages.lines.${index}.operation`, 'Damage operation is required.', add);
  });

  validateMoney(claim.valuation.initialReserve, 'valuation.initialReserve', add);
  if (claim.valuation.initialReserve.amount && !/^\d{1,12}(\.\d{1,2})?$/.test(claim.valuation.initialReserve.amount)) {
    add('valuation.initialReserve.amount', 'Initial reserve must be non-negative, with up to two decimal places and twelve digits before the decimal.');
  }
  validateMoney(claim.valuation.vehicleMarketValue, 'valuation.vehicleMarketValue', add);
  validateMoney(claim.valuation.wreckedCarValue, 'valuation.wreckedCarValue', add);
  validateMoney(claim.valuation.maximumCompensation, 'valuation.maximumCompensation', add);

  return errors;
}

export function validateVin(value: string): string | null {
  const vin = value.trim().toUpperCase();
  if (vin.length !== 17) return 'Chassis series/VIN must contain exactly 17 characters.';
  if (!/^[A-HJ-NPR-Z0-9]{17}$/.test(vin)) return 'Chassis series/VIN may contain only A-H, J-N, P, R-Z, and digits.';
  return null;
}

export function validateCnp(value: string): string | null {
  const cnp = value.trim();
  if (!/^\d{13}$/.test(cnp)) return 'CNP must contain exactly 13 digits.';
  const s = Number(cnp[0]);
  const prefix = s === 1 || s === 2 ? 1900 : s === 3 || s === 4 ? 1800 : s >= 5 && s <= 9 ? 2000 : -1;
  if (prefix === -1) return 'CNP has an invalid sex/century digit.';
  const birth = new Date(Date.UTC(prefix + Number(cnp.slice(1, 3)), Number(cnp.slice(3, 5)) - 1, Number(cnp.slice(5, 7))));
  if (birth.getUTCFullYear() !== prefix + Number(cnp.slice(1, 3)) || birth.getUTCMonth() !== Number(cnp.slice(3, 5)) - 1 || birth.getUTCDate() !== Number(cnp.slice(5, 7))) {
    return 'CNP must contain a real birth date.';
  }
  if (birth > new Date()) return 'CNP birth date must not be in the future.';
  if (!CNP_COUNTIES.has(cnp.slice(7, 9))) return 'CNP county code is invalid.';
  if (cnp.slice(9, 12) === '000') return 'CNP sequence cannot be 000.';
  const weights = '279146358279';
  const sum = weights.split('').reduce((total, weight, index) => total + Number(weight) * Number(cnp[index]), 0);
  const expected = sum % 11 === 10 ? 1 : sum % 11;
  if (expected !== Number(cnp[12])) return 'CNP checksum is invalid.';
  return null;
}

function validateClaimant(claim: ClaimFormModel | ClaimSubmission, add: (path: string, message: string) => void) {
  required(claim.claimant.identifier, 'claimant.identifier', 'Claimant identifier is required.', add);
  required(claim.claimant.address, 'claimant.address', 'Claimant address is required.', add);
  if (claim.claimant.type === 'LEGAL_ENTITY') {
    required(claim.claimant.companyName, 'claimant.companyName', 'Company name is required for legal entity claimants.', add);
  } else {
    required(claim.claimant.firstName, 'claimant.firstName', 'First name is required.', add);
    required(claim.claimant.lastName, 'claimant.lastName', 'Last name is required.', add);
  }
  if (claim.claimant.type === 'ROMANIAN_NATURAL_PERSON') {
    const cnpError = validateCnp(claim.claimant.identifier);
    if (cnpError) add('claimant.identifier', cnpError);
  }
  if (claim.claimant.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(claim.claimant.email)) {
    add('claimant.email', 'Email must use a valid email format.');
  }
  if (claim.claimant.phone && !/^\+?[0-9 ()-]{7,20}$/.test(claim.claimant.phone)) {
    add('claimant.phone', 'Phone must contain a valid international or local number.');
  }
}

function validateDamagedObject(claim: ClaimFormModel | ClaimSubmission, regressCharacteristic: boolean, today: string, add: (path: string, message: string) => void) {
  const object = claim.damagedObject;
  if (object.type === 'Injury' || object.type === 'Other property') {
    const branch = object.type === 'Injury' ? 'injury' : 'property';
    const values = object.type === 'Injury' ? object.injury : object.property;
    for (const [field, message] of Object.entries(DAMAGED_OBJECT_REQUIRED_FIELDS[object.type])) {
      required(values[field as keyof typeof values], `damagedObject.${branch}.${field}`, message, add);
    }
    if (object.type === 'Other property' && object.property.category && !PROPERTY_CATEGORIES.includes(object.property.category)) {
      add('damagedObject.property.category', 'Choose a supported property category.');
    }
    if (object.type === 'Injury') {
      if (object.injury.roleInAccident && !INJURED_PERSON_ROLES.includes(object.injury.roleInAccident)) {
        add('damagedObject.injury.roleInAccident', 'Choose a supported role in the accident.');
      }
      const date = object.injury.firstTreatmentDate;
      if (date && (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(date)) || new Date(date).toISOString().slice(0, 10) !== date)) {
        add('damagedObject.injury.firstTreatmentDate', 'Enter a valid date.');
      } else if (date && (date > today || (claim.event.accidentDate && date < claim.event.accidentDate))) {
        add('damagedObject.injury.firstTreatmentDate', 'First treatment date must be on or after the accident and not in the future.');
      }
    }
    return;
  }
  if (object.type !== 'Vehicle') {
    add('damagedObject.type', 'Choose Vehicle, Injury, or Other property.');
    return;
  }
  const vehicle = object.vehicle;
  required(vehicle.registrationPlate, 'damagedObject.vehicle.registrationPlate', 'Vehicle registration plate is required.', add);
  required(vehicle.vin, 'damagedObject.vehicle.vin', 'Chassis series/VIN is required.', add);
  const vinError = validateVin(vehicle.vin);
  if (vehicle.vin && vinError) add('damagedObject.vehicle.vin', vinError);
  nonNegativeInteger(vehicle.mileage, 'damagedObject.vehicle.mileage', 'Mileage must be a non-negative integer.', add);
  const currentYear = Number(today.slice(0, 4));
  if (vehicle.manufactureYear) {
    const year = Number(vehicle.manufactureYear);
    if (!/^\d{4}$/.test(vehicle.manufactureYear) || year < 1886 || year > currentYear + 1) {
      add('damagedObject.vehicle.manufactureYear', 'Manufacture year must be a plausible four-digit year.');
    }
    if (vehicle.firstRegistrationDate && year > Number(vehicle.firstRegistrationDate.slice(0, 4))) {
      add('damagedObject.vehicle.manufactureYear', 'Manufacture year must not be later than first registration year.');
    }
  }
  if (vehicle.firstRegistrationDate && vehicle.firstRegistrationDate > (claim.event.accidentDate || today)) {
    add('damagedObject.vehicle.firstRegistrationDate', 'First registration date must not be later than accident date or today.');
  }
  if (vehicle.insuredPeriodFrom && vehicle.insuredPeriodTo && vehicle.insuredPeriodFrom > vehicle.insuredPeriodTo) {
    add('damagedObject.vehicle.insuredPeriodFrom', 'Insured period from must be on or before insured period to.');
    add('damagedObject.vehicle.insuredPeriodTo', 'Insured period to must be on or after insured period from.');
  }
  if (claim.event.accidentDate && vehicle.insuredPeriodFrom && vehicle.insuredPeriodTo && !claim.general.uncoveredRisk) {
    if (claim.event.accidentDate < vehicle.insuredPeriodFrom || claim.event.accidentDate > vehicle.insuredPeriodTo) {
      add('event.accidentDate', 'Accident date must fall inside insured period unless uncovered risk is selected.');
    }
  }
  if (regressCharacteristic && !vehicle.regress) {
    add('damagedObject.vehicle.regress', 'Regress is required for regress claim characteristics.');
  }
  if (vehicle.regress) {
    if (!vehicle.recoveryPercentage) add('damagedObject.vehicle.recoveryPercentage', 'Percentage for recovery is required when regress is selected.');
    const percentage = Number(vehicle.recoveryPercentage);
    if (!Number.isFinite(percentage) || percentage < 0 || percentage > 100) {
      add('damagedObject.vehicle.recoveryPercentage', 'Percentage for recovery must be between 0 and 100.');
    }
  }
}

function validateMoney(value: MoneyValue, path: string, add: (path: string, message: string) => void) {
  if (value.amount && !value.currency) add(`${path}.currency`, 'Currency is required when an amount is entered.');
  if (!value.amount && value.currency) add(`${path}.amount`, 'Amount is required when a currency is selected.');
  if (value.amount) {
    const number = Number(value.amount);
    if (!Number.isFinite(number)) add(`${path}.amount`, 'Enter a valid decimal amount.');
    if (number < 0) add(`${path}.amount`, 'Amount must be non-negative.');
  }
}

function validateLocation(claim: ClaimFormModel | ClaimSubmission, add: (path: string, message: string) => void) {
  const counties = LOCATION_TREE[claim.event.accidentCountry];
  if (!counties) return;
  const cities = counties[claim.event.accidentCounty];
  if (claim.event.accidentCounty && !cities) add('event.accidentCounty', 'Selected county does not belong to the selected country.');
  if (cities && claim.event.accidentCity && !cities.includes(claim.event.accidentCity)) {
    add('event.accidentCity', 'Selected city does not belong to the selected county.');
  }
}

function required(value: string | string[], path: string, message: string, add: (path: string, message: string) => void) {
  if (Array.isArray(value) ? value.length === 0 : value.trim().length === 0) add(path, message);
}

function positiveInteger(value: string, path: string, message: string, add: (path: string, message: string) => void) {
  if (!/^[1-9]\d*$/.test(value)) add(path, message);
}

function nonNegativeInteger(value: string, path: string, message: string, add: (path: string, message: string) => void) {
  if (value && !/^\d+$/.test(value)) add(path, message);
}

function trimStrings(value: unknown) {
  if (!value || typeof value !== 'object') return;
  for (const key of Object.keys(value)) {
    const record = value as Record<string, unknown>;
    if (typeof record[key] === 'string') record[key] = record[key].trim();
    else trimStrings(record[key]);
  }
}
