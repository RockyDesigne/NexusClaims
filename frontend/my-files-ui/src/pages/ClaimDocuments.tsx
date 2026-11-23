import axios from 'axios';
import { useEffect, useRef, useState } from 'react';
import ClaimNavigation from '../components/ClaimNavigation';
import { getClaimDocuments, uploadClaimDocument, downloadClaimDocument, claimDocumentPreviewUrl, type ClaimDocumentDTO, type ClaimDocumentsDTO } from '../services/api';
import { MAX_QUEUED_FILES, documentFileError, filterDocuments, formatDocumentSize, type DocumentCategory } from '../domain/documents';
import styles from '../styles/Dashboard.module.css';
import documentStyles from '../styles/ClaimDocuments.module.css';

type QueuedFile = { key: string; file: File; tag: string; status: 'ready' | 'uploading' | 'failed'; progress: number; error: string };
export default function ClaimDocuments({ claimId }: { claimId: string }) {
  const [data, setData] = useState<ClaimDocumentsDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);
  const [tag, setTag] = useState('');
  const [category, setCategory] = useState<DocumentCategory>('ALL');
  const [tagFilter, setTagFilter] = useState('ALL');
  const [queue, setQueue] = useState<QueuedFile[]>([]);
  const [validation, setValidation] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const [success, setSuccess] = useState('');
  const [dragging, setDragging] = useState(false);
  const [downloadError, setDownloadError] = useState('');
  const [downloading, setDownloading] = useState<number[]>([]);
  const input = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const busy = useRef(false);
  const uploadAbort = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getClaimDocuments(claimId, controller.signal).then((result) => {
      if (!controller.signal.aborted) setData(result);
    }).catch((cause: unknown) => {
      if (controller.signal.aborted) return;
      setLoadError(axios.isAxiosError(cause) && cause.response?.status === 404 ? 'Claim file was not found.' : 'Unable to load documents. Please try again.');
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [claimId, retry]);
  useEffect(() => {
    const controller = new AbortController();
    uploadAbort.current = controller;
    return () => controller.abort();
  }, []);
  useEffect(() => { heading.current?.focus(); }, [loading]);

  const addFiles = (files: File[]) => {
    if (!data?.canUpload || busy.current || loading || loadError) return;
    const errors: string[] = [];
    const added: QueuedFile[] = [];
    for (const file of files) {
      const error = documentFileError(file, data.allowedExtensions, data.maxFileSize);
      if (error) { errors.push(`${file.name}: ${error}`); continue; }
      if (queue.length + added.length >= MAX_QUEUED_FILES) { errors.push(`Choose up to ${MAX_QUEUED_FILES} files at a time.`); break; }
      if ([...queue, ...added].some((item) => item.file.name === file.name && item.file.size === file.size && item.file.lastModified === file.lastModified && item.tag === tag)) continue;
      added.push({ key: crypto.randomUUID(), file, tag, status: 'ready', progress: 0, error: '' });
    }
    setQueue((current) => [...current, ...added]); setValidation(errors); setSuccess('');
  };
  const upload = async () => {
    if (!data?.canUpload || busy.current || !queue.length || loading || loadError) return;
    busy.current = true; setUploading(true); setSuccess(''); setValidation([]);
    const signal = uploadAbort.current?.signal;
    let uploaded = 0;
    for (const item of queue) {
      if (signal?.aborted) break;
      setQueue((current) => current.map((row) => row.key === item.key ? { ...row, status: 'uploading', error: '', progress: 0 } : row));
      try {
        const saved = await uploadClaimDocument(claimId, item.file, item.tag, item.key, (progress) => {
          if (!signal?.aborted) setQueue((current) => current.map((row) => row.key === item.key ? { ...row, progress } : row));
        }, signal);
        if (signal?.aborted) break;
        setData((current) => current ? { ...current, documents: [saved, ...current.documents.filter((document) => document.id !== saved.id)] } : current);
        setQueue((current) => current.filter((row) => row.key !== item.key));
        uploaded++;
      } catch (cause) {
        if (signal?.aborted) break;
        let error = 'Upload failed. The file is kept here for retry.';
        if (axios.isAxiosError(cause)) {
          if (cause.response?.status === 403) error = 'You do not have permission to attach documents.';
          else if (cause.response?.status === 413) error = 'File exceeds the attachment size limit.';
          else if (Array.isArray(cause.response?.data)) error = cause.response.data.map((item: { message: string }) => item.message).join(' ');
          else if (cause.response?.status === 404) error = 'The claim file could not be found.';
        }
        setQueue((current) => current.map((row) => row.key === item.key ? { ...row, status: 'failed', error } : row));
      }
    }
    busy.current = false;
    if (!signal?.aborted) {
      setUploading(false);
      if (uploaded) { setSuccess(`${uploaded} document${uploaded === 1 ? '' : 's'} attached to ${data.claimNumber}.`); setCategory('ALL'); setTagFilter('ALL'); }
    }
  };
  const download = async (document: ClaimDocumentDTO) => {
    if (downloading.includes(document.id)) return;
    setDownloading((current) => [...current, document.id]); setDownloadError('');
    try {
      const blob = await downloadClaimDocument(claimId, document.id);
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url; link.download = document.name; link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { setDownloadError(`Unable to download ${document.name}. Please try again.`); }
    finally { setDownloading((current) => current.filter((id) => id !== document.id)); }
  };
  const documents = data?.documents ?? [];
  const visible = filterDocuments(documents, category, tagFilter);
  return (
    <main className={styles.page}>
      <ClaimNavigation claimId={claimId} claimNumber={data?.claimNumber} active="documents" busy={uploading} headingRef={heading} />
      <h2 className={documentStyles.title}>Documents</h2>
      {loading && <div role="status" className={styles.stateBox}>Loading documents...</div>}
      {loadError && <div role="alert" className={styles.errorMessage}>{loadError} <button className={styles.secondaryButton} onClick={() => { setLoading(true); setLoadError(''); setRetry((current) => current + 1); }}>Retry</button></div>}
      {success && <div role="status" className={styles.successMessage}>{success}</div>}
      {data && <>
        {!data.canUpload && <p className={styles.stateBox}>Documents are read-only for your current role. You can browse and download attachments.</p>}
        {data.canUpload && <section aria-label="Attach documents" className={`${documentStyles.dropzone} ${dragging ? documentStyles.dragging : ''}`}
          onDragOver={(event) => { event.preventDefault(); if (!uploading) setDragging(true); }} onDragLeave={() => setDragging(false)}
          onDrop={(event) => { event.preventDefault(); setDragging(false); addFiles(Array.from(event.dataTransfer.files)); }}>
          <div className={documentStyles.tagPicker}><label htmlFor="upload-document-tag">Select an existing tag</label><select id="upload-document-tag" value={tag} disabled={uploading || loading || Boolean(loadError)} onChange={(event) => setTag(event.target.value)}><option value="">No tag</option>{data.tags.map((choice) => <option key={choice}>{choice}</option>)}</select><small>Applies to files added to the queue.</small></div>
          <span aria-hidden="true" className={documentStyles.uploadIcon}>↑</span>
          <p>Drag files here or choose files to attach</p>
          <input ref={input} id="claim-document-files" type="file" multiple accept={data.allowedExtensions.join(',')} aria-label="Files to attach" hidden disabled={uploading || loading || Boolean(loadError)} onChange={(event) => { addFiles(Array.from(event.target.files ?? [])); event.target.value = ''; }} />
          <button type="button" className={styles.primaryButton} disabled={uploading || loading || Boolean(loadError)} onClick={() => input.current?.click()}>Choose files</button>
          <p className={documentStyles.help}>PDF, JPG, PNG, GIF, WebP, text, CSV, Word and Excel. Up to {formatDocumentSize(data.maxFileSize)} per file.</p>
        </section>}
        {validation.length > 0 && <div role="alert" className={styles.errorSummary}><strong>Some files could not be added.</strong><ul>{validation.map((error, index) => <li key={index}>{error}</li>)}</ul></div>}
        {queue.length > 0 && <section className={styles.formSection} aria-label="Files to upload"><h2>Files to upload</h2><ul className={documentStyles.queue}>
          {queue.map((item) => <li key={item.key}><div><strong>{item.file.name}</strong><p>{formatDocumentSize(item.file.size)} · {item.tag || 'Untagged'}</p>{item.status === 'uploading' && <label>Uploading {item.progress}%<progress value={item.progress} max={100} /></label>}{item.error && <p role="alert" className={styles.fieldError}>{item.error}</p>}</div><button className={styles.secondaryButton} disabled={uploading} onClick={() => setQueue((current) => current.filter((row) => row.key !== item.key))} aria-label={`Remove ${item.file.name} from upload queue`}>Remove</button></li>)}
        </ul><div className={styles.actions}><button className={styles.primaryButton} disabled={uploading || loading || Boolean(loadError)} onClick={() => void upload()}>{uploading ? 'Uploading documents...' : queue.every((item) => item.status === 'failed') ? 'Retry uploads' : 'Upload files'}</button></div></section>}
        <section className={styles.formSection} aria-label="Claim documents">
          <div className={documentStyles.filters}><span>Filter by category:</span>{([['ALL', 'All'], ['IMAGE', 'Images'], ['OTHER', 'Other']] as const).map(([value, label]) => <button type="button" key={value} aria-pressed={category === value} className={category === value ? styles.primaryButton : styles.secondaryButton} onClick={() => setCategory(value)}>{label} ({documents.filter((document) => value === 'ALL' || document.category === value).length})</button>)}</div>
          <div className={documentStyles.tagFilter}><label htmlFor="document-tag-filter">Filter by tag</label><select id="document-tag-filter" value={tagFilter} onChange={(event) => setTagFilter(event.target.value)}><option value="ALL">All tags</option><option value="UNTAGGED">Untagged ({documents.filter((document) => !document.tag).length})</option>{data.tags.map((choice) => <option key={choice} value={choice}>{choice} ({documents.filter((document) => document.tag === choice).length})</option>)}</select></div>
          {downloadError && <p role="alert" className={styles.errorMessage}>{downloadError}</p>}
          {documents.length === 0 ? <p className={styles.stateBox}>No documents attached to this claim yet.</p> : visible.length === 0 ? <p className={styles.stateBox}>No documents match the selected filters.</p> : <div className={documentStyles.cards}>{visible.map((document) => <DocumentCard key={document.id} document={document} claimId={claimId} downloading={downloading.includes(document.id)} download={() => void download(document)} />)}</div>}
        </section>
      </>}
    </main>
  );
}
function DocumentCard({ document, claimId, downloading, download }: { document: ClaimDocumentDTO; claimId: string; downloading: boolean; download: () => void }) {
  const [failedPreview, setFailedPreview] = useState(false);
  const [previewAttempt, setPreviewAttempt] = useState(0);
  const [previewLoaded, setPreviewLoaded] = useState(false);
  const previewUrl = `${claimDocumentPreviewUrl(claimId, document.id)}?attempt=${previewAttempt}`;
  return <article className={documentStyles.card} aria-label={document.name}>
    <div className={documentStyles.preview} aria-busy={!previewLoaded && !failedPreview}>{!previewLoaded && !failedPreview && <span className={documentStyles.previewLoading}>Loading preview…</span>}{!failedPreview ? <a href={previewUrl} target="_blank" rel="noopener noreferrer" aria-label={`Open ${document.category === 'IMAGE' ? 'preview' : 'first-page preview'} of ${document.name}`}><img src={previewUrl} alt={`${document.category === 'IMAGE' ? 'Preview' : 'First page'} of ${document.name}`} loading="lazy" onLoad={() => setPreviewLoaded(true)} onError={() => setFailedPreview(true)} /></a> : <div className={documentStyles.previewFailure}><span>Preview unavailable</span><button type="button" className={styles.secondaryButton} onClick={() => { setPreviewAttempt((current) => current + 1); setPreviewLoaded(false); setFailedPreview(false); }} aria-label={`Retry preview of ${document.name}`}>Retry preview</button></div>}</div>
    {document.category !== 'IMAGE' && previewLoaded && <p>Page 1</p>}
    <h3>{document.name}</h3><p>{formatDocumentSize(document.size)}</p><p className={documentStyles.tag}>{document.tag || 'Untagged'}</p>
    <time dateTime={document.uploadedAt}>{new Intl.DateTimeFormat('en-GB', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Bucharest' }).format(new Date(document.uploadedAt))}</time>
    <p>{document.uploadedBy}</p><button className={styles.secondaryButton} disabled={downloading} onClick={download} aria-label={`Download ${document.name}`}>{downloading ? 'Downloading...' : 'Download'}</button>
  </article>;
}
