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

export interface PatientVisitCreatePayload {
  primaryAppointmentId: number;
  provisionalDiagnosis?: string | null;
  clinicalNote?: string | null;
}

export interface PatientVisit {
  id: number;
  patient?: {
    id: number;
    code?: string | null;
    fullName?: string | null;
    phone?: string | null;
  } | null;
  primaryAppointment?: {
    id: number;
    scheduledAt?: string;
    status?: string;
  } | null;
  provisionalDiagnosis?: string | null;
  clinicalNote?: string | null;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const createVisit = async (payload: PatientVisitCreatePayload): Promise<PatientVisit> => {
  const { data } = await http.post<RestResponse<PatientVisit>>('/visits', payload);
  return normalizeResponse(data);
};

export const fetchVisitById = async (id: number): Promise<PatientVisit> => {
  const { data } = await http.get<RestResponse<PatientVisit> | PatientVisit>(`/visits/${id}`);
  return normalizeResponse(data);
};

