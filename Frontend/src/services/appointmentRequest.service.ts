import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export type AppointmentRequestStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED';

export interface AppointmentRequestPayload {
  fullName: string;
  phone: string;
  email?: string;
  dateOfBirth?: string; // ISO date string YYYY-MM-DD
  preferredAt?: string; // ISO datetime string YYYY-MM-DDTHH:mm:ss
  symptomDescription?: string;
}

export interface AppointmentRequest {
  id: number;
  fullName: string;
  phone: string;
  email?: string | null;
  dateOfBirth?: string | null;
  preferredAt?: string | null;
  symptomDescription?: string | null;
  status: AppointmentRequestStatus;
  staffNote?: string | null;
  createdAt?: string;
  updatedAt?: string;
  processedAt?: string | null;
  patient?:
    | {
        id: number;
        fullName?: string | null;
        code?: string | null;
        phone?: string | null;
      }
    | null;
  processedBy?:
    | {
        id: number;
        fullName?: string | null;
        email?: string | null;
      }
    | null;
}

export interface AppointmentRequestQuery {
  status?: string;
}

export interface AppointmentRequestApprovePayload {
  patientId?: number;
  doctorId: number;
  scheduledAt: string;
  duration: number;
  staffNote?: string;
}

export interface AppointmentRequestRejectPayload {
  staffNote: string;
}

export const createAppointmentRequest = async (payload: AppointmentRequestPayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest>>('/appointment-requests', payload);
  return data?.data;
};

export const fetchAppointmentRequests = async (params: AppointmentRequestQuery = {}) => {
  const { data } = await http.get<RestResponse<AppointmentRequest[]>>('/appointment-requests', { params });
  return data?.data ?? [];
};

export const fetchAppointmentRequestById = async (id: number) => {
  const { data } = await http.get<RestResponse<AppointmentRequest>>(`/appointment-requests/${id}`);
  return data?.data;
};

export const approveAppointmentRequest = async (id: number, payload: AppointmentRequestApprovePayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest>>(`/appointment-requests/${id}/approve`, payload);
  return data?.data;
};

export const rejectAppointmentRequest = async (id: number, payload: AppointmentRequestRejectPayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest>>(`/appointment-requests/${id}/reject`, payload);
  return data?.data;
};
