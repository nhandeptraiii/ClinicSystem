import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface AppointmentSummary {
  id: number;
  scheduledAt: string;
  status: string;
  createdAt?: string;
}

export interface AppointmentQuery {
  doctorId?: number;
  patientId?: number;
  status?: string;
}

export const fetchAppointments = async (params: AppointmentQuery = {}) => {
  const { data } = await http.get<RestResponse<AppointmentSummary[]>>('/appointments', { params });
  return data?.data ?? [];
};
