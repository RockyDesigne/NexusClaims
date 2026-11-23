import axios from 'axios';
import { useEffect, useRef, useState } from 'react';
import ClaimNavigation from '../components/ClaimNavigation';
import { selectedClaim } from '../domain/selectedClaim';
import { addClaimReserve, getClaimReserves, type ClaimReservesDTO } from '../services/api';
import { todayInBusinessZone } from '../domain/claimForm';
import { displayValue } from '../domain/claimDetail';
import { RESERVE_CATEGORIES, RESERVE_TYPES, reserveErrors, reserveTypeLabel, type ReserveDraft } from '../domain/reserves';
import styles from '../styles/Dashboard.module.css';
import reserveStyles from '../styles/ClaimReserves.module.css';

export default function ClaimReserves({ claimId }: { claimId: string }) {
  const [data, setData] = useState<ClaimReservesDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);
  const [draft, setDraft] = useState<ReserveDraft>({ category: 'RESERVE', type: 'MATERIAL_DAMAGE', amount: '', currency: 'RON', details: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState('');
  const key = useRef(crypto.randomUUID());
  const savingRef = useRef(false);
  const initializedDraft = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const summary = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    getClaimReserves(claimId, controller.signal).then((result) => {
      if (controller.signal.aborted) return;
      setData(result);
      if (!initializedDraft.current) {
        const initial = result.entries.find((entry) => entry.operation === 'INITIAL');
        if (initial) setDraft((current) => ({ ...current, type: initial.type, currency: initial.currency }));
        initializedDraft.current = true;
      }
    }).catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      setLoadError(axios.isAxiosError(cause) && cause.response?.status === 404 ? 'Claim file was not found.' : 'Unable to load reserve history. Please try again.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [claimId, retry]);
  useEffect(() => { heading.current?.focus(); }, [loading]);

  const reload = () => { setLoading(true); setLoadError(''); setSaveError(''); setRetry((current) => current + 1); };
  const update = (field: keyof ReserveDraft, value: string) => {
    setDraft((current) => ({ ...current, [field]: value }));
    setErrors((current) => { const next = { ...current }; delete next[field]; return next; });
    key.current = crypto.randomUUID();
    setSuccess('');
  };
  const save = async () => {
    if (savingRef.current || !data?.canEdit || loading || loadError) return;
    const found = reserveErrors(draft);
    setErrors(found); setSaveError(''); setSuccess('');
    if (Object.keys(found).length) { queueMicrotask(() => summary.current?.focus()); return; }
    savingRef.current = true; setSaving(true);
    try {
      const result = await addClaimReserve(claimId, { ...draft, amount: draft.amount.trim(), details: draft.details.trim(), idempotencyKey: key.current, expectedRevision: data.revision });
      setData(result);
      setSuccess(`Reserve change saved for ${result.claimNumber}. History and balances have been updated.`);
      setDraft((current) => ({ ...current, amount: '', details: '' }));
      key.current = crypto.randomUUID();
    } catch (cause) {
      if (axios.isAxiosError(cause) && Array.isArray(cause.response?.data)) {
        setErrors(Object.fromEntries(cause.response.data.map((error: { path: string; message: string }) => [error.path, error.message])));
      } else if (axios.isAxiosError(cause) && cause.response?.status === 403) {
        setSaveError('You do not have permission to change reserves. Your entered values have been preserved.');
      } else if (axios.isAxiosError(cause) && cause.response?.status === 409) {
        setSaveError('Reserve history changed. Reload balances, review the current reserve, then save again. Your entered values have been preserved.');
      } else {
        setSaveError('Unable to save this reserve change. Your entered values have been preserved; you can retry.');
      }
      queueMicrotask(() => summary.current?.focus());
    } finally { savingRef.current = false; setSaving(false); }
  };
  const selected = selectedClaim();
  const number = data?.claimNumber ?? (selected?.id === claimId ? selected.number : undefined);
  const fieldError = (field: string) => errors[field] ? <span id={`reserve-${field}-error`} className={styles.fieldError}>{errors[field]}</span> : null;
  const accessibility = (field: string) => ({ 'aria-invalid': Boolean(errors[field]), 'aria-describedby': errors[field] ? `reserve-${field}-error` : undefined });

  return (
    <main className={styles.page}>
      <ClaimNavigation claimId={claimId} claimNumber={number} active="reserves" headingRef={heading} busy={saving} />
      <h2 className={reserveStyles.title}>Reserves</h2>
      {loading && <div role="status" className={styles.stateBox}>Loading reserve history...</div>}
      {loadError && <div role="alert" className={styles.errorMessage}>{loadError} <button className={styles.secondaryButton} onClick={reload}>Retry</button></div>}
      {data && <>
        {success && <div className={styles.successMessage} role="status">{success}</div>}
        {!data.canEdit && <p className={styles.stateBox}>Reserve history is read-only for your current role.</p>}
        {data.canEdit && <section className={styles.formSection} aria-labelledby="reserve-change-title">
          <h2 id="reserve-change-title">Change reserve</h2>
          <p className={reserveStyles.help}>Enter a positive amount to increase the reserve or a negative amount to decrease it. Each change is added to the history.</p>
          <form onSubmit={(event) => { event.preventDefault(); void save(); }}>
            <fieldset disabled={saving || loading || Boolean(loadError)} className={reserveStyles.editor}>
              <div className={styles.field}><label htmlFor="reserve-category">Category</label><select id="reserve-category" value={draft.category} onChange={(event) => update('category', event.target.value)} {...accessibility('category')}>{RESERVE_CATEGORIES.map((choice) => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select>{fieldError('category')}</div>
              <div className={styles.field}><label htmlFor="reserve-type">Type</label><select id="reserve-type" value={draft.type} onChange={(event) => update('type', event.target.value)} {...accessibility('type')}>{RESERVE_TYPES.map((choice) => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select>{fieldError('type')}</div>
              <div className={styles.field}><label htmlFor="reserve-date">Date</label><input id="reserve-date" type="date" value={todayInBusinessZone()} readOnly /></div>
              <div className={styles.field}><label htmlFor="reserve-amount">Amount change</label><input id="reserve-amount" type="text" inputMode="text" placeholder="e.g. 500.00 or -250.00" value={draft.amount} onChange={(event) => update('amount', event.target.value)} {...accessibility('amount')} />{fieldError('amount')}</div>
              <div className={styles.field}><label htmlFor="reserve-currency">Currency</label><select id="reserve-currency" value={draft.currency} onChange={(event) => update('currency', event.target.value)} {...accessibility('currency')}>{['RON', 'EUR', 'USD'].map((currency) => <option key={currency}>{currency}</option>)}</select>{fieldError('currency')}</div>
              <div className={styles.field}><label htmlFor="reserve-details">Details / reason</label><textarea id="reserve-details" rows={2} value={draft.details} onChange={(event) => update('details', event.target.value)} {...accessibility('details')} />{fieldError('details')}</div>
            </fieldset>
            {(Object.keys(errors).length > 0 || saveError) && <div className={styles.errorSummary} ref={summary} tabIndex={-1} role="alert"><strong>Reserve change was not saved.</strong>{saveError && <p>{saveError}</p>}<ul>{Object.entries(errors).map(([field, message]) => <li key={field}><a href={`#reserve-${field}`} onClick={(event) => { event.preventDefault(); const input = document.getElementById(`reserve-${field}`); input?.focus(); input?.scrollIntoView({ block: 'center' }); }}>{message}</a></li>)}</ul><button type="button" className={styles.secondaryButton} disabled={saving || loading} onClick={reload}>Reload balances</button></div>}
            <div className={styles.actions}><button className={styles.primaryButton} disabled={saving || loading || Boolean(loadError)}>{saving ? 'Saving reserve...' : 'Save reserve change'}</button></div>
          </form>
        </section>}
        {RESERVE_CATEGORIES.map((category) => <ReserveHistory key={category.value} data={data} category={category.value} title={category.label} />)}
      </>}
    </main>
  );
}

function ReserveHistory({ data, category, title }: { data: ClaimReservesDTO; category: string; title: string }) {
  const entries = data.entries.filter((entry) => entry.category === category);
  const balances = data.balances.filter((balance) => balance.category === category);
  const totals = data.totals.filter((total) => total.category === category);
  return <section className={reserveStyles.ledger} aria-label={`${title} history`}>
    <div className={styles.formSection}><h2>{title} history</h2>
      {entries.length === 0 ? <p className={reserveStyles.help}>No {title.toLowerCase()} entries recorded.</p> : <div className={styles.tableScroller}><table className={styles.damageTable}>
        <thead><tr>{['Type', 'Operation', 'Date', 'Amount', 'Currency', 'Details', 'User', 'Created', 'Reserve value', 'Reserve currency'].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{entries.map((entry) => <tr key={entry.id}><td>{reserveTypeLabel(entry.type)}</td><td>{entry.operation === 'INITIAL' ? 'Initial reserve' : entry.operation === 'INCREASE' ? 'Increase' : 'Decrease'}</td><td>{displayValue(entry.date)}</td><td className={reserveStyles.amount}>{entry.amount.startsWith('-') || entry.operation === 'INITIAL' ? entry.amount : `+${entry.amount}`}</td><td>{entry.currency}</td><td className={reserveStyles.details}>{entry.details}</td><td>{entry.user}</td><td>{new Intl.DateTimeFormat('en-GB', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Bucharest' }).format(new Date(entry.createdAt))}</td><td className={reserveStyles.amount}>{entry.reserveValue}</td><td>{entry.currency}</td></tr>)}</tbody>
      </table></div>}
      <div className={reserveStyles.balances}>{balances.map((balance) => <p key={`${balance.type}-${balance.currency}`}>Total {title.toLowerCase()} — {reserveTypeLabel(balance.type)}: <strong>{balance.amount} {balance.currency}</strong></p>)}</div>
    </div>
    <aside className={styles.formSection} aria-label={`Total ${title.toLowerCase()}`}><h2>Total {title.toLowerCase()}</h2>
      {totals.length ? totals.map((total) => <p key={total.currency} className={reserveStyles.total}>{total.amount} {total.currency}</p>) : <p className={reserveStyles.help}>No balance recorded.</p>}
      {entries.length > 0 && <div className={styles.tableScroller}><table className={styles.damageTable}><thead><tr><th>Date</th><th>Amount</th><th>Currency</th></tr></thead><tbody>{entries.map((entry) => <tr key={entry.id}><td>{displayValue(entry.date)}</td><td>{entry.totalValue}</td><td>{entry.currency}</td></tr>)}</tbody></table></div>}
    </aside>
  </section>;
}
