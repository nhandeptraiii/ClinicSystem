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

export interface AppointmentSummary {
  id: number;
  scheduledAt: string;
  status: string;
  createdAt?: string;
}

export interface AppointmentDoctorRef {
  id: number;
  specialty?: string | null;
  account?:
    | {
        id: number;
        fullName?: string | null;
      }
    | null;
}

export interface AppointmentPatientRef {
  id: number;
  code?: string | null;
  fullName?: string | null;
  phone?: string | null;
}

export interface AppointmentClinicRoomRef {
  id: number;
  code: string;
  name: string;
}

export interface AppointmentDetail extends AppointmentSummary {
  duration?: number | null;
  reason?: string | null;
  notes?: string | null;
  patient?: AppointmentPatientRef | null;
  doctor?: AppointmentDoctorRef | null;
  clinicRoom?: AppointmentClinicRoomRef | null;
}

export interface AppointmentQuery {
  doctorId?: number;
  patientId?: number;
  status?: string;
}

export interface AppointmentCreatePayload {
  patientId: number;
  doctorId: number;
  scheduledAt: string; // ISO string
  clinicRoomId: number;
  duration?: number;
  reason?: string | null;
  notes?: string | null;
}

export const fetchAppointments = async (params: AppointmentQuery = {}) => {
  const { data } = await http.get<RestResponse<AppointmentDetail[]>>('/appointments', { params });
  if (Array.isArray(data)) {
    return data as AppointmentDetail[];
  }
  const normalized = normalizeResponse(data);
  return Array.isArray(normalized) ? normalized : [];
};

export const createAppointment = async (payload: AppointmentCreatePayload) => {
  const { data } = await http.post<RestResponse<AppointmentDetail>>('/appointments', payload);
  return normalizeResponse(data);
};
