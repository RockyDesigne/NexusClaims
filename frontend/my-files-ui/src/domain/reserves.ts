export const RESERVE_CATEGORIES = [
  { value: 'RESERVE', label: 'Reserve' },
  { value: 'RECOVERY_RESERVE', label: 'Recovery reserve' },
];
export const RESERVE_TYPES = [
  { value: 'MATERIAL_DAMAGE', label: 'Material damage' },
  { value: 'INJURY', label: 'Injury' },
  { value: 'TECHNICAL_OR_MEDICAL_EXPERTS', label: 'Technical or medical experts' },
];
export interface ReserveDraft { category: string; type: string; amount: string; currency: string; details: string; }
export function reserveErrors(draft: ReserveDraft) {
  const errors: Record<string, string> = {};
  if (!RESERVE_CATEGORIES.some((choice) => choice.value === draft.category)) errors.category = 'Choose a reserve category.';
  if (!RESERVE_TYPES.some((choice) => choice.value === draft.type)) errors.type = 'Choose a reserve type.';
  if (!['RON', 'EUR', 'USD'].includes(draft.currency)) errors.currency = 'Choose RON, EUR, or USD.';
  const amount = draft.amount.trim();
  // Validate decimal text without converting money to binary floating point.
  if (!/^-?(0|[1-9]\d{0,11})(\.\d{1,2})?$/.test(amount) || /^-?0(?:\.0{1,2})?$/.test(amount)) {
    errors.amount = 'Enter a non-zero decimal change with up to two decimal places (maximum 12 digits before the decimal).';
  }
  if (!draft.details.trim() || draft.details.trim().length > 1000) errors.details = 'Enter a reason for the change (up to 1,000 characters).';
  return errors;
}
export function reserveTypeLabel(type: string) { return RESERVE_TYPES.find((choice) => choice.value === type)?.label ?? type; }
