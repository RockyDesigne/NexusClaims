import { createInitialClaim, type ClaimFormModel } from './claimForm';
import type { ClaimFileDetailDTO } from '../services/api';

export function valueAt(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, key) =>
    current && typeof current === 'object' ? (current as Record<string, unknown>)[key] : undefined, value);
}

export function displayValue(value: unknown): string {
  if (value === undefined || value === null || value === '') return 'Not recorded';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'string' || typeof value === 'number') {
    const text = String(value);
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return `${text.slice(8, 10)}/${text.slice(5, 7)}/${text.slice(0, 4)}`;
    const labels: Record<string, string> = {
      ROMANIAN_NATURAL_PERSON: 'Romanian natural person',
      FOREIGN_NATURAL_PERSON: 'Foreign natural person',
      LEGAL_ENTITY: 'Legal entity',
    };
    return labels[text] ?? text;
  }
  if (Array.isArray(value)) return value.length ? value.map(displayValue).join(', ') : 'Not recorded';
  return 'Not recorded';
}

export function claimForEditing(detail: ClaimFileDetailDTO): ClaimFormModel {
  const draft = createInitialClaim(detail.summary.selectedCharacteristics);
  // Summary-only seeded files have no complete intake snapshot. Restore known values,
  // and leave the remaining fields empty for the director to complete before saving.
  draft.general.claimDate = detail.summary.claimDate;
  draft.general.policyType = detail.summary.policyType;
  draft.general.policySeries = detail.summary.policySeries ?? '';
  draft.general.policyNumber = detail.summary.policyNumber ?? '';
  draft.general.notificationNumber = detail.summary.notificationNumber ?? '';
  draft.general.externalClaimNumber = detail.summary.externalClaimNumber ?? '';
  draft.general.externalNotificationNumber = detail.summary.externalNotificationNumber ?? '';
  draft.context.companyNetwork = detail.summary.insuranceCompany;
  draft.context.product = detail.summary.claimType;
  draft.event.announcementDate = detail.summary.announcementDate;
  draft.event.accidentDate = detail.summary.accidentDate ?? '';
  draft.event.numberOfCarsInvolved = '';
  draft.event.accidentCountry = '';
  draft.policySnapshot.ownerData = detail.summary.insuredName ?? '';
  draft.damagedObject.vehicle.registrationPlate = detail.summary.claimantVehicleRegistration || detail.summary.insuredVehicleRegistration || '';
  mergeKnown(draft as unknown as Record<string, unknown>, detail.claim);
  return draft;
}

function mergeKnown(target: Record<string, unknown>, source: Record<string, unknown> | null) {
  if (!source) return;
  for (const key of Object.keys(target)) {
    const value = source[key];
    if (value === undefined || value === null) continue;
    if (Array.isArray(target[key]) && Array.isArray(value)) target[key] = structuredClone(value);
    else if (target[key] && typeof target[key] === 'object' && typeof value === 'object' && !Array.isArray(value)) {
      mergeKnown(target[key] as Record<string, unknown>, value as Record<string, unknown>);
    } else if (typeof target[key] === typeof value) target[key] = value;
  }
}
