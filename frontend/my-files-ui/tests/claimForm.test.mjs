import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';

// Use the installed compiler and Node's test runner; no additional test dependency.
const source = readFileSync(new URL('../src/domain/claimForm.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { createInitialClaim, sanitizeClaimForSubmit, validateClaim } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const objectErrors = (claim) => validateClaim(claim).filter(({ path }) => path.startsWith('damagedObject.'));

for (const [type, branch] of [['Vehicle', 'vehicle'], ['Injury', 'injury'], ['Other property', 'property']]) {
  test(`${type} submits only its active branch and preserves the editing draft`, () => {
    const claim = createInitialClaim();
    claim.damagedObject.type = type;
    claim.damagedObject.vehicle.registrationPlate = 'SYNTHETIC';
    claim.damagedObject.injury.description = '  Synthetic injury  ';
    claim.damagedObject.property.description = '  Synthetic property  ';
    const before = structuredClone(claim);
    const payload = sanitizeClaimForSubmit(claim);
    assert.deepEqual(Object.keys(payload.damagedObject).sort(), [branch, 'type'].sort());
    assert.deepEqual(claim, before);
    if (branch !== 'vehicle') assert.equal(payload.damagedObject[branch].description, `Synthetic ${branch}`);
  });
}

test('injury-only composition starts with Injury selected', () => {
  assert.equal(createInitialClaim(['Injury']).damagedObject.type, 'Injury');
  assert.equal(createInitialClaim(['Material damages', 'Injury']).damagedObject.type, 'Vehicle');
});

test('injury requires the person and description, with optional treatment details', () => {
  const claim = createInitialClaim(['Injury']);
  assert.deepEqual(objectErrors(claim).map(({ path }) => path).sort(), ['damagedObject.injury.description', 'damagedObject.injury.injuredPersonName']);
  claim.damagedObject.injury.injuredPersonName = 'Synthetic injured person';
  claim.damagedObject.injury.description = 'Reported bruising';
  assert.deepEqual(objectErrors(sanitizeClaimForSubmit(claim)), []);
});

test('treatment date accepts the accident date and rejects impossible, earlier, or future dates', () => {
  const claim = createInitialClaim(['Injury']);
  claim.event.accidentDate = '2026-01-10';
  for (const date of ['2026-01-09', '2999-01-01', '2026-02-30', 'invalid']) {
    claim.damagedObject.injury.firstTreatmentDate = date;
    assert.ok(objectErrors(claim).some(({ path }) => path === 'damagedObject.injury.firstTreatmentDate'), date);
  }
  claim.damagedObject.injury.firstTreatmentDate = '2026-01-10';
  assert.ok(!objectErrors(claim).some(({ path }) => path === 'damagedObject.injury.firstTreatmentDate'));
});

test('property minimums are checked without vehicle or injury requirements', () => {
  const claim = createInitialClaim();
  claim.damagedObject.type = 'Other property';
  assert.deepEqual(objectErrors(claim).map(({ path }) => path).sort(), ['category', 'description', 'location', 'ownerName'].map((key) => `damagedObject.property.${key}`));
  Object.assign(claim.damagedObject.property, { category: 'Fence or gate', ownerName: 'Synthetic owner', location: 'Synthetic address', description: 'Damaged fence' });
  claim.damagedObject.injury.firstTreatmentDate = 'invalid';
  assert.deepEqual(objectErrors(sanitizeClaimForSubmit(claim)), []);
});

test('switching away and back retains branch values for editing', () => {
  const claim = createInitialClaim();
  claim.damagedObject.type = 'Injury';
  claim.damagedObject.injury.injuredPersonName = 'Synthetic person';
  claim.damagedObject.type = 'Other property';
  claim.damagedObject.property.ownerName = 'Synthetic owner';
  assert.ok(!('injury' in sanitizeClaimForSubmit(claim).damagedObject));
  claim.damagedObject.type = 'Injury';
  assert.equal(sanitizeClaimForSubmit(claim).damagedObject.injury.injuredPersonName, 'Synthetic person');
});
