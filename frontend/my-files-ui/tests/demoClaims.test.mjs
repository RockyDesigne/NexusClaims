import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/domain/claimForm.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { validateClaim, todayInBusinessZone } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const today = todayInBusinessZone();
const offsetDate = (days) => new Date(Date.parse(`${today}T00:00:00Z`) + days * 86400000).toISOString().slice(0, 10);
let json = readFileSync(new URL('../../../src/main/resources/demo/claims.json', import.meta.url), 'utf8');
for (const [name, offset] of Object.entries({ CLAIM_DATE: -4, ACCIDENT_DATE: -7, ANNOUNCEMENT_DATE: -6, ASSESSMENT_DATE: -4, REQUEST_DATE: -3, ISSUE_DATE: -70, VALID_FROM: -60, VALID_TO: 300 })) {
  json = json.replaceAll(`__${name}__`, offsetDate(offset));
}
const examples = JSON.parse(json);
for (const example of examples) {
  test(`complete ${example.claim.damagedObject.type} demo passes frontend intake validation`, () => {
    assert.deepEqual(validateClaim(example.claim), []);
    assert.equal(Object.keys(example.claim.damagedObject).length, 2);
    assert.ok(example.claim.damages.lines.length > 0);
    assert.ok(example.claim.claimant.email.endsWith('@example.test'));
  });
}
