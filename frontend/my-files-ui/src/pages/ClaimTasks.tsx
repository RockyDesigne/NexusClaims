import axios from 'axios';
import { useEffect, useRef, useState } from 'react';
import ClaimNavigation from '../components/ClaimNavigation';
import { createClaimTask, getClaimTasks, updateClaimTask, handoffClaimTask, settleClaimTask, type HandoffClaimTaskRequest, type ClaimTaskDTO, type ClaimTasksDTO } from '../services/api';
import { handoffErrors, isActiveTask, emptyTaskDraft, filterTasks, taskErrors, taskLabel, type TaskDraft } from '../domain/tasks';
import styles from '../styles/Dashboard.module.css';
import taskStyles from '../styles/ClaimTasks.module.css';

const dateTime = (value: string | null | undefined) => value ? new Intl.DateTimeFormat('en-GB', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Bucharest' }).format(new Date(value)) : 'Not recorded';

export default function ClaimTasks({ claimId }: { claimId: string }) {
  const [data, setData] = useState<ClaimTasksDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [reload, setReload] = useState(0);
  const [draft, setDraft] = useState<TaskDraft>(emptyTaskDraft);
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
  const opener = useRef<HTMLButtonElement | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saveError, setSaveError] = useState('');
  const [success, setSuccess] = useState('');
  const [saving, setSaving] = useState(false);
  const [filters, setFilters] = useState({ query: '', assignee: '', type: '', status: '' });
  const key = useRef(crypto.randomUUID());
  const busy = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const summary = useRef<HTMLDivElement>(null);
  const title = useRef<HTMLSelectElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    getClaimTasks(claimId, controller.signal).then((result) => {
      if (!controller.signal.aborted) setData(result);
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted) setLoadError(axios.isAxiosError(cause) && cause.response?.status === 404 ? 'Claim file was not found.' : 'Unable to load tasks. Please try again.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [claimId, reload]);
  useEffect(() => { if (!loading) heading.current?.focus(); }, [loading]);
  useEffect(() => { if (showCreate) title.current?.focus(); }, [showCreate]);

  const refresh = () => { setLoading(true); setLoadError(''); setSaveError(''); setReload((value) => value + 1); };
  const change = (field: keyof TaskDraft, value: string) => {
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => { const next = { ...current }; delete next[field]; return next; });
    key.current = crypto.randomUUID(); setSuccess('');
  };
  const failed = (cause: unknown) => {
    if (axios.isAxiosError(cause) && Array.isArray(cause.response?.data)) {
      setErrors(Object.fromEntries(cause.response.data.map((error: { path: string; message: string }) => [error.path, error.message])));
    } else if (axios.isAxiosError(cause) && cause.response?.status === 403) {
      setSaveError('Task managers can create replacements; only the assigned loss adjuster can confirm final settlement. Your entered values have been preserved.');
    } else if (axios.isAxiosError(cause) && cause.response?.status === 409) {
      const message = typeof cause.response.data === 'string' ? cause.response.data : cause.response.data?.message;
      setSaveError(`${message || 'Tasks changed. Reload tasks and review before retrying.'} Your entered values have been preserved.`);
    } else setSaveError('Unable to save this task change. Your entered values have been preserved; you can retry.');
    queueMicrotask(() => summary.current?.focus());
  };
  const create = async () => {
    if (busy.current || !data?.canManage || loading || loadError) return;
    const found = taskErrors(draft); setErrors(found); setSaveError(''); setSuccess('');
    if (Object.keys(found).length) { queueMicrotask(() => summary.current?.focus()); return; }
    busy.current = true; setSaving(true);
    try {
      const current = data.tasks.find(isActiveTask);
      if (!current) { setSaveError('No current task was found. Reload tasks before continuing.'); return; }
      const result = await createClaimTask(claimId, { ...draft, description: draft.description.trim(), idempotencyKey: key.current, currentTaskId: current.id, expectedRevision: current.revision });
      setData(result); setDraft(emptyTaskDraft()); key.current = crypto.randomUUID(); setShowCreate(false);
      setSelectedTaskId(result.tasks.find(isActiveTask)?.id ?? null);
      setSuccess('New task created and assigned. The previous active task is closed.');
    } catch (cause) { failed(cause); }
    finally { busy.current = false; setSaving(false); }
  };
  const update = async (task: ClaimTaskDTO, changes: { assignee?: string; status?: string; description?: string }) => {
    if (busy.current || !data || loading || loadError) return;
    busy.current = true; setSaving(true); setErrors({}); setSaveError(''); setSuccess('');
    try {
      setData(await updateClaimTask(claimId, task.id, { ...changes, expectedRevision: task.revision }));
      setSuccess(changes.assignee ? `Task #${task.id} reassigned.` : changes.description !== undefined ? `Task #${task.id} description saved.` : `Task #${task.id} state updated.`);
    } catch (cause) { failed(cause); }
    finally { busy.current = false; setSaving(false); }
  };
  const handoff = async (task: ClaimTaskDTO, request: HandoffClaimTaskRequest) => {
    if (busy.current || !task.canReplace || loading || loadError) return;
    busy.current = true; setSaving(true); setErrors({}); setSaveError(''); setSuccess('');
    try {
      const result = await handoffClaimTask(claimId, task.id, request);
      setData(result); setSelectedTaskId(result.tasks.find((next) => next.predecessorTaskId === task.id)?.id ?? task.id);
      setSuccess(`Task #${task.id} closed. Its replacement is now the only active task.`);
    } catch (cause) { failed(cause); }
    finally { busy.current = false; setSaving(false); }
  };
  const settle = async (task: ClaimTaskDTO, confirmed: boolean) => {
    if (busy.current || !task.canWork || loading || loadError) return;
    busy.current = true; setSaving(true); setErrors({}); setSaveError(''); setSuccess('');
    try {
      const result = await settleClaimTask(claimId, task.id, task.revision, confirmed);
      setData(result); setShowCreate(false);
      setSuccess(`Final payment confirmed. Claim ${result.claimNumber} and its final settlement task are closed.`);
    } catch (cause) { failed(cause); }
    finally { busy.current = false; setSaving(false); }
  };
  const fieldError = (field: string) => errors[field] ? <span className={styles.fieldError} id={`task-${field}-error`}>{errors[field]}</span> : null;
  const accessibility = (field: string) => ({ 'aria-invalid': Boolean(errors[field]), 'aria-describedby': errors[field] ? `task-${field}-error` : undefined });
  const filtered = data ? filterTasks(data.tasks, filters.query, filters.assignee, filters.type, filters.status).sort((left, right) => left.id - right.id) : [];
  const unavailable = saving || loading || Boolean(loadError);
  const counts = { OPEN: 0, IN_PROGRESS: 0, COMPLETED: 0, CANCELLED: 0 };
  data?.tasks.forEach((task) => counts[task.status]++);
  const currentTask = data?.tasks.find(isActiveTask);
  const selectedTask = data?.tasks.find((task) => task.id === selectedTaskId);

  return <main className={styles.page}>
    <ClaimNavigation claimId={claimId} claimNumber={data?.claimNumber} active="tasks" busy={saving} headingRef={heading} />
    <div className={styles.sectionHeader}><h2>Tasks</h2>{data?.canManage && <button className={styles.primaryButton} disabled={unavailable} onClick={() => setShowCreate((value) => !value)}>{showCreate ? 'Hide task form' : 'Create task'}</button>}</div>
    {loading && <p role="status" className={styles.stateBox}>Loading tasks...</p>}
    {loadError && <p role="alert" className={styles.errorMessage}>{loadError} <button className={styles.secondaryButton} onClick={refresh}>Retry</button></p>}
    {success && <p role="status" className={styles.successMessage}>{success}</p>}
    {(saveError || Object.keys(errors).length > 0) && <div ref={summary} tabIndex={-1} role="alert" className={styles.errorSummary}><strong>Task change was not saved.</strong>{saveError && <p>{saveError}</p>}<ul>{Object.entries(errors).map(([field, message]) => <li key={field}><a href={`#task-${field}`} onClick={(event) => { event.preventDefault(); document.getElementById(`task-${field}`)?.focus(); }}>{message}</a></li>)}</ul><button className={styles.secondaryButton} disabled={unavailable} onClick={refresh}>Reload tasks</button></div>}
    {data && <>
      <div className={taskStyles.counts} aria-label="Task totals">{(['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'] as const).map((status) => <div key={status}><span>{taskLabel(status)}</span><strong>{counts[status]}</strong></div>)}</div>
      <p className={taskStyles.help}>Current actor: {data.currentActorName} · Claim state: <strong>{data.claimStatus}</strong></p>
      {data.claimStatus.toLowerCase() === 'open' ? <p className={taskStyles.help}>One task can be active at a time, including tasks in progress. Creating its replacement closes the current task. Task managers can take over from the current assignee; final settlement closes the claim.</p> : <p className={styles.stateBox}>This claim is closed. Its task history is read-only.</p>}
      {currentTask && <section className={styles.stateBox} aria-label="Current active task"><strong>Current active task: </strong><button className={taskStyles.taskLink} onClick={(event) => { opener.current = event.currentTarget; setSelectedTaskId(currentTask.id); }}>#{currentTask.id} · {currentTask.title}</button><p>Assigned to {currentTask.assigneeName} · {taskLabel(currentTask.status)}</p></section>}
      {selectedTask && <TaskTicket key={`${selectedTask.id}-${selectedTask.revision}`} task={selectedTask} data={data} disabled={unavailable}
        onUpdate={(changes) => { void update(selectedTask, changes); }} onHandoff={(request) => { void handoff(selectedTask, request); }} onSettle={(confirmed) => { void settle(selectedTask, confirmed); }} onClose={() => { setSelectedTaskId(null); queueMicrotask(() => opener.current?.focus()); }} />}
      {showCreate && data.canManage && <section className={styles.formSection} aria-labelledby="create-task-title"><h2 id="create-task-title">Create replacement task</h2>
        {currentTask && <p className={taskStyles.help}>Saving closes task #{currentTask.id}, currently assigned to {currentTask.assigneeName}, and makes the new task the only active one.</p>}
        <form onSubmit={(event) => { event.preventDefault(); void create(); }}>
          <fieldset className={taskStyles.editor} disabled={unavailable}>
            <div className={styles.field}><label htmlFor="task-type">Task name</label><select id="task-type" ref={title} value={draft.type} onChange={(event) => change('type', event.target.value)} {...accessibility('type')}>{data.taskTypes.map((type) => <option key={type} value={type}>{taskLabel(type)}</option>)}</select>{fieldError('type')}</div>
            <div className={styles.field}><label htmlFor="task-assignee">Assigned loss adjuster</label><select id="task-assignee" value={draft.assignee} onChange={(event) => change('assignee', event.target.value)} {...accessibility('assignee')}><option value="">Automatic — round robin</option>{data.adjusters.map((adjuster) => <option key={adjuster.username} value={adjuster.username}>{adjuster.name}</option>)}</select>{fieldError('assignee')}</div>
            <div className={`${styles.field} ${taskStyles.description}`}><label htmlFor="task-description">Description (optional)</label><textarea id="task-description" rows={3} maxLength={4000} value={draft.description} onChange={(event) => change('description', event.target.value)} {...accessibility('description')} />{fieldError('description')}</div>
          </fieldset>
          <div className={styles.actions}><button className={styles.primaryButton} disabled={unavailable || data.adjusters.length === 0}>{saving ? 'Saving task...' : 'Create task and close current'}</button></div>
          {!data.adjusters.length && <p role="alert">No active loss adjusters are available. Tasks cannot be assigned.</p>}
        </form>
      </section>}
      <section className={styles.formSection} aria-labelledby="claim-tasks-title"><h2 id="claim-tasks-title">Claim tasks</h2>
        <div className={taskStyles.filters}>
          <div className={styles.field}><label htmlFor="tasks-search">Search tasks</label><input id="tasks-search" value={filters.query} onChange={(event) => setFilters((current) => ({ ...current, query: event.target.value }))} placeholder="Task name, description or number" /></div>
          <div className={styles.field}><label htmlFor="tasks-adjuster">Assigned loss adjuster</label><select id="tasks-adjuster" value={filters.assignee} onChange={(event) => setFilters((current) => ({ ...current, assignee: event.target.value }))}><option value="">All adjusters</option>{Array.from(new Map([...data.tasks.map((task) => [task.assignee, task.assigneeName] as const), ...data.adjusters.map((adjuster) => [adjuster.username, adjuster.name] as const)]).entries()).map(([username, name]) => <option key={username} value={username}>{name}</option>)}</select></div>
          <div className={styles.field}><label htmlFor="tasks-type">Task name</label><select id="tasks-type" value={filters.type} onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}><option value="">All types</option>{data.taskTypes.map((type) => <option key={type} value={type}>{taskLabel(type)}</option>)}</select></div>
          <div className={styles.field}><label htmlFor="tasks-status">State</label><select id="tasks-status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}><option value="">All states</option>{['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'].map((status) => <option key={status} value={status}>{taskLabel(status)}</option>)}</select></div>
        </div>
        <div className={taskStyles.resultBar}><p>{filtered.length} of {data.tasks.length} tasks</p><button className={styles.secondaryButton} onClick={() => setFilters({ query: '', assignee: '', type: '', status: '' })}>Reset filters</button></div>
        {!filtered.length ? <p className={styles.stateBox}>{data.tasks.length ? 'No tasks match these filters.' : 'No tasks have been created for this claim.'}</p> : <div className={styles.tableScroller}><table className={`${styles.damageTable} ${taskStyles.table}`}><thead><tr>{['Task name', 'Description', 'Assignee', 'Assigned by', 'Assigned at', 'Closed at', 'State'].map((label) => <th scope="col" key={label}>{label}</th>)}</tr></thead><tbody>{filtered.map((task) => <tr key={task.id}>
          <td className={taskStyles.task}><button className={taskStyles.taskLink} onClick={(event) => { opener.current = event.currentTarget; setSelectedTaskId(task.id); }}>{`#${task.id} · ${task.title}`}</button></td>
          <td className={taskStyles.task}>{task.description || '—'}</td><td>{task.assigneeName}<small>{task.automaticAssignment ? 'Round robin' : 'Manual assignment'}</small></td>
          <td>{task.assignedBy}</td><td>{dateTime(task.assignedAt)}</td><td>{task.closedAt ? dateTime(task.closedAt) : !isActiveTask(task) ? 'Not recorded' : '—'}</td>
          <td><span className={taskStyles.status} data-status={task.status}>{taskLabel(task.status)}</span></td>
        </tr>)}</tbody></table></div>}
      </section>
      <details className={styles.formSection}><summary className={taskStyles.historyTitle}>Task history ({data.activities.length})</summary>{data.activities.length ? <ol className={taskStyles.history}>{data.activities.map((activity) => <li key={activity.id}><strong>Task #{activity.taskId}</strong> · {activity.details}<small>{dateTime(activity.createdAt)} · {activity.actor}</small></li>)}</ol> : <p>No task activity recorded.</p>}</details>
    </>}
  </main>;
}

type TaskChanges = { assignee?: string; status?: string; description?: string };
function TaskTicket({ task, data, disabled, onUpdate, onHandoff, onSettle, onClose }: { task: ClaimTaskDTO; data: ClaimTasksDTO; disabled: boolean; onUpdate: (changes: TaskChanges) => void; onHandoff: (request: HandoffClaimTaskRequest) => void; onSettle: (confirmed: boolean) => void; onClose: () => void }) {
  const [description, setDescription] = useState(task.description);
  const [error, setError] = useState('');
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  const saveDescription = () => {
    if (description.trim().length > 4000) { setError('Description must be at most 4,000 characters.'); return; }
    setError(''); onUpdate({ description: description.trim() });
  };
  return <section className={styles.formSection} aria-labelledby="task-ticket-heading">
    <div className={styles.sectionHeader}><h2 id="task-ticket-heading" tabIndex={-1} ref={heading}>Task #{task.id} · {task.title}</h2><button className={styles.secondaryButton} disabled={disabled} onClick={onClose}>Close task details</button></div>
    <dl className={taskStyles.ticketFields}>
      <div><dt>Task name</dt><dd>{task.title}</dd></div><div><dt>State</dt><dd><span className={taskStyles.status} data-status={task.status}>{taskLabel(task.status)}</span></dd></div>
      <div><dt>Assignee</dt><dd>{task.assigneeName}</dd></div><div><dt>Assigned by</dt><dd>{task.assignedBy}</dd></div>
      <div><dt>Assigned at</dt><dd>{dateTime(task.assignedAt)}</dd></div><div><dt>Closed at</dt><dd>{task.closedAt ? dateTime(task.closedAt) : !isActiveTask(task) ? 'Not recorded' : '—'}</dd></div>
    </dl>
    {task.predecessorTaskId && <p className={taskStyles.help}>Handoff from task #{task.predecessorTaskId}.</p>}
    {task.closureReason && <p className={styles.stateBox}>{task.closureReason}</p>}
    {task.canEditDescription ? <form onSubmit={(event) => { event.preventDefault(); saveDescription(); }}>
      <div className={styles.field}><label htmlFor="task-ticket-description">Description</label><textarea id="task-ticket-description" disabled={disabled} rows={5} maxLength={4000} value={description} onChange={(event) => { setDescription(event.target.value); setError(''); }} aria-invalid={Boolean(error)} aria-describedby={error ? 'task-ticket-description-error' : undefined} /></div>
      {error && <p className={styles.fieldError} id="task-ticket-description-error" role="alert">{error}</p>}
      <div className={styles.actions}><button className={styles.secondaryButton} disabled={disabled || description.trim() === task.description}>Save description</button></div>
    </form> : <div className={taskStyles.readOnlyDescription}><strong>Description</strong><p>{task.description || 'No description.'}</p></div>}
    {task.canEditDescription && description.trim() !== task.description && <p className={taskStyles.help}>Save the description before changing the assignment or state.</p>}
    {isActiveTask(task) && data.claimStatus.toLowerCase() === 'open' && <TaskActions task={task} data={data} disabled={disabled || description.trim() !== task.description} onUpdate={onUpdate} onHandoff={onHandoff} onSettle={onSettle} />}
  </section>;
}

function TaskActions({ task, data, disabled, onUpdate, onHandoff, onSettle }: { task: ClaimTaskDTO; data: ClaimTasksDTO; disabled: boolean; onUpdate: (changes: TaskChanges) => void; onHandoff: (request: HandoffClaimTaskRequest) => void; onSettle: (confirmed: boolean) => void }) {
  const [assignee, setAssignee] = useState(task.assignee);
  const [showHandoff, setShowHandoff] = useState(false);
  const [draft, setDraft] = useState<TaskDraft>({ type: '', description: '', assignee: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [confirmed, setConfirmed] = useState(false);
  const key = useRef(crypto.randomUUID());
  const handoffHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (showHandoff) handoffHeading.current?.focus(); }, [showHandoff]);
  const change = (field: keyof TaskDraft, value: string) => { setDraft((current) => ({ ...current, [field]: value })); setErrors({}); key.current = crypto.randomUUID(); };
  const submitHandoff = () => {
    const found = handoffErrors(draft, task.assignee, data.canManage); setErrors(found);
    if (Object.keys(found).length) { queueMicrotask(() => handoffHeading.current?.focus()); return; }
    onHandoff({ ...draft, description: draft.description.trim(), expectedRevision: task.revision, idempotencyKey: key.current });
  };
  return <>
    {data.canManage && <div className={taskStyles.rowActions}>
      <label htmlFor={`task-${task.id}-assignee`}>Assign task #{task.id}</label><select id={`task-${task.id}-assignee`} disabled={disabled} value={assignee} onChange={(event) => setAssignee(event.target.value)}>{!data.adjusters.some((adjuster) => adjuster.username === task.assignee) && <option value={task.assignee}>{task.assigneeName} (inactive)</option>}{data.adjusters.map((adjuster) => <option key={adjuster.username} value={adjuster.username}>{adjuster.name}</option>)}</select>
      <button className={styles.secondaryButton} disabled={disabled || assignee === task.assignee} onClick={() => onUpdate({ assignee })}>Assign</button>
    </div>}
    {task.canReplace ? <>
      <div className={taskStyles.rowActions}>
        {task.canWork && task.status === 'OPEN' && <button className={styles.secondaryButton} disabled={disabled} onClick={() => onUpdate({ status: 'IN_PROGRESS' })}>Start task</button>}
        <button className={styles.secondaryButton} disabled={disabled} onClick={() => setShowHandoff((value) => !value)}>{showHandoff ? 'Hide handoff form' : data.canManage ? 'Take over and create next task' : 'Create handoff and close task'}</button>
      </div>
      {showHandoff && <section id="task-handoff" className={taskStyles.handoff} aria-labelledby="handoff-title"><h3 id="handoff-title" ref={handoffHeading} tabIndex={-1}>Create the next task</h3><p className={taskStyles.help}>Saving closes the current task and opens its replacement in one operation.</p>
        <form onSubmit={(event) => { event.preventDefault(); submitHandoff(); }}>
          <fieldset disabled={disabled} className={taskStyles.editor}>
            <div className={styles.field}><label htmlFor="handoff-type">Next task name</label><select id="handoff-type" value={draft.type} onChange={(event) => change('type', event.target.value)} aria-invalid={Boolean(errors.type)} aria-describedby={errors.type ? 'handoff-type-error' : undefined}><option value="">Choose a task</option>{data.taskTypes.map((type) => <option value={type} key={type}>{taskLabel(type)}</option>)}</select>{errors.type && <span id="handoff-type-error" className={styles.fieldError}>{errors.type}</span>}</div>
            <div className={styles.field}><label htmlFor="handoff-assignee">Next assignee</label><select id="handoff-assignee" value={draft.assignee} onChange={(event) => change('assignee', event.target.value)} aria-invalid={Boolean(errors.assignee)} aria-describedby={errors.assignee ? 'handoff-assignee-error' : undefined}><option value="">Choose a loss adjuster</option>{data.adjusters.filter((adjuster) => data.canManage || adjuster.username !== task.assignee).map((adjuster) => <option value={adjuster.username} key={adjuster.username}>{adjuster.name}</option>)}</select>{errors.assignee && <span id="handoff-assignee-error" className={styles.fieldError}>{errors.assignee}</span>}</div>
            <div className={`${styles.field} ${taskStyles.description}`}><label htmlFor="handoff-description">Next task description</label><textarea id="handoff-description" rows={3} maxLength={4000} value={draft.description} onChange={(event) => change('description', event.target.value)} aria-invalid={Boolean(errors.description)} aria-describedby={errors.description ? 'handoff-description-error' : undefined} />{errors.description && <span id="handoff-description-error" className={styles.fieldError}>{errors.description}</span>}</div>
          </fieldset><button className={styles.primaryButton} disabled={disabled || !data.adjusters.some((adjuster) => data.canManage || adjuster.username !== task.assignee)}>Assign next task and close this task</button>
          {!data.adjusters.some((adjuster) => data.canManage || adjuster.username !== task.assignee) && <p role="alert">No other active loss adjuster is available for this handoff.</p>}
        </form>
      </section>}
      {task.canWork && task.type === data.finalTaskType && <section id="task-settlement" className={taskStyles.handoff} aria-labelledby="settlement-title"><h3 id="settlement-title">Final settlement</h3><p className={taskStyles.help}>Confirming final payment closes this task and the claim. No further task is created.</p>
        <label className={taskStyles.confirmation}><input type="checkbox" disabled={disabled} checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />Final payment has been completed</label>
        <button className={styles.primaryButton} disabled={disabled || !confirmed} onClick={() => onSettle(confirmed)}>Confirm final payment and close claim</button>
      </section>}
    </> : <p className={taskStyles.help}>The assignee can hand off this task. A task manager can take over and create its replacement.</p>}
  </>;
}
