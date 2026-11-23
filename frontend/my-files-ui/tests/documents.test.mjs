import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';
const source = readFileSync(new URL('../src/domain/documents.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { documentFileError, filterDocuments } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
test('upload validation rejects empty, large, unsupported and unsafe filenames', () => {
  const allowed = ['.pdf', '.png'];
  assert.equal(documentFileError({ name: 'demo.PDF', size: 20 }, allowed, 100), '');
  for (const file of [{ name: 'demo.pdf', size: 0 }, { name: 'demo.pdf', size: 101 }, { name: 'demo.html', size: 10 }, { name: 'bad\nname.pdf', size: 10 }]) assert.ok(documentFileError(file, allowed, 100));
});
test('category and tag filters combine without changing the original list', () => {
  const documents = [{ id: 1, category: 'IMAGE', tag: 'Damaged Vehicle' }, { id: 2, category: 'OTHER', tag: 'Accident Documents' }, { id: 3, category: 'OTHER', tag: '' }];
  assert.deepEqual(filterDocuments(documents, 'IMAGE', 'ALL').map((item) => item.id), [1]);
  assert.deepEqual(filterDocuments(documents, 'OTHER', 'Accident Documents').map((item) => item.id), [2]);
  assert.deepEqual(filterDocuments(documents, 'ALL', 'UNTAGGED').map((item) => item.id), [3]);
  assert.deepEqual(filterDocuments(documents, 'IMAGE', 'Accident Documents'), []);
  assert.equal(documents.length, 3);
});
