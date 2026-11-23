import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',
});

export interface ClaimFileDTO {
  id: number;
  claimNumber: string;
  claimType: string;
  status: string;
  claimDate: string;
  announcementDate: string;
  accidentDate: string | null;
  policyType: string;
  policySeries: string;
  policyNumber: string;
  insuredName: string;
  notificationNumber: string;
  externalClaimNumber: string;
  externalNotificationNumber: string;
  claimantName: string;
  insuredVehicleRegistration: string;
  claimantVehicleRegistration: string;
  insuranceCompany: string;
  createdBy: string;
  selectedCharacteristics: string[];
  createdAt: string;
}

export interface ClaimFileDetailDTO {
  summary: ClaimFileDTO;
  claim: Record<string, unknown> | null;
  canEdit: boolean;
}

export interface ClaimStackSummaryDTO {
  claims: ClaimFileDTO[];
  statusCounts: Record<string, number>;
}

export interface ValidationErrorDTO {
  path: string;
  message: string;
}

export interface StubIntegrationResponse {
  status: string;
  message: string;
  data: Record<string, string>;
}

export interface CreateClaimFileRequest<TClaim> {
  idempotencyKey: string;
  claim: TClaim;
}

export const getClaims = async (query = '', status = '') => {
  const { data } = await api.get<ClaimStackSummaryDTO>('/claims', {
    params: { query, status },
  });
  return data;
};

export const createClaimFile = async <TClaim>(request: CreateClaimFileRequest<TClaim>) => {
  const { data } = await api.post<ClaimFileDTO>('/claims', request);
  return data;
};

export const getClaimFile = async (id: string, signal?: AbortSignal) => {
  const { data } = await api.get<ClaimFileDetailDTO>(`/claims/${encodeURIComponent(id)}`, { signal });
  return data;
};

export const updateClaimFile = async <TClaim>(id: number, claim: TClaim) => {
  const { data } = await api.put<ClaimFileDetailDTO>(`/claims/${id}`, { claim });
  return data;
};

export const lookupPolicySnapshot = async (policySeries: string, policyNumber: string) => {
  const { data } = await api.get<StubIntegrationResponse>('/claims/policy-snapshot', {
    params: { policySeries, policyNumber },
  });
  return data;
};

export const checkAida = async (registrationPlate: string, vin: string) => {
  const { data } = await api.get<StubIntegrationResponse>('/claims/aida-check', {
    params: { registrationPlate, vin },
  });
  return data;
};

export interface ReserveEntryDTO {
  id: number;
  category: string;
  type: string;
  operation: string;
  date: string;
  amount: string;
  currency: string;
  details: string;
  user: string;
  createdAt: string;
  reserveValue: string;
  totalValue: string;
}
export interface ClaimReservesDTO {
  claimId: number;
  claimNumber: string;
  canEdit: boolean;
  revision: number;
  entries: ReserveEntryDTO[];
  balances: { category: string; type: string; amount: string; currency: string }[];
  totals: { category: string; amount: string; currency: string }[];
}
export interface CreateReserveEntryRequest {
  idempotencyKey: string;
  category: string;
  type: string;
  amount: string;
  currency: string;
  details: string;
  expectedRevision: number;
}
export const getClaimReserves = async (id: string, signal?: AbortSignal) => {
  const { data } = await api.get<ClaimReservesDTO>(`/claims/${encodeURIComponent(id)}/reserves`, { signal });
  return data;
};
export const addClaimReserve = async (id: string, request: CreateReserveEntryRequest) => {
  const { data } = await api.post<ClaimReservesDTO>(`/claims/${encodeURIComponent(id)}/reserves`, request);
  return data;
};

