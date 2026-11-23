import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';
const source = readFileSync(new URL('../src/domain/reserves.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { reserveErrors } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const draft = { category: 'RESERVE', type: 'MATERIAL_DAMAGE', amount: '100.25', currency: 'RON', details: 'Fictional reassessment' };
test('valid positive and negative decimal adjustments are accepted as strings', () => {
  for (const amount of ['0.01', '100.25', '-100.25', '999999999999.99']) assert.deepEqual(reserveErrors({ ...draft, amount }), {});
});
test('zero, exponent notation, overprecision and malformed amounts are rejected', () => {
  for (const amount of ['0', '-0.00', '', '1.001', '1e2', 'NaN', '1000000000000.00', '1,25']) assert.ok(reserveErrors({ ...draft, amount }).amount, amount);
});
test('category, type, currency and reason are validated together', () => {
  assert.deepEqual(Object.keys(reserveErrors({ ...draft, category: 'wrong', type: '', currency: 'XYZ', details: '  ' })).sort(), ['category', 'currency', 'details', 'type']);
});

test('invalid initial-reserve precision is rejected before submitting intake', async () => {
  const source = readFileSync(new URL('../src/domain/claimForm.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
  const { createInitialClaim, validateClaim } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
  for (const amount of ['1.001', '1000000000000.00']) {
    const claim = createInitialClaim();
    claim.valuation.initialReserve = { amount, currency: 'RON' };
    assert.ok(validateClaim(claim).some((error) => error.path === 'valuation.initialReserve.amount'));
  }
});
