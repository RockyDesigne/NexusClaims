import axios from 'axios';
import { Link, useMatch, useNavigate } from 'react-router-dom';
import ClaimDetail from './ClaimDetail';
import ClaimReserves from './ClaimReserves';
import ClaimDocuments from './ClaimDocuments';
import ClaimTasks from './ClaimTasks';
import ClaimNavigation from '../components/ClaimNavigation';
import { claimForEditing } from '../domain/claimDetail';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  checkAida,
  createClaimFile,
  updateClaimFile,
  type ClaimFileDetailDTO,
  getClaims,
  lookupPolicySnapshot,
  type ClaimFileDTO,
  type ValidationErrorDTO,
} from '../services/api';
import {
  CHARACTERISTICS,
  CURRENCIES,
  LOCATION_TREE,
  INJURED_PERSON_ROLES,
  PROPERTY_CATEGORIES,
  createInitialClaim,
  sanitizeClaimForSubmit,
  validateClaim,
  type ClaimFormModel,
  type DamageLine,
  type ValidationError,
} from '../domain/claimForm';
import styles from '../styles/Dashboard.module.css';

type ViewMode = 'stack' | 'create' | 'edit';

export default function Dashboard() {
  const claimId = useMatch('/claims/:claimId/*')?.params.claimId;
  const isReserves = Boolean(useMatch('/claims/:claimId/reserves'));
  const isTasks = Boolean(useMatch('/claims/:claimId/tasks'));
  const isDocuments = Boolean(useMatch('/claims/:claimId/documents'));
  const navigate = useNavigate();
  const [editingClaim, setEditingClaim] = useState<ClaimFileDetailDTO | null>(null);
  const stackOpener = useRef<string | null>(claimId ? `claim-link-${claimId}` : null);
  const savingRef = useRef(false);
  const [viewMode, setViewMode] = useState<ViewMode>('stack');
  const [claims, setClaims] = useState<ClaimFileDTO[]>([]);
  const [statusCounts, setStatusCounts] = useState<Record<string, number>>({});
  const [filters, setFilters] = useState(() => savedStackFilters().filters);
  const [appliedFilters, setAppliedFilters] = useState(() => savedStackFilters().appliedFilters);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [isTypeModalOpen, setIsTypeModalOpen] = useState(false);
  const [selectedCharacteristics, setSelectedCharacteristics] = useState<string[]>(['Material damages']);
  const [claimForm, setClaimForm] = useState<ClaimFormModel>(() => createInitialClaim());
  const [validationErrors, setValidationErrors] = useState<ValidationError[]>([]);
  const [saveError, setSaveError] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [damageDraft, setDamageDraft] = useState<DamageLine>(emptyDamageLine());
  const [editingDamageId, setEditingDamageId] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const createButtonRef = useRef<HTMLButtonElement | null>(null);
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);
  const errorSummaryRef = useRef<HTMLDivElement | null>(null);

  const fieldErrors = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const error of validationErrors) {
      map.set(error.path, [...(map.get(error.path) ?? []), error.message]);
    }
    return map;
  }, [validationErrors]);

  const statusFilters = useMemo(() => ['All', ...Object.keys(statusCounts)], [statusCounts]);

  const fetchClaims = useCallback(async () => {
    setIsLoading(true);
    setLoadError('');
    try {
      const summary = await getClaims(appliedFilters.query, appliedFilters.status);
      setClaims(summary.claims);
      setStatusCounts(summary.statusCounts);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : 'Unable to load claims.');
    } finally {
      setIsLoading(false);
    }
  }, [appliedFilters.query, appliedFilters.status]);

  useEffect(() => {
    if (claimId || viewMode === 'create') return;
    void fetchClaims();
  }, [fetchClaims, viewMode, claimId]);

  useEffect(() => {
    try {
      sessionStorage.setItem('nexus-claims-stack-filters', JSON.stringify({ filters, appliedFilters }));
    } catch { /* Filters still work in memory when storage is unavailable. */ }
  }, [filters, appliedFilters]);

  useEffect(() => {
    if (claimId) {
      stackOpener.current = `claim-link-${claimId}`;
      return;
    }
    if (!stackOpener.current || isLoading) return;
    const frame = requestAnimationFrame(() => {
      const opener = document.getElementById(stackOpener.current ?? '');
      if (opener) {
        opener.focus();
        stackOpener.current = null;
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [claimId, isLoading]);

  useEffect(() => {
    if (!isTypeModalOpen) return;
    closeButtonRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeTypeModal();
      if (event.key === 'Tab') trapModalFocus(event);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isTypeModalOpen]);

  const startCreateForm = () => {
    const hasPrimary = selectedCharacteristics.includes('Material damages') || selectedCharacteristics.includes('Injury');
    if (!hasPrimary) return;
    setClaimForm(createInitialClaim(selectedCharacteristics));
    setValidationErrors([]);
    setSaveError('');
    setSuccessMessage('');
    setIdempotencyKey(crypto.randomUUID());
    setIsTypeModalOpen(false);
    setViewMode('create');
  };

  const closeTypeModal = () => {
    setIsTypeModalOpen(false);
    createButtonRef.current?.focus();
  };

  const setField = (path: string, value: unknown) => {
    setValidationErrors((current) => current.filter((error) => path === 'damagedObject.type'
      ? !error.path.startsWith('damagedObject.')
      : error.path !== path));
    setClaimForm((current) => {
      const next = structuredClone(current) as unknown as Record<string, unknown>;
      const parts = path.split('.');
      let target = next;
      for (const part of parts.slice(0, -1)) target = target[part] as Record<string, unknown>;
      target[parts[parts.length - 1]] = value;
      if (path === 'event.accidentCountry') {
        const event = next.event as Record<string, unknown>;
        event.accidentCounty = '';
        event.accidentCity = '';
      }
      if (path === 'event.accidentCounty') {
        const event = next.event as Record<string, unknown>;
        event.accidentCity = '';
      }
      if (path === 'claimant.type') {
        const claimant = next.claimant as Record<string, unknown>;
        claimant.identifier = '';
        claimant.firstName = '';
        claimant.lastName = '';
        claimant.companyName = '';
      }
      return next as unknown as ClaimFormModel;
    });
  };

  const isEditing = viewMode === 'edit' && !isReserves && !isDocuments && !isTasks && claimId === String(editingClaim?.summary.id);

  const startEditing = (detail: ClaimFileDetailDTO) => {
    if (!detail.canEdit) return;
    setEditingClaim(detail);
    setClaimForm(claimForEditing(detail));
    setValidationErrors([]);
    setSaveError('');
    setSuccessMessage('');
    setViewMode('edit');
    window.scrollTo(0, 0);
  };

  const saveClaim = async () => {
    if (savingRef.current) return;
    const sanitizedClaim = sanitizeClaimForSubmit(claimForm);
    const clientErrors = validateClaim(sanitizedClaim);
    setValidationErrors(clientErrors);
    setSaveError('');

    if (clientErrors.length > 0) {
      queueMicrotask(() => errorSummaryRef.current?.focus());
      return;
    }

    savingRef.current = true;
    setIsSaving(true);
    try {
      if (isEditing && editingClaim) {
        await updateClaimFile(editingClaim.summary.id, sanitizedClaim);
        setSuccessMessage(`Claim ${editingClaim.summary.claimNumber} was updated.`);
        setViewMode('stack');
        setEditingClaim(null);
      } else {
        const createdClaim = await createClaimFile({ idempotencyKey, claim: sanitizedClaim });
        setSuccessMessage(`Claim ${createdClaim.claimNumber} was created with Open status.`);
        setViewMode('stack');
        setAppliedFilters({ query: '', status: 'All' });
        setFilters({ query: '', status: 'All' });
        setIdempotencyKey(crypto.randomUUID());
        navigate('/');
      }
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 403) {
        setSaveError('You do not have permission to edit this claim. Your changes have not been saved.');
      } else if (axios.isAxiosError(error) && Array.isArray(error.response?.data)) {
        setValidationErrors(error.response.data as ValidationErrorDTO[]);
      } else {
        setSaveError(error instanceof Error ? error.message : 'Unable to save claim. Retry is available.');
      }
      queueMicrotask(() => errorSummaryRef.current?.focus());
    } finally {
      savingRef.current = false;
      setIsSaving(false);
    }
  };

  const loadPolicySnapshot = async () => {
    try {
      const response = await lookupPolicySnapshot(claimForm.general.policySeries, claimForm.general.policyNumber);
      setClaimForm((current) => ({
        ...current,
        policySnapshot: {
          issueDate: response.data.issueDate ?? '',
          ownerData: response.data.ownerData ?? '',
          userData: response.data.userData ?? '',
          vehicleData: response.data.vehicleData ?? '',
          validFrom: response.data.validFrom ?? '',
          validTo: response.data.validTo ?? '',
          directSettlementEligible: response.data.directSettlementEligible ?? '',
          lookupStatus: response.message,
        },
      }));
    } catch {
      setClaimForm((current) => ({
        ...current,
        policySnapshot: { ...current.policySnapshot, lookupStatus: 'Policy lookup failed. Manual claim data was preserved.' },
      }));
    }
  };

  const runAidaCheck = async () => {
    const response = await checkAida(claimForm.damagedObject.vehicle.registrationPlate, claimForm.damagedObject.vehicle.vin);
    setField('damagedObject.vehicle.aidaStatus', response.message);
  };

  const saveDamageLine = () => {
    if (editingDamageId) {
      setClaimForm((current) => ({
        ...current,
        damages: {
          ...current.damages,
          lines: current.damages.lines.map((line) => (line.id === editingDamageId ? damageDraft : line)),
        },
      }));
    } else {
      setClaimForm((current) => ({
        ...current,
        damages: { ...current.damages, lines: [...current.damages.lines, { ...damageDraft, id: crypto.randomUUID() }] },
      }));
    }
    setDamageDraft(emptyDamageLine());
    setEditingDamageId(null);
  };

  const editDamageLine = (line: DamageLine) => {
    setDamageDraft(line);
    setEditingDamageId(line.id);
  };

  const deleteDamageLine = (lineId: string) => {
    if (!window.confirm('Delete this damage line?')) return;
    setClaimForm((current) => ({
      ...current,
      damages: { ...current.damages, lines: current.damages.lines.filter((line) => line.id !== lineId) },
    }));
  };

  if (claimId && isTasks) return <ClaimTasks key={claimId} claimId={claimId} />;

  if (claimId && isDocuments) return <ClaimDocuments key={claimId} claimId={claimId} />;

  if (claimId && isReserves) return <ClaimReserves key={claimId} claimId={claimId} />;

  if (claimId && !isEditing) {
    return <ClaimDetail key={claimId} claimId={claimId} onEdit={startEditing} successMessage={successMessage} />;
  }

  if (viewMode === 'create' || isEditing) {
    return (
      <main className={styles.page}>
        {isEditing ? <ClaimNavigation claimId={claimId} claimNumber={editingClaim!.summary.claimNumber} active="details" busy={isSaving}>
          <button type="button" className={styles.secondaryButton} disabled={isSaving} onClick={() => { setViewMode('stack'); setEditingClaim(null); }}>Cancel editing</button>
        </ClaimNavigation> : <header className={styles.header}>
          <div>
            <p className={styles.eyebrow}>{isEditing ? 'Edit claim' : 'Create claim'}</p>
            <h1 className={styles.title}>{isEditing ? editingClaim?.summary.claimNumber : 'New insurance file'}</h1>
          </div>
          <button type="button" className={styles.secondaryButton} disabled={isSaving} onClick={() => { setViewMode('stack'); setEditingClaim(null); }}>
            {isEditing ? 'Cancel editing' : 'Back to claims stack'}
          </button>
        </header>}

        <fieldset className={styles.formBody} disabled={isSaving}>
        <div className={styles.contextChips} aria-label="Claim context">
          <span>{claimForm.context.companyNetwork}</span>
          <span>{claimForm.context.product}</span>
          {claimForm.context.characteristics.map((characteristic) => <span key={characteristic}>{characteristic}</span>)}
        </div>

        {isEditing && !editingClaim?.claim && <p className={styles.stateBox}>This file has summary information only. Complete the missing intake fields before saving.</p>}
        <ErrorSummary errors={validationErrors} saveError={saveError} refElement={errorSummaryRef} />

        <section className={styles.formSection}>
          <h2>General</h2>
          <div className={styles.formGrid}>
            <ReadOnlyField label="Claim number" value={isEditing ? editingClaim!.summary.claimNumber : 'Will be generated'} />
            <InputField label="Claim date" path="general.claimDate" type="date" value={claimForm.general.claimDate} setField={setField} errors={fieldErrors} />
            <ReadOnlyField label="Claim status" value={isEditing ? editingClaim!.summary.status : 'Open'} />
            <ReadOnlyField label="Policy type" value={claimForm.general.policyType} />
            <InputField label="Policy series" path="general.policySeries" value={claimForm.general.policySeries} setField={setField} errors={fieldErrors} />
            <InputField label="Policy number" path="general.policyNumber" value={claimForm.general.policyNumber} setField={setField} errors={fieldErrors} />
            <InputField label="Notification number" path="general.notificationNumber" value={claimForm.general.notificationNumber} setField={setField} errors={fieldErrors} />
            <InputField label="External claim number" path="general.externalClaimNumber" value={claimForm.general.externalClaimNumber} setField={setField} errors={fieldErrors} />
            <InputField label="External notification number" path="general.externalNotificationNumber" value={claimForm.general.externalNotificationNumber} setField={setField} errors={fieldErrors} />
          </div>
          <div className={styles.checkboxGrid}>
            {[
              ['Shared fault', 'general.sharedFault', claimForm.general.sharedFault],
              ['Suspicious claim', 'general.suspiciousClaim', claimForm.general.suspiciousClaim],
              ['External claim', 'general.externalClaim', claimForm.general.externalClaim],
              ['Direct settlement', 'general.directSettlement', claimForm.general.directSettlement],
              ['Insured vehicle alienated', 'general.insuredVehicleAlienated', claimForm.general.insuredVehicleAlienated],
              ['Uncovered risk', 'general.uncoveredRisk', claimForm.general.uncoveredRisk],
              ['Online claim', 'general.onlineClaim', claimForm.general.onlineClaim],
            ].map(([label, path, value]) => (
              <CheckboxField key={String(path)} label={String(label)} path={String(path)} checked={Boolean(value)} setField={setField} />
            ))}
          </div>
        </section>

        <section className={styles.formSection}>
          <div className={styles.sectionHeader}>
            <h2>Policy snapshot</h2>
            <button type="button" className={styles.secondaryButton} onClick={loadPolicySnapshot}>Lookup policy</button>
          </div>
          <p className={styles.stubNotice}>{claimForm.policySnapshot.lookupStatus}</p>
          <div className={styles.formGrid}>
            <ReadOnlyField label="Issue date" value={claimForm.policySnapshot.issueDate || '-'} />
            <ReadOnlyField label="Owner data" value={claimForm.policySnapshot.ownerData || '-'} />
            <ReadOnlyField label="User data" value={claimForm.policySnapshot.userData || '-'} />
            <ReadOnlyField label="Vehicle data" value={claimForm.policySnapshot.vehicleData || '-'} />
            <ReadOnlyField label="Valid from" value={claimForm.policySnapshot.validFrom || '-'} />
            <ReadOnlyField label="Valid to" value={claimForm.policySnapshot.validTo || '-'} />
            <ReadOnlyField label="Direct settlement eligibility" value={claimForm.policySnapshot.directSettlementEligible || '-'} />
          </div>
        </section>

        <section className={styles.formSection}>
          <h2>Additional services</h2>
          <div className={styles.checkboxGrid}>
            <CheckboxField label="Audatex" path="additionalServices.audatex" checked={claimForm.additionalServices.audatex} setField={setField} />
            <CheckboxField label="DAT" path="additionalServices.dat" checked={claimForm.additionalServices.dat} setField={setField} />
            <CheckboxField label="Auction" path="additionalServices.auction" checked={claimForm.additionalServices.auction} setField={setField} />
          </div>
        </section>

        <section className={styles.formSection}>
          <h2>Event</h2>
          <div className={styles.formGrid}>
            <InputField label="Announcement date" path="event.announcementDate" type="date" value={claimForm.event.announcementDate} setField={setField} errors={fieldErrors} />
            <InputField label="Damage assessment date" path="event.damageAssessmentDate" type="date" value={claimForm.event.damageAssessmentDate} setField={setField} errors={fieldErrors} />
            <SelectField label="Damage assessment county" path="event.damageAssessmentCounty" value={claimForm.event.damageAssessmentCounty} options={['', 'Bucharest', 'Cluj', 'Timis']} setField={setField} errors={fieldErrors} />
            <InputField label="Accident date" path="event.accidentDate" type="date" value={claimForm.event.accidentDate} setField={setField} errors={fieldErrors} />
            <InputField label="Accident time" path="event.accidentTime" type="time" value={claimForm.event.accidentTime} setField={setField} errors={fieldErrors} />
            <InputField label="Accident location" path="event.accidentLocation" value={claimForm.event.accidentLocation} setField={setField} errors={fieldErrors} />
            <SelectField label="Accident country" path="event.accidentCountry" value={claimForm.event.accidentCountry} options={['', ...Object.keys(LOCATION_TREE)]} setField={setField} errors={fieldErrors} />
            <SelectField label="Accident county" path="event.accidentCounty" value={claimForm.event.accidentCounty} options={['', ...Object.keys(LOCATION_TREE[claimForm.event.accidentCountry] ?? {})]} setField={setField} errors={fieldErrors} />
            <SelectField label="Accident city" path="event.accidentCity" value={claimForm.event.accidentCity} options={['', ...(LOCATION_TREE[claimForm.event.accidentCountry]?.[claimForm.event.accidentCounty] ?? [])]} setField={setField} errors={fieldErrors} />
            <InputField label="Accident place" path="event.accidentPlace" value={claimForm.event.accidentPlace} setField={setField} errors={fieldErrors} />
            <SelectField label="Accident visibility" path="event.accidentVisibility" value={claimForm.event.accidentVisibility} options={['', 'Good', 'Reduced', 'Night']} setField={setField} errors={fieldErrors} />
            <SelectField label="Road condition" path="event.roadCondition" value={claimForm.event.roadCondition} options={['', 'Dry', 'Wet', 'Snow', 'Ice']} setField={setField} errors={fieldErrors} />
            <SelectField label="Accident documents" path="event.accidentDocuments" value={claimForm.event.accidentDocuments} options={['', 'Amicable settlement', 'Police report', 'Other']} setField={setField} errors={fieldErrors} />
            <InputField label="Amicable settlement number" path="event.amicableSettlementNumber" value={claimForm.event.amicableSettlementNumber} setField={setField} errors={fieldErrors} />
            <InputField label="Additional investigations" path="event.additionalInvestigations" value={claimForm.event.additionalInvestigations} setField={setField} errors={fieldErrors} />
            <InputField label="Number of cars involved" path="event.numberOfCarsInvolved" type="number" value={claimForm.event.numberOfCarsInvolved} setField={setField} errors={fieldErrors} />
            <TextAreaField label="Description" path="event.description" value={claimForm.event.description} setField={setField} errors={fieldErrors} />
            <TextAreaField label="Remarks inspection report" path="event.remarksInspectionReport" value={claimForm.event.remarksInspectionReport} setField={setField} errors={fieldErrors} />
          </div>
          <div className={styles.checkboxGrid}>
            <CheckboxField label="Notified directly by email without prior telephone inquiry" path="event.directEmailNoPriorPhone" checked={claimForm.event.directEmailNoPriorPhone} setField={setField} />
            <CheckboxField label="Known email/intermediary with previous notifications" path="event.knownEmailOrIntermediary" checked={claimForm.event.knownEmailOrIntermediary} setField={setField} />
            <CheckboxField label="Another person, auto service, or entity notified" path="event.thirdPartyOrAutoServiceNotification" checked={claimForm.event.thirdPartyOrAutoServiceNotification} setField={setField} />
            <CheckboxField label="Compensation and payment requested at notification" path="event.compensationRequestedAtNotification" checked={claimForm.event.compensationRequestedAtNotification} setField={setField} />
            <CheckboxField label="Assessment in different city or at repair shop" path="event.differentCityOrRepairShopAssessment" checked={claimForm.event.differentCityOrRepairShopAssessment} setField={setField} />
          </div>
        </section>

        <section className={styles.formSection}>
          <h2>Claimant</h2>
          <div className={styles.formGrid}>
            <SelectField label="Claimant type" path="claimant.type" value={claimForm.claimant.type} options={['ROMANIAN_NATURAL_PERSON', 'FOREIGN_NATURAL_PERSON', 'LEGAL_ENTITY']} setField={setField} errors={fieldErrors} />
            <InputField label={claimantIdentifierLabel(claimForm.claimant.type)} path="claimant.identifier" value={claimForm.claimant.identifier} setField={setField} errors={fieldErrors} />
            {claimForm.claimant.type === 'LEGAL_ENTITY' ? (
              <InputField label="Company name" path="claimant.companyName" value={claimForm.claimant.companyName} setField={setField} errors={fieldErrors} />
            ) : (
              <>
                <InputField label="Last name" path="claimant.lastName" value={claimForm.claimant.lastName} setField={setField} errors={fieldErrors} />
                <InputField label="First name" path="claimant.firstName" value={claimForm.claimant.firstName} setField={setField} errors={fieldErrors} />
              </>
            )}
            <InputField label="Address" path="claimant.address" value={claimForm.claimant.address} setField={setField} errors={fieldErrors} />
            <InputField label="Phone" path="claimant.phone" value={claimForm.claimant.phone} setField={setField} errors={fieldErrors} />
            <InputField label="Email" path="claimant.email" value={claimForm.claimant.email} setField={setField} errors={fieldErrors} />
          </div>
        </section>

        <section className={styles.formSection}>
          <h2>Damaged object</h2>
          <div className={styles.segmented}>
            {(['Vehicle', 'Injury', 'Other property'] as const).map((type) => (
              <button key={type} type="button" aria-pressed={claimForm.damagedObject.type === type} className={claimForm.damagedObject.type === type ? styles.activeSegment : ''} onClick={() => setField('damagedObject.type', type)}>
                {type}
              </button>
            ))}
          </div>
          {claimForm.damagedObject.type === 'Vehicle' && (
            <>
              <div className={styles.formGrid}>
                <InputField label="Registration plate" path="damagedObject.vehicle.registrationPlate" value={claimForm.damagedObject.vehicle.registrationPlate} setField={setField} errors={fieldErrors} />
                <InputField label="Chassis series/VIN" path="damagedObject.vehicle.vin" value={claimForm.damagedObject.vehicle.vin} setField={setField} errors={fieldErrors} />
                <InputField label="Category" path="damagedObject.vehicle.category" value={claimForm.damagedObject.vehicle.category} setField={setField} errors={fieldErrors} />
                <InputField label="Make" path="damagedObject.vehicle.make" value={claimForm.damagedObject.vehicle.make} setField={setField} errors={fieldErrors} />
                <InputField label="Model" path="damagedObject.vehicle.model" value={claimForm.damagedObject.vehicle.model} setField={setField} errors={fieldErrors} />
                <SelectField label="Registration type" path="damagedObject.vehicle.registrationType" value={claimForm.damagedObject.vehicle.registrationType} options={['', 'Permanent', 'Temporary']} setField={setField} errors={fieldErrors} />
                <InputField label="Mileage" path="damagedObject.vehicle.mileage" type="number" value={claimForm.damagedObject.vehicle.mileage} setField={setField} errors={fieldErrors} />
                <InputField label="Manufacture year" path="damagedObject.vehicle.manufactureYear" value={claimForm.damagedObject.vehicle.manufactureYear} setField={setField} errors={fieldErrors} />
                <InputField label="Vehicle color" path="damagedObject.vehicle.vehicleColor" value={claimForm.damagedObject.vehicle.vehicleColor} setField={setField} errors={fieldErrors} />
                <InputField label="First registration date" path="damagedObject.vehicle.firstRegistrationDate" type="date" value={claimForm.damagedObject.vehicle.firstRegistrationDate} setField={setField} errors={fieldErrors} />
                <InputField label="Insured period from" path="damagedObject.vehicle.insuredPeriodFrom" type="date" value={claimForm.damagedObject.vehicle.insuredPeriodFrom} setField={setField} errors={fieldErrors} />
                <InputField label="Insured period to" path="damagedObject.vehicle.insuredPeriodTo" type="date" value={claimForm.damagedObject.vehicle.insuredPeriodTo} setField={setField} errors={fieldErrors} />
                <SelectField label="Regress" path="damagedObject.vehicle.regress" value={claimForm.damagedObject.vehicle.regress} options={['', 'Regress', 'External regress']} setField={setField} errors={fieldErrors} />
                {claimForm.damagedObject.vehicle.regress && <InputField label="Percentage for recovery" path="damagedObject.vehicle.recoveryPercentage" type="number" value={claimForm.damagedObject.vehicle.recoveryPercentage} setField={setField} errors={fieldErrors} />}
              </div>
              <div className={styles.inlineActions}>
                <button type="button" className={styles.secondaryButton} onClick={runAidaCheck}>Check AIDA</button>
                <span className={styles.stubNotice}>{claimForm.damagedObject.vehicle.aidaStatus}</span>
              </div>
            </>
          )}
          {claimForm.damagedObject.type === 'Injury' && (
            <>
              <div className={styles.formGrid}>
                <InputField label="Injured person name" path="damagedObject.injury.injuredPersonName" value={claimForm.damagedObject.injury.injuredPersonName} setField={setField} errors={fieldErrors} />
                <SelectField label="Role in accident" path="damagedObject.injury.roleInAccident" value={claimForm.damagedObject.injury.roleInAccident} options={INJURED_PERSON_ROLES} setField={setField} errors={fieldErrors} />
                <InputField label="Affected body area" path="damagedObject.injury.bodyArea" value={claimForm.damagedObject.injury.bodyArea} setField={setField} errors={fieldErrors} />
                <InputField label="First treatment date" path="damagedObject.injury.firstTreatmentDate" type="date" value={claimForm.damagedObject.injury.firstTreatmentDate} setField={setField} errors={fieldErrors} />
                <InputField label="Medical provider / hospital" path="damagedObject.injury.medicalProvider" value={claimForm.damagedObject.injury.medicalProvider} setField={setField} errors={fieldErrors} />
                <InputField label="Medical document reference" path="damagedObject.injury.medicalDocumentReference" value={claimForm.damagedObject.injury.medicalDocumentReference} setField={setField} errors={fieldErrors} />
              </div>
              <TextAreaField label="Injury description" path="damagedObject.injury.description" value={claimForm.damagedObject.injury.description} setField={setField} errors={fieldErrors} />
            </>
          )}
          {claimForm.damagedObject.type === 'Other property' && (
            <>
              <div className={styles.formGrid}>
                <SelectField label="Property category" path="damagedObject.property.category" value={claimForm.damagedObject.property.category} options={PROPERTY_CATEGORIES} setField={setField} errors={fieldErrors} />
                <InputField label="Property owner name" path="damagedObject.property.ownerName" value={claimForm.damagedObject.property.ownerName} setField={setField} errors={fieldErrors} />
                <InputField label="Property address / location" path="damagedObject.property.location" value={claimForm.damagedObject.property.location} setField={setField} errors={fieldErrors} />
                <InputField label="Property identifier / serial number" path="damagedObject.property.identifier" value={claimForm.damagedObject.property.identifier} setField={setField} errors={fieldErrors} />
              </div>
              <TextAreaField label="Property and damage description" path="damagedObject.property.description" value={claimForm.damagedObject.property.description} setField={setField} errors={fieldErrors} />
            </>
          )}
        </section>

        <section className={styles.formSection}>
          <h2>Damages</h2>
          <div className={styles.formGrid}>
            <InputField label="Name" path="damageDraft.name" value={damageDraft.name} setField={(_, value) => setDamageDraft((current) => ({ ...current, name: String(value) }))} errors={fieldErrors} />
            <InputField label="Number" path="damageDraft.number" value={damageDraft.number} setField={(_, value) => setDamageDraft((current) => ({ ...current, number: String(value) }))} errors={fieldErrors} />
            <InputField label="Damage part" path="damageDraft.symbol" value={damageDraft.symbol} setField={(_, value) => setDamageDraft((current) => ({ ...current, symbol: String(value) }))} errors={fieldErrors} />
            <SelectField label="Operation" path="damageDraft.operation" value={damageDraft.operation} options={['', 'Repair', 'Replace', 'Inspect']} setField={(_, value) => setDamageDraft((current) => ({ ...current, operation: String(value) }))} errors={fieldErrors} />
          </div>
          <button type="button" className={styles.secondaryButton} onClick={saveDamageLine}>{editingDamageId ? 'Save damage' : 'Add damages'}</button>
          {fieldErrors.get('damages.lines')?.map((error) => <p key={error} className={styles.fieldError}>{error}</p>)}
          <div className={styles.tableScroller}>
          <table className={styles.damageTable}>
            <thead><tr><th>Name</th><th>Number</th><th>Damage part</th><th>Operation</th><th>Actions</th></tr></thead>
            <tbody>
              {claimForm.damages.lines.map((line) => (
                <tr key={line.id}>
                  <td>{line.name}</td><td>{line.number}</td><td>{line.symbol}</td><td>{line.operation}</td>
                  <td><button type="button" className={styles.linkButton} onClick={() => editDamageLine(line)}>Edit</button> <button type="button" className={styles.linkButton} onClick={() => deleteDamageLine(line.id)}>Delete</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          <div className={styles.formGrid}>
            <SelectField label="Damage documents" path="damages.documents" value={claimForm.damages.documents} options={['', 'Photos', 'Estimate', 'Police report']} setField={setField} errors={fieldErrors} />
            <InputField label="Accident type" path="damages.accidentType" value={claimForm.damages.accidentType} setField={setField} errors={fieldErrors} />
            <SelectField label="Damage type" path="damages.damageType" value={claimForm.damages.damageType} options={['', 'Vehicle body', 'Mechanical', 'Glass', 'Other']} setField={setField} errors={fieldErrors} />
            <SelectField label="Damage reason" path="damages.damageReason" value={claimForm.damages.damageReason} options={['', 'Collision', 'Road event', 'Other']} setField={setField} errors={fieldErrors} />
            <SelectField label="Solution mode" path="damages.solutionMode" value={claimForm.damages.solutionMode} options={['', 'Repair', 'Compensation', 'Reject']} setField={setField} errors={fieldErrors} />
            <TextAreaField label="Preexisting damages" path="damages.preexistingDamages" value={claimForm.damages.preexistingDamages} setField={setField} errors={fieldErrors} />
          </div>
        </section>

        <section className={styles.formSection}>
          <h2>Valuation and reserve</h2>
          <div className={styles.formGrid}>
            {isEditing ? <div><ReadOnlyField label="Initial reserve" value={`${claimForm.valuation.initialReserve.amount || 'Not recorded'} ${claimForm.valuation.initialReserve.currency}`} /><Link to={`/claims/${claimId}/reserves`}>Change reserve and view history</Link></div> : <MoneyField label="Initial reserve" basePath="valuation.initialReserve" value={claimForm.valuation.initialReserve} setField={setField} errors={fieldErrors} />}
            <MoneyField label="Vehicle market value" basePath="valuation.vehicleMarketValue" value={claimForm.valuation.vehicleMarketValue} setField={setField} errors={fieldErrors} />
            <MoneyField label="Wrecked car value" basePath="valuation.wreckedCarValue" value={claimForm.valuation.wreckedCarValue} setField={setField} errors={fieldErrors} />
            <MoneyField label="Maximum compensation value" basePath="valuation.maximumCompensation" value={claimForm.valuation.maximumCompensation} setField={setField} errors={fieldErrors} />
            <InputField label="Request date" path="valuation.requestDate" type="date" value={claimForm.valuation.requestDate} setField={setField} errors={fieldErrors} />
          </div>
        </section>

        <section className={styles.formSection}>
          <h2>Liability</h2>
          <div className={styles.formGrid}>
            <SelectField label="Guilty status/party" path="liability.guiltyStatus" value={claimForm.liability.guiltyStatus} options={['', 'Insured driver', 'Claimant driver', 'Shared fault', 'Unknown']} setField={setField} errors={fieldErrors} />
            <InputField label="Damaged vehicle driver" path="liability.damagedVehicleDriver" value={claimForm.liability.damagedVehicleDriver} setField={setField} errors={fieldErrors} />
          </div>
        </section>

        </fieldset>
        <div className={styles.saveBar}>
          <ErrorSummary errors={validationErrors} saveError={saveError} refElement={errorSummaryRef} compact />
          <button type="button" className={styles.primaryButton} onClick={saveClaim} disabled={isSaving}>
            {isSaving ? 'Saving claim...' : isEditing ? 'Save changes' : 'Save'}
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div><p className={styles.eyebrow}>Claims stack</p><h1 className={styles.title}>Insurance files</h1></div>
        <div className={styles.actorPanel}><span className={styles.actorLabel}>Current actor</span><strong>Fictional Insurance Director</strong></div>
      </header>
      {successMessage && <div className={styles.successMessage} role="status">{successMessage}</div>}
      <section className={styles.toolbar} aria-label="Claim filters">
        <div className={styles.filterGrid}>
          <InputBare label="Search claims" value={filters.query} onChange={(value) => setFilters((current) => ({ ...current, query: value }))} placeholder="Claim number, insured, claimant, notification, plate" />
          <label className={styles.field}><span id="claims-status-label">Status</span><select aria-labelledby="claims-status-label" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}>{statusFilters.map((status) => <option key={status}>{status}</option>)}</select></label>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.secondaryButton} onClick={() => { setFilters({ query: '', status: 'All' }); setAppliedFilters({ query: '', status: 'All' }); }}>Reset filters</button>
          <button type="button" className={styles.secondaryButton} onClick={() => setAppliedFilters(filters)}>Search</button>
          <button type="button" className={styles.secondaryButton} onClick={() => exportClaims(claims)} disabled={claims.length === 0}>Export</button>
          <button ref={createButtonRef} type="button" className={styles.primaryButton} onClick={() => setIsTypeModalOpen(true)}>Create new claim</button>
        </div>
      </section>
      <section className={styles.statusBar} aria-label="Claim status counts">
        {statusFilters.map((status) => (
          <button key={status} type="button" className={`${styles.statusChip} ${appliedFilters.status === status ? styles.activeChip : ''}`} onClick={() => { const next = { ...filters, status }; setFilters(next); setAppliedFilters(next); }}>
            <span>{status}</span><strong>{status === 'All' ? Object.values(statusCounts).reduce((sum, count) => sum + count, 0) : statusCounts[status]}</strong>
          </button>
        ))}
      </section>
      <section className={styles.tableSection}>
        {isLoading && <div className={styles.stateBox}>Loading claims...</div>}
        {loadError && <div className={styles.errorMessage}>{loadError}</div>}
        {!isLoading && !loadError && claims.length === 0 && <div className={styles.stateBox}>No claim files match the current filters.</div>}
        {!isLoading && !loadError && claims.length > 0 && <ClaimsTable claims={claims} onOpen={(id) => { stackOpener.current = `claim-link-${id}`; setSuccessMessage(''); }} />}
      </section>
      {isTypeModalOpen && (
        <div className={styles.modalBackdrop} role="presentation">
          <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="claim-type-title">
            <div className={styles.modalHeader}>
              <div><p className={styles.eyebrow}>MTPL</p><h2 id="claim-type-title">Select claim type</h2></div>
              <button ref={closeButtonRef} type="button" className={styles.iconButton} onClick={closeTypeModal} aria-label="Close modal">x</button>
            </div>
            <div className={styles.characteristics}>
              {CHARACTERISTICS.map((characteristic) => (
                <label key={characteristic} className={styles.checkboxCard}>
                  <input type="checkbox" checked={selectedCharacteristics.includes(characteristic)} onChange={() => setSelectedCharacteristics((current) => current.includes(characteristic) ? current.filter((value) => value !== characteristic) : [...current, characteristic])} />
                  <span>{characteristic}</span>
                </label>
              ))}
            </div>
            {!selectedCharacteristics.some((value) => value === 'Material damages' || value === 'Injury') && <p className={styles.fieldError}>Select Material damages or Injury before continuing.</p>}
            <div className={styles.modalActions}>
              <button type="button" className={styles.secondaryButton} onClick={closeTypeModal}>Cancel</button>
              <button type="button" className={styles.primaryButton} onClick={startCreateForm}>Save</button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}

function ClaimsTable({ claims, onOpen }: { claims: ClaimFileDTO[]; onOpen: (id: number) => void }) {
  return (
    <div className={styles.tableScroller}>
      <table className={styles.claimTable}>
        <thead><tr><th>Claim number</th><th>Claim type</th><th>Announcement date</th><th>Accident date</th><th>Policy type</th><th>Insured name</th><th>Notification number</th><th>Status</th><th>Claimant name</th><th>Insured vehicle</th><th>Claimant vehicle</th><th>Insurance company</th><th>Created by</th></tr></thead>
        <tbody>{claims.map((claim) => (
          <tr key={claim.id}>
            <td><Link id={`claim-link-${claim.id}`} className={styles.linkButton} to={`/claims/${claim.id}`} onClick={() => onOpen(claim.id)}>{claim.claimNumber}</Link></td>
            <td><div className={styles.typeCell}><span>{claim.claimType}</span><small>{claim.selectedCharacteristics.join(', ')}</small></div></td>
            <td>{formatDate(claim.announcementDate)}</td><td>{formatDate(claim.accidentDate)}</td><td>{claim.policyType}</td><td>{claim.insuredName}</td><td>{claim.notificationNumber}</td>
            <td><span className={styles.statusPill}>{claim.status}</span></td><td>{claim.claimantName || '-'}</td><td>{claim.insuredVehicleRegistration || '-'}</td><td>{claim.claimantVehicleRegistration || '-'}</td><td>{claim.insuranceCompany}</td><td>{claim.createdBy}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

function InputField({ label, path, value, setField, errors, type = 'text' }: FieldProps & { type?: string }) {
  return <label className={styles.field} htmlFor={idFor(path)}><span id={`${idFor(path)}-label`}>{label}</span><input aria-labelledby={`${idFor(path)}-label`} id={idFor(path)} aria-invalid={errors.has(path) || undefined} aria-describedby={errors.has(path) ? `${idFor(path)}-error` : undefined} type={type} value={value} onChange={(event) => setField(path, event.target.value)} /> <FieldErrors path={path} errors={errors} /></label>;
}

function TextAreaField({ label, path, value, setField, errors }: FieldProps) {
  return <label className={styles.field} htmlFor={idFor(path)}><span id={`${idFor(path)}-label`}>{label}</span><textarea aria-labelledby={`${idFor(path)}-label`} aria-invalid={errors.has(path) || undefined} aria-describedby={errors.has(path) ? `${idFor(path)}-error` : undefined} id={idFor(path)} value={value} onChange={(event) => setField(path, event.target.value)} rows={4} /> <FieldErrors path={path} errors={errors} /></label>;
}

function SelectField({ label, path, value, options, setField, errors }: FieldProps & { options: string[] }) {
  return <label className={styles.field} htmlFor={idFor(path)}><span id={`${idFor(path)}-label`}>{label}</span><select aria-labelledby={`${idFor(path)}-label`} aria-invalid={errors.has(path) || undefined} aria-describedby={errors.has(path) ? `${idFor(path)}-error` : undefined} id={idFor(path)} value={value} onChange={(event) => setField(path, event.target.value)}>{options.map((option) => <option key={option} value={option}>{option || 'Choose...'}</option>)}</select><FieldErrors path={path} errors={errors} /></label>;
}

function CheckboxField({ label, path, checked, setField }: { label: string; path: string; checked: boolean; setField: (path: string, value: unknown) => void }) {
  return <label className={styles.checkboxCard}><input type="checkbox" checked={checked} onChange={(event) => setField(path, event.target.checked)} /><span>{label}</span></label>;
}

function MoneyField({ label, basePath, value, setField, errors }: { label: string; basePath: string; value: { amount: string; currency: string }; setField: (path: string, value: unknown) => void; errors: Map<string, string[]> }) {
  return <div className={styles.moneyField}><InputField label={`${label} amount`} path={`${basePath}.amount`} type="number" value={value.amount} setField={setField} errors={errors} /><SelectField label={`${label} currency`} path={`${basePath}.currency`} value={value.currency} options={CURRENCIES} setField={setField} errors={errors} /></div>;
}

function ReadOnlyField({ label, value }: { label: string; value: string }) {
  return <div className={styles.readOnlyField}><span>{label}</span><strong>{value}</strong></div>;
}

function InputBare({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder: string }) {
  return <label className={styles.field}><span>{label}</span><input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} /></label>;
}

function FieldErrors({ path, errors }: { path: string; errors: Map<string, string[]> }) {
  return <>{errors.get(path)?.map((error) => <span key={error} id={`${idFor(path)}-error`} className={styles.fieldError}>{error}</span>)}</>;
}

function ErrorSummary({ errors, saveError, refElement, compact = false }: { errors: ValidationError[]; saveError: string; refElement: React.RefObject<HTMLDivElement | null>; compact?: boolean }) {
  if (errors.length === 0 && !saveError) return null;
  return (
    <div ref={refElement} className={compact ? styles.errorSummaryCompact : styles.errorSummary} tabIndex={-1}>
      <strong>Resolve these issues before saving.</strong>
      {saveError && <p>{saveError}</p>}
      <ul>{errors.map((error, index) => <li key={`${error.path}-${index}`}><a href={`#${idFor(error.path)}`} onClick={(event) => { event.preventDefault(); const input = document.getElementById(idFor(error.path)); input?.focus(); input?.scrollIntoView({ block: 'center' }); }}>{error.message}</a></li>)}</ul>
    </div>
  );
}

interface FieldProps {
  label: string;
  path: string;
  value: string;
  setField: (path: string, value: unknown) => void;
  errors: Map<string, string[]>;
}

function claimantIdentifierLabel(type: ClaimFormModel['claimant']['type']) {
  if (type === 'ROMANIAN_NATURAL_PERSON') return 'CNP';
  if (type === 'LEGAL_ENTITY') return 'CUI/CIF/TIN';
  return 'TIN/foreign identifier';
}

function emptyDamageLine(): DamageLine {
  return { id: '', name: '', number: '', symbol: '', operation: '' };
}

function idFor(path: string) {
  return path.replaceAll('.', '-');
}

function formatDate(value: string | null) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('en-GB').format(new Date(`${value}T00:00:00`));
}

function exportClaims(claims: ClaimFileDTO[]) {
  const header = ['Claim number', 'Claim type', 'Announcement date', 'Accident date', 'Policy type', 'Insured name', 'Notification number', 'Claim status', 'Claimant name', 'Insured vehicle registration', 'Claimant vehicle registration', 'Insurance company', 'Created by'];
  const rows = claims.map((claim) => [claim.claimNumber, claim.claimType, claim.announcementDate, claim.accidentDate ?? '', claim.policyType, claim.insuredName, claim.notificationNumber, claim.status, claim.claimantName, claim.insuredVehicleRegistration, claim.claimantVehicleRegistration, claim.insuranceCompany, claim.createdBy]);
  const csv = [header, ...rows].map((row) => row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'claims-stack.csv';
  link.click();
  window.URL.revokeObjectURL(url);
}

function trapModalFocus(event: KeyboardEvent) {
  const modal = document.querySelector('[role="dialog"]');
  if (!modal) return;
  const focusableElements = Array.from(modal.querySelectorAll<HTMLElement>('button, input, select, textarea, [href], [tabindex]:not([tabindex="-1"])')).filter((element) => !element.hasAttribute('disabled'));
  const firstElement = focusableElements[0];
  const lastElement = focusableElements[focusableElements.length - 1];
  if (!firstElement || !lastElement) return;
  if (event.shiftKey && document.activeElement === firstElement) {
    event.preventDefault();
    lastElement.focus();
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    event.preventDefault();
    firstElement.focus();
  }
}

function savedStackFilters() {
  const defaults = { filters: { query: '', status: 'All' }, appliedFilters: { query: '', status: 'All' } };
  try {
    const saved = JSON.parse(sessionStorage.getItem('nexus-claims-stack-filters') ?? 'null');
    if (saved && [saved.filters, saved.appliedFilters].every((value) =>
      value && typeof value.query === 'string' && typeof value.status === 'string')) return saved as typeof defaults;
  } catch { /* Ignore an unavailable or invalid session snapshot. */ }
  return defaults;
}