export interface ClaimDocumentDTO {
  id: number;
  name: string;
  contentType: string;
  category: 'IMAGE' | 'OTHER';
  size: number;
  tag: string;
  uploadedBy: string;
  uploadedAt: string;
}
export interface ClaimDocumentsDTO {
  claimId: number;
  claimNumber: string;
  canUpload: boolean;
  documents: ClaimDocumentDTO[];
  tags: string[];
  allowedExtensions: string[];
  maxFileSize: number;
}
export const getClaimDocuments = async (claimId: string, signal?: AbortSignal) => {
  const { data } = await api.get<ClaimDocumentsDTO>(`/claims/${encodeURIComponent(claimId)}/documents`, { signal });
  return data;
};
export const uploadClaimDocument = async (claimId: string, file: File, tag: string, key: string, progress: (value: number) => void, signal?: AbortSignal) => {
  const form = new FormData();
  form.append('file', file); form.append('tag', tag); form.append('idempotencyKey', key);
  const { data } = await api.post<ClaimDocumentDTO>(`/claims/${encodeURIComponent(claimId)}/documents`, form, {
    signal,
    onUploadProgress: (event) => progress(Math.min(99, Math.round(event.loaded * 100 / (event.total || file.size || 1)))),
  });
  return data;
};
export const claimDocumentPreviewUrl = (claimId: string, id: number) => `${api.defaults.baseURL}/claims/${encodeURIComponent(claimId)}/documents/${id}/preview`;
export const downloadClaimDocument = async (claimId: string, id: number) => {
  const { data } = await api.get<Blob>(`/claims/${encodeURIComponent(claimId)}/documents/${id}/download`, { responseType: 'blob' });
  return data;
};

export interface ClaimTaskDTO {
  id: number; title: string; type: string; description: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  assignee: string; assigneeName: string; dueDate: string | null;
  createdAt: string; createdBy: string; automaticAssignment: boolean; revision: number;
  assignedBy: string; assignedAt: string; closedAt: string | null;
  predecessorTaskId: number | null; closureReason: string | null; canWork: boolean; canEditDescription: boolean; canReplace: boolean;
}
export interface ClaimTasksDTO {
  claimId: number; claimNumber: string; claimStatus: string; canManage: boolean;
  currentActorUsername: string; currentActorName: string; finalTaskType: string;
  adjusters: { username: string; name: string }[];
  tasks: ClaimTaskDTO[];
  activities: { id: number; taskId: number; details: string; actor: string; createdAt: string }[];
  taskTypes: string[];
}
export interface CreateClaimTaskRequest {
  idempotencyKey: string; type: string; description: string; assignee: string; currentTaskId: number; expectedRevision: number;
}
export const getClaimTasks = async (claimId: string, signal?: AbortSignal) => {
  const { data } = await api.get<ClaimTasksDTO>(`/claims/${encodeURIComponent(claimId)}/tasks`, { signal });
  return data;
};
export const createClaimTask = async (claimId: string, request: CreateClaimTaskRequest) => {
  const { data } = await api.post<ClaimTasksDTO>(`/claims/${encodeURIComponent(claimId)}/tasks`, request);
  return data;
};
export const updateClaimTask = async (claimId: string, taskId: number, request: { expectedRevision: number; assignee?: string; status?: string; description?: string }) => {
  const { data } = await api.put<ClaimTasksDTO>(`/claims/${encodeURIComponent(claimId)}/tasks/${taskId}`, request);
  return data;
};

export interface HandoffClaimTaskRequest {
  idempotencyKey: string; expectedRevision: number; type: string; description: string; assignee: string;
}
export const handoffClaimTask = async (claimId: string, taskId: number, request: HandoffClaimTaskRequest) => {
  const { data } = await api.post<ClaimTasksDTO>(`/claims/${encodeURIComponent(claimId)}/tasks/${taskId}/handoff`, request);
  return data;
};
export const settleClaimTask = async (claimId: string, taskId: number, expectedRevision: number, finalPaymentConfirmed: boolean) => {
  const { data } = await api.post<ClaimTasksDTO>(`/claims/${encodeURIComponent(claimId)}/tasks/${taskId}/settle`, { expectedRevision, finalPaymentConfirmed });
  return data;
};
