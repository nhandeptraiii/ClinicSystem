import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

const normalizeResponse = <T>(payload: RestResponse<T> | T): T => {
  if (payload && typeof payload === 'object' && 'data' in (payload as RestResponse<T>)) {
    return (payload as RestResponse<T>).data;
  }
  return payload as T;
};

export interface PrescriptionItem {
  id: number;
  medication?: {
    id: number;
    name?: string;
    unit?: string | null;
  } | null;
  medicationName?: string | null;
  quantity?: number;
  dosage?: string | null;
  frequency?: string | null;
  duration?: string | null;
  instruction?: string | null;
}

export type PrescriptionStatus =
  | 'WAITING'
  | 'DISPENSED'
  | 'ON_HOLD';

export const fetchPrescriptionPdf = async (id: number): Promise<Blob> => {
  const response = await http.get<Blob>(`/prescriptions/${id}/print`, { responseType: 'blob' });
  return response.data;
};

export interface Prescription {
  id: number;
  visit?: {
    id: number;
    patient?: {
      id: number;
      fullName?: string | null;
      code?: string | null;
    } | null;
    primaryAppointment?: {
      clinicRoom?: {
        name?: string | null;
      } | null;
    } | null;
  } | null;
  prescribedBy?: {
    id: number;
    account?: {
      id: number;
      fullName?: string | null;
    } | null;
  } | null;
  issuedAt?: string;
  notes?: string | null;
  status?: PrescriptionStatus | null;
  pharmacistNote?: string | null;
  dispensedAt?: string | null;
  items?: PrescriptionItem[];
  createdAt?: string;
  updatedAt?: string;
}

export interface PrescriptionItemPayload {
  medicationId?: number | null;
  medicationName?: string | null;
  quantity: number;
  dosage: string;
  frequency: string;
  duration?: string | null;
  instruction?: string | null;
}

export interface PrescriptionCreatePayload {
  visitId: number;
  prescribedById?: number | null;
  issuedAt?: string | null;
  notes?: string | null;
  items: PrescriptionItemPayload[];
}

export interface PrescriptionUpdatePayload {
  prescribedById?: number | null;
  clearPrescribedBy?: boolean | null;
  issuedAt?: string | null;
  notes?: string | null;
  items?: PrescriptionItemPayload[];
}

export interface PrescriptionStatusUpdatePayload {
  status: PrescriptionStatus;
  pharmacistNote?: string | null;
}

type PrescriptionQuery =
  | { visitId?: number; status?: PrescriptionStatus }
  | number
  | undefined;

export const fetchPrescriptions = async (query?: PrescriptionQuery): Promise<Prescription[]> => {
  const params =
    typeof query === 'number'
      ? { visitId: query }
      : query && typeof query === 'object'
        ? { visitId: query.visitId, status: query.status }
        : undefined;

  const { data } = await http.get<RestResponse<Prescription[]> | Prescription[]>('/prescriptions', { params });
  const unwrapped = normalizeResponse(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const fetchPrescriptionById = async (id: number): Promise<Prescription> => {
  const { data } = await http.get<RestResponse<Prescription> | Prescription>(`/prescriptions/${id}`);
  return normalizeResponse(data);
};

export const createPrescription = async (payload: PrescriptionCreatePayload): Promise<Prescription> => {
  const { data } = await http.post<RestResponse<Prescription>>('/prescriptions', payload);
  return normalizeResponse(data);
};

export const updatePrescription = async (id: number, payload: PrescriptionUpdatePayload): Promise<Prescription> => {
  const { data } = await http.put<RestResponse<Prescription>>(`/prescriptions/${id}`, payload);
  return normalizeResponse(data);
};

export const updatePrescriptionStatus = async (
  id: number,
  payload: PrescriptionStatusUpdatePayload,
): Promise<Prescription> => {
  const { data } = await http.patch<RestResponse<Prescription>>(`/prescriptions/${id}/status`, payload);
  return normalizeResponse(data);
};

export const deletePrescription = async (id: number): Promise<void> => {
  await http.delete(`/prescriptions/${id}`);
};
