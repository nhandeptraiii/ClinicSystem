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

export interface PatientQuery {
  keyword?: string;
  dateOfBirth?: string;
  page?: number;
  size?: number;
}

export interface PatientPage {
  items: Patient[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
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

export interface PatientUpdatePayload {
  code?: string;
  fullName?: string;
  gender?: string | null;
  dateOfBirth?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  note?: string | null;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const fetchPatients = async (keyword?: string) => {
  const params = keyword ? { keyword } : undefined;
  const { data } = await http.get<RestResponse<Patient[]> | Patient[]>('/patients', { params });
  const unwrapped = unwrap(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const searchPatients = async (query: { keyword?: string } = {}) => {
  return fetchPatients(query.keyword);
};

export const fetchPatientPage = async (params: PatientQuery = {}) => {
  const { data } = await http.get<RestResponse<PatientPage> | PatientPage | Patient[]>('/patients', {
    params,
  });
  const unwrapped = unwrap(data);
  if (Array.isArray(unwrapped)) {
    return {
      items: unwrapped,
      page: params.page ?? 0,
      size: params.size ?? unwrapped.length,
      totalElements: unwrapped.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    } satisfies PatientPage;
  }
  return unwrapped as PatientPage;
};

export const fetchPatientById = async (id: number) => {
  const { data } = await http.get<RestResponse<Patient> | Patient>(`/patients/${id}`);
  return unwrap(data);
};

export const createPatient = async (payload: PatientCreatePayload) => {
  const { data } = await http.post<RestResponse<Patient> | Patient>('/patients', payload);
  return unwrap(data);
};

export const updatePatient = async (id: number, payload: PatientUpdatePayload) => {
  const { data } = await http.put<RestResponse<Patient> | Patient>(`/patients/${id}`, payload);
  return unwrap(data);
};

export const deletePatient = async (id: number) => {
  await http.delete(`/patients/${id}`);
};
