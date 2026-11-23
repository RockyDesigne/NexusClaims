import axios from 'axios';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import ClaimNavigation from '../components/ClaimNavigation';
import { getClaimFile, type ClaimFileDetailDTO } from '../services/api';
import { displayValue, valueAt } from '../domain/claimDetail';
import styles from '../styles/Dashboard.module.css';
import detailStyles from '../styles/ClaimDetail.module.css';

type Fields = Record<string, string>;
const groups: { title: string; base: string; fields: Fields }[] = [
  { title: 'General', base: 'general', fields: {
    claimDate: 'Claim date', policyType: 'Policy type', policySeries: 'Policy series', policyNumber: 'Policy number', notificationNumber: 'Notification number', externalClaimNumber: 'External claim number', externalNotificationNumber: 'External notification number', sharedFault: 'Shared fault', suspiciousClaim: 'Suspicious claim', externalClaim: 'External claim', directSettlement: 'Direct settlement', insuredVehicleAlienated: 'Insured vehicle alienated', uncoveredRisk: 'Uncovered risk', onlineClaim: 'Online claim',
  } },
  { title: 'Policy snapshot', base: 'policySnapshot', fields: {
    issueDate: 'Issue date', ownerData: 'Owner', userData: 'User', vehicleData: 'Vehicle', validFrom: 'Valid from', validTo: 'Valid to', directSettlementEligible: 'Direct settlement eligible', lookupStatus: 'Policy lookup status',
  } },
  { title: 'Additional services', base: 'additionalServices', fields: { audatex: 'Audatex', dat: 'DAT', auction: 'Auction' } },
  { title: 'Event', base: 'event', fields: {
    announcementDate: 'Announcement date', damageAssessmentDate: 'Damage assessment date', damageAssessmentCounty: 'Damage assessment county', accidentDate: 'Accident date', accidentTime: 'Accident time', accidentLocation: 'Accident location', accidentCountry: 'Country', accidentCounty: 'County', accidentCity: 'City', accidentPlace: 'Place', accidentVisibility: 'Visibility', roadCondition: 'Road condition', accidentDocuments: 'Accident documents', amicableSettlementNumber: 'Amicable settlement number', description: 'Description', remarksInspectionReport: 'Remarks / inspection report', additionalInvestigations: 'Additional investigations', numberOfCarsInvolved: 'Number of cars involved', directEmailNoPriorPhone: 'Direct email without prior telephone inquiry', knownEmailOrIntermediary: 'Known email / intermediary', thirdPartyOrAutoServiceNotification: 'Third-party / auto-service notification', compensationRequestedAtNotification: 'Compensation requested at notification', differentCityOrRepairShopAssessment: 'Different city / repair-shop assessment',
  } },
  { title: 'Claimant', base: 'claimant', fields: { type: 'Claimant type', identifier: 'TIN / identifier', firstName: 'First name', lastName: 'Last name', companyName: 'Company name', address: 'Address', phone: 'Phone', email: 'Email' } },
];
const objectFields: Record<string, { branch: string; fields: Fields }> = {
  Vehicle: { branch: 'vehicle', fields: { registrationPlate: 'Registration plate', vin: 'Chassis series / VIN', category: 'Category', make: 'Make', model: 'Model', registrationType: 'Registration type', mileage: 'Mileage', manufactureYear: 'Manufacture year', vehicleColor: 'Color', firstRegistrationDate: 'First registration date', insuredPeriodFrom: 'Insured period from', insuredPeriodTo: 'Insured period to', regress: 'Regress', recoveryPercentage: 'Percentage for recovery', aidaStatus: 'AIDA status' } },
  Injury: { branch: 'injury', fields: { injuredPersonName: 'Injured person name', roleInAccident: 'Role in accident', bodyArea: 'Affected body area', description: 'Injury description', firstTreatmentDate: 'First treatment date', medicalProvider: 'Medical provider / hospital', medicalDocumentReference: 'Medical document reference' } },
  'Other property': { branch: 'property', fields: { category: 'Property category', ownerName: 'Property owner name', location: 'Property address / location', identifier: 'Property identifier / serial number', description: 'Property and damage description' } },
};

