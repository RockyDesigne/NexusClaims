import { useEffect, type ReactNode, type RefObject } from 'react';
import { Link } from 'react-router-dom';
import styles from '../styles/ClaimNavigation.module.css';
import { selectedClaim } from '../domain/selectedClaim';

export default function ClaimNavigation({ claimId, claimNumber, active, busy = false, headingRef, children }: {
  claimId?: string; claimNumber?: string; active: 'details' | 'reserves' | 'documents' | 'tasks'; busy?: boolean;
  headingRef?: RefObject<HTMLHeadingElement | null>; children?: ReactNode;
}) {
  useEffect(() => {
    if (!claimId || !claimNumber) return;
    try { sessionStorage.setItem('nexus-selected-claim', JSON.stringify({ id: claimId, number: claimNumber })); }
    catch { /* Keep the selected claim visible without persisting the selection. */ }
  }, [claimId, claimNumber]);
  const remembered = selectedClaim();
  const number = claimNumber ?? (remembered && claimId === remembered.id ? remembered.number : undefined);
  const link = (to: string, label: string, current: boolean, available = true) =>
    available && !busy ? <Link to={to} aria-current={current ? 'page' : undefined}>{label}</Link>
      : <span aria-disabled="true" title={busy ? 'Wait until saving finishes.' : 'Open a claim file from the stack first.'}>{label}</span>;
  return (
    <header className={styles.bar}>
      <div className={styles.selection}>
        <span className={styles.label}>Selected claim file</span>
        <h1 ref={headingRef} tabIndex={-1}>{number ?? (claimId ? 'Loading claim...' : 'Select a claim')}</h1>
      </div>
      <nav className={styles.tabs} aria-label="Claim navigation">
        {link('/', 'Claims list', false)}
        {link(`/claims/${claimId}`, 'Claim file', active === 'details', Boolean(claimId))}
        {link(`/claims/${claimId}/reserves`, 'Reserves', active === 'reserves', Boolean(claimId))}
        {link(`/claims/${claimId}/tasks`, 'Tasks', active === 'tasks', Boolean(claimId))}
        {link(`/claims/${claimId}/documents`, 'Documents', active === 'documents', Boolean(claimId))}
      </nav>
      <div className={styles.actions}>{children}</div>
    </header>
  );
}
