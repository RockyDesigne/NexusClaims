import type { ClaimTaskDTO } from '../services/api';
export const TASK_NAMES: Record<string, string> = {
  DAMAGE_ASSESSMENT: 'Damage Assessment', CLAIM_SETTLEMENT: 'Claim Settlement',
  DAMAGE_REASSESSMENT: 'Damage Reassessment', PAYMENT: 'Payment', RECOVERY: 'Recovery', ANTI_FRAUD: 'Anti-fraud Investigation',
};
export const taskLabel = (value: string) => TASK_NAMES[value] ?? ({ OPEN: 'Open', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled' })[value] ?? value;
export interface TaskDraft { type: string; description: string; assignee: string }
export const emptyTaskDraft = (): TaskDraft => ({ type: 'DAMAGE_ASSESSMENT', description: '', assignee: '' });
export function taskErrors(draft: TaskDraft): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!Object.hasOwn(TASK_NAMES, draft.type)) errors.type = 'Select a predefined task name.';
  if (draft.description.trim().length > 4000) errors.description = 'Description must be at most 4,000 characters.';
  return errors;
}
export function handoffErrors(draft: TaskDraft, currentAssignee: string, managerOverride = false): Record<string, string> {
  const errors = taskErrors(draft);
  if (!draft.assignee || (!managerOverride && draft.assignee === currentAssignee)) errors.assignee = 'Assign the new task to someone else.';
  return errors;
}
export const isActiveTask = (task: Pick<ClaimTaskDTO, 'status'>) => task.status === 'OPEN' || task.status === 'IN_PROGRESS';
export function filterTasks(tasks: ClaimTaskDTO[], query: string, assignee: string, type: string, status: string) {
  const needle = query.trim().toLowerCase();
  return tasks.filter((task) => (!needle || `${task.id} ${task.title} ${task.description}`.toLowerCase().includes(needle))
    && (!assignee || task.assignee === assignee) && (!type || task.type === type) && (!status || task.status === status));
}
