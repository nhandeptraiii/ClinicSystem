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
  status?: 'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED';
  keyword?: string;
  page?: number;
  size?: number;
}

export interface AppointmentPage {
  items: AppointmentDetail[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
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

export interface AppointmentUpdatePayload {
  patientId?: number;
  doctorId?: number;
  clinicRoomId?: number;
  scheduledAt?: string; // ISO string
  duration?: number;
  reason?: string | null;
  notes?: string | null;
}

export interface AppointmentStatusUpdatePayload {
  status: string;
  note?: string | null;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const fetchAppointments = async (params: AppointmentQuery = {}) => {
  const { data } = await http.get<RestResponse<AppointmentDetail[]>>('/appointments', { params });
  if (Array.isArray(data)) {
    return data as AppointmentDetail[];
  }
  const normalized = normalizeResponse(data);
  return Array.isArray(normalized) ? normalized : [];
};

export const fetchAppointmentPage = async (params: AppointmentQuery = {}) => {
  const { data } = await http.get<RestResponse<AppointmentPage> | AppointmentPage | AppointmentDetail[]>('/appointments', {
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
    } satisfies AppointmentPage;
  }
  return unwrapped as AppointmentPage;
};

export const fetchAppointmentById = async (id: number) => {
  const { data } = await http.get<RestResponse<AppointmentDetail> | AppointmentDetail>(`/appointments/${id}`);
  return unwrap(data);
};

export const createAppointment = async (payload: AppointmentCreatePayload) => {
  const { data } = await http.post<RestResponse<AppointmentDetail>>('/appointments', payload);
  return normalizeResponse(data);
};

export const updateAppointment = async (id: number, payload: AppointmentUpdatePayload) => {
  const { data } = await http.put<RestResponse<AppointmentDetail>>(`/appointments/${id}`, payload);
  return normalizeResponse(data);
};

export const updateAppointmentStatus = async (id: number, payload: AppointmentStatusUpdatePayload) => {
  const { data } = await http.patch<RestResponse<AppointmentDetail>>(`/appointments/${id}/status`, payload);
  return normalizeResponse(data);
};

export const deleteAppointment = async (id: number) => {
  await http.delete(`/appointments/${id}`);
};
