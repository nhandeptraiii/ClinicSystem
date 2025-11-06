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

export interface Prescription {
  id: number;
  visit?: {
    id: number;
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

export const fetchPrescriptions = async (visitId?: number): Promise<Prescription[]> => {
  const params = visitId ? { visitId } : undefined;
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

export const deletePrescription = async (id: number): Promise<void> => {
  await http.delete(`/prescriptions/${id}`);
};

