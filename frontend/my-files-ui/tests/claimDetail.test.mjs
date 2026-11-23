import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';

const compile = (name) => ts.transpileModule(readFileSync(new URL(`../src/domain/${name}.ts`, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const uri = (source) => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`;
const formModule = uri(compile('claimForm'));
const { claimForEditing, displayValue, valueAt } = await import(uri(compile('claimDetail').replace("'./claimForm'", `'${formModule}'`)));
const { createInitialClaim, sanitizeClaimForSubmit } = await import(formModule);
const summary = {
  id: 7, claimNumber: 'CLM-TEST-7', selectedCharacteristics: ['Injury'], claimDate: '2026-01-10',
  announcementDate: '2026-01-10', accidentDate: '2026-01-09', policyType: 'MTPL',
  policySeries: 'SYN', policyNumber: '100', notificationNumber: 'SYN-1', claimType: 'MTPL', insuranceCompany: 'Nexus Mutual',
};

test('saved injury claim round trips into an editing draft without losing its intake fields', () => {
  const claim = createInitialClaim(['Injury']);
  claim.damagedObject.injury.injuredPersonName = 'Synthetic person';
  claim.damagedObject.injury.description = 'Reported bruising';
  claim.valuation.initialReserve = { amount: '1234.50', currency: 'EUR' };
  const payload = sanitizeClaimForSubmit(claim);
  const detail = { summary, claim: payload, canEdit: true };
  const draft = claimForEditing(detail);
  assert.deepEqual(sanitizeClaimForSubmit(draft), payload);
  assert.ok('vehicle' in draft.damagedObject);
  assert.deepEqual(detail.claim, payload);
});

test('summary-only seeds restore known fields and leave missing incident data empty', () => {
  const draft = claimForEditing({ summary, claim: null, canEdit: true });
  assert.equal(draft.general.policyNumber, '100');
  assert.equal(draft.event.accidentDate, '2026-01-09');
  assert.equal(draft.event.accidentCountry, '');
  assert.equal(draft.event.numberOfCarsInvolved, '');
  assert.equal(draft.claimant.firstName, '');
});

test('partial older snapshots get defaults for newly added fields without overwriting saved values', () => {
  const draft = claimForEditing({ summary, claim: { damagedObject: { type: 'Injury', injury: { description: 'Saved description' } }, general: { sharedFault: true } }, canEdit: true });
  assert.equal(draft.damagedObject.injury.description, 'Saved description');
  assert.equal(draft.damagedObject.injury.medicalProvider, '');
  assert.equal(draft.general.sharedFault, true);
});

test('detail formatting distinguishes missing values from false and zero and preserves money strings', () => {
  assert.equal(displayValue(false), 'No');
  assert.equal(displayValue(0), '0');
  assert.equal(displayValue('1234.50 EUR'), '1234.50 EUR');
  assert.equal(displayValue('2026-01-10'), '10/01/2026');
  assert.equal(displayValue(undefined), 'Not recorded');
  assert.equal(valueAt(null, 'damagedObject.injury.description'), undefined);
});
