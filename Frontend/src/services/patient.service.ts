import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface Patient {
  id: number;
  code: string;
  fullName: string;
  gender?: string | null;
  dateOfBirth?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  note?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface PatientSearchParams {
  keyword?: string;
}

export interface PatientCreatePayload {
  code: string;
  fullName: string;
  gender?: string | null;
  dateOfBirth?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  note?: string | null;
}

const normalizeResponse = <T>(payload: RestResponse<T> | T): T => {
  if (payload && typeof payload === 'object' && 'data' in (payload as RestResponse<T>)) {
    return (payload as RestResponse<T>).data;
  }
  return payload as T;
};

export const searchPatients = async (params: PatientSearchParams = {}) => {
  const { data } = await http.get<RestResponse<Patient[]>>('/patients', {
    params: params.keyword ? { keyword: params.keyword } : undefined,
  });
  if (Array.isArray(data)) {
    return data as Patient[];
  }
  const normalized = normalizeResponse(data);
  return Array.isArray(normalized) ? normalized : [];
};

export const fetchPatientById = async (id: number) => {
  const { data } = await http.get<RestResponse<Patient>>(`/patients/${id}`);
  return normalizeResponse(data);
};

export const createPatient = async (payload: PatientCreatePayload) => {
  const { data } = await http.post<RestResponse<Patient>>('/patients', payload);
  return normalizeResponse(data);
};

