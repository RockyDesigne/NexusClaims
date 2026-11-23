import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
import ts from 'typescript';
const source = readFileSync(new URL('../src/domain/tasks.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { handoffErrors, isActiveTask, filterTasks, taskErrors, emptyTaskDraft, TASK_NAMES } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const first = { id: 1, title: 'Inspect vehicle', description: 'Fictional bumper', status: 'OPEN', assignee: 'alex', type: 'DAMAGE_ASSESSMENT' };
const completed = { ...first, id: 2, status: 'COMPLETED' };
test('handoffs require a predefined task and a different explicit assignee', () => {
  assert.deepEqual(handoffErrors({ ...emptyTaskDraft(), type: 'PAYMENT', assignee: 'dana' }, 'alex'), {});
  assert.ok(handoffErrors({ ...emptyTaskDraft(), assignee: 'alex' }, 'alex').assignee);
  assert.deepEqual(handoffErrors({ ...emptyTaskDraft(), assignee: 'alex' }, 'alex', true), {});
  assert.ok(handoffErrors({ ...emptyTaskDraft(), assignee: '' }, 'alex', true).assignee);
  assert.ok(handoffErrors({ ...emptyTaskDraft(), assignee: '' }, 'alex').assignee);
  assert.ok(handoffErrors({ ...emptyTaskDraft(), type: '', assignee: 'dana' }, 'alex').type);
  for (const status of ['OPEN', 'IN_PROGRESS']) assert.equal(isActiveTask({ status }), true);
  for (const status of ['COMPLETED', 'CANCELLED']) assert.equal(isActiveTask({ status }), false);
});
test('combined filters preserve task and assignee context', () => {
  const other = { ...first, id: 3, title: 'Documents', assignee: 'dana', type: 'DOCUMENT_REVIEW' };
  const tasks = [first, completed, other];
  assert.deepEqual(filterTasks(tasks, ' BUMPER ', 'alex', 'DAMAGE_ASSESSMENT', 'OPEN'), [first]);
  assert.deepEqual(filterTasks(tasks, '', 'dana', '', ''), [other]);
  assert.deepEqual(filterTasks(tasks, 'none', '', '', ''), []);
  assert.deepEqual(filterTasks(tasks, '', '', '', ''), tasks);
});
test('task names come from the six predefined choices and the default is Damage Assessment', () => {
  assert.deepEqual(Object.keys(TASK_NAMES), ['DAMAGE_ASSESSMENT', 'CLAIM_SETTLEMENT', 'DAMAGE_REASSESSMENT', 'PAYMENT', 'RECOVERY', 'ANTI_FRAUD']);
  assert.equal(emptyTaskDraft().type, 'DAMAGE_ASSESSMENT');
  assert.deepEqual(taskErrors(emptyTaskDraft()), {});
  for (const type of ['CLAIM_REVIEW', 'DOCUMENT_REVIEW', 'FOLLOW_UP', 'invented', 'toString']) assert.ok(taskErrors({ ...emptyTaskDraft(), type }).type);
  assert.ok(taskErrors({ ...emptyTaskDraft(), description: 'x'.repeat(4001) }).description);
});