export default function ClaimDetail({ claimId, onEdit, successMessage }: { claimId: string; onEdit: (detail: ClaimFileDetailDTO) => void; successMessage: string }) {
  const [detail, setDetail] = useState<ClaimFileDetailDTO | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    getClaimFile(claimId, controller.signal).then((result) => {
      setDetail(result);
    }).catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      setError(axios.isAxiosError(cause) && cause.response?.status === 404
        ? 'Claim file was not found.' : 'Unable to load this claim file. Please try again.');
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [claimId, retry]);

  useEffect(() => { heading.current?.focus(); }, [loading]);

  const summary = detail?.summary;
  const claim = detail?.claim;
  const objectType = valueAt(claim, 'damagedObject.type');
  const object = typeof objectType === 'string' ? objectFields[objectType] : undefined;
  const lines = valueAt(claim, 'damages.lines');

  return (
    <main className={styles.page}>
      <ClaimNavigation claimId={claimId} claimNumber={summary?.claimNumber} active="details" headingRef={heading}>
        <Link className={styles.secondaryButton} to="/">Back to claims stack</Link>
        {detail?.canEdit && <button className={styles.primaryButton} onClick={() => onEdit(detail)}>Edit claim</button>}
      </ClaimNavigation>
      {successMessage && <div className={styles.successMessage} role="status">{successMessage}</div>}
      {loading && <div className={styles.stateBox} role="status">Loading claim file...</div>}
      {error && <div className={styles.errorMessage} role="alert"><p>{error}</p><button className={styles.secondaryButton} onClick={() => { setError(''); setLoading(true); setRetry((current) => current + 1); }}>Retry</button></div>}
      {detail && summary && (
        <>
          <div className={styles.contextChips} aria-label="Claim context"><span>{summary.insuranceCompany}</span><span>{summary.claimType}</span>{summary.selectedCharacteristics.map((item) => <span key={item}>{item}</span>)}</div>
          <section className={styles.formSection}><h2>File overview</h2><dl className={detailStyles.fields}>
            {Object.entries({ 'Claim number': summary.claimNumber, Status: summary.status, 'Claim date': summary.claimDate, 'Announcement date': summary.announcementDate, 'Accident date': summary.accidentDate, 'Policy type': summary.policyType, 'Policy series': summary.policySeries, 'Policy number': summary.policyNumber, 'Notification number': summary.notificationNumber, 'External claim number': summary.externalClaimNumber, 'External notification number': summary.externalNotificationNumber, 'Insured name': summary.insuredName, 'Claimant name': summary.claimantName, 'Insured vehicle registration': summary.insuredVehicleRegistration, 'Claimant vehicle registration': summary.claimantVehicleRegistration, 'Created by': summary.createdBy }).map(([label, value]) => <DetailField key={label} label={label} value={value} />)}
          </dl></section>
          {!claim && <p className={styles.stateBox}>This file contains summary information only. No detailed intake data has been recorded.</p>}
          {claim && <>
            {groups.map((group) => <Section key={group.base} {...group} claim={claim} />)}
            <section className={styles.formSection}><h2>Damaged object — {displayValue(objectType)}</h2>{object ? <dl className={detailStyles.fields}>{Object.entries(object.fields).map(([key, label]) => <DetailField key={key} label={label} value={valueAt(claim, `damagedObject.${object.branch}.${key}`)} />)}</dl> : <p>Not recorded</p>}</section>
            <section className={styles.formSection}><h2>Damages</h2>
              {Array.isArray(lines) && lines.length > 0 ? <div className={styles.tableScroller}><table className={styles.damageTable}><thead><tr><th>Name</th><th>Number</th><th>Damage part</th><th>Operation</th></tr></thead><tbody>{lines.map((line, index) => <tr key={index}>{['name', 'number', 'symbol', 'operation'].map((key) => <td key={key}>{displayValue(valueAt(line, key))}</td>)}</tr>)}</tbody></table></div> : <p>No damage lines recorded.</p>}
              <FieldList claim={claim} base="damages" fields={{ documents: 'Damage documents', accidentType: 'Accident type', damageType: 'Damage type', damageReason: 'Damage reason', solutionMode: 'Solution mode', preexistingDamages: 'Preexisting damages' }} />
            </section>
            <section className={styles.formSection}><div className={styles.sectionHeader}><h2>Valuation and reserve</h2><Link className={styles.secondaryButton} to={`/claims/${claimId}/reserves`}>View reserve history</Link></div><dl className={detailStyles.fields}>
              {Object.entries({ initialReserve: 'Initial reserve', vehicleMarketValue: 'Vehicle market value', wreckedCarValue: 'Wrecked car value', maximumCompensation: 'Maximum compensation' }).map(([key, label]) => {
                const amount = valueAt(claim, `valuation.${key}.amount`);
                const currency = valueAt(claim, `valuation.${key}.currency`);
                return <DetailField key={key} label={label} value={amount === '' || amount === undefined || amount === null ? undefined : `${String(amount)} ${typeof currency === 'string' ? currency : ''}`.trim()} />;
              })}
              <DetailField label="Request date" value={valueAt(claim, 'valuation.requestDate')} />
            </dl></section>
            <Section title="Liability" base="liability" fields={{ guiltyStatus: 'Guilty status / party', damagedVehicleDriver: 'Damaged vehicle driver' }} claim={claim} />
          </>}
        </>
      )}
    </main>
  );
}

function Section({ title, base, fields, claim }: { title: string; base: string; fields: Fields; claim: unknown }) {
  return <section className={styles.formSection}><h2>{title}</h2><FieldList base={base} fields={fields} claim={claim} /></section>;
}
function FieldList({ base, fields, claim }: { base: string; fields: Fields; claim: unknown }) {
  return <dl className={detailStyles.fields}>{Object.entries(fields).map(([key, label]) => <DetailField key={key} label={label} value={valueAt(claim, `${base}.${key}`)} />)}</dl>;
}
function DetailField({ label, value }: { label: string; value: unknown }) {
  return <div><dt>{label}</dt><dd>{displayValue(value)}</dd></div>;
}
