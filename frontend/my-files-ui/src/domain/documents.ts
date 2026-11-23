import type { ClaimDocumentDTO } from '../services/api';
export type DocumentCategory = 'ALL' | 'IMAGE' | 'OTHER';
export const MAX_QUEUED_FILES = 20;
export function documentFileError(file: Pick<File, 'name' | 'size'>, allowed: string[], maxSize: number) {
  const hasControl = Array.from(file.name).some((character) => { const code = character.charCodeAt(0); return code < 32 || (code >= 127 && code <= 159); });
  if (!file.name.trim() || file.name.length > 180 || hasControl) return 'Use a filename of 1–180 characters without control characters.';
  const extension = `.${file.name.split('.').pop()?.toLowerCase()}`;
  if (!allowed.includes(extension)) return 'This file format is not supported.';
  if (!file.size) return 'Empty files cannot be attached.';
  if (file.size > maxSize) return `File exceeds the ${formatDocumentSize(maxSize)} limit.`;
  return '';
}
export function filterDocuments(documents: ClaimDocumentDTO[], category: DocumentCategory, tag: string) {
  return documents.filter((document) => (category === 'ALL' || document.category === category)
    && (tag === 'ALL' || (tag === 'UNTAGGED' ? !document.tag : document.tag === tag)));
}
export function formatDocumentSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
