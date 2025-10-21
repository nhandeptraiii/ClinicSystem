import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

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
  email?: string;
  dateOfBirth?: string;
  preferredAt?: string;
  symptomDescription?: string;
  status: string;
  createdAt?: string;
}

export interface AppointmentRequestQuery {
  status?: string;
}

export const createAppointmentRequest = async (payload: AppointmentRequestPayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest>>('/appointment-requests', payload);
  return data?.data;
};

export const fetchAppointmentRequests = async (params: AppointmentRequestQuery = {}) => {
  const { data } = await http.get<RestResponse<AppointmentRequest[]>>('/appointment-requests', { params });
  return data?.data ?? [];
};
