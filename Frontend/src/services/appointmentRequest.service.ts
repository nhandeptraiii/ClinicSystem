import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export type AppointmentLifecycleStatus = 'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED';

// Backward compatibility - map old type to new
export type AppointmentRequestStatus = AppointmentLifecycleStatus;

export interface AppointmentRequestPayload {
  fullName: string;
  phone: string;
  email?: string;
  dateOfBirth?: string; // ISO date string YYYY-MM-DD
  preferredAt?: string; // ISO datetime string YYYY-MM-DDTHH:mm:ss
  symptomDescription?: string;
  recaptchaToken?: string;
}

export interface AppointmentRequest {
  id: number;
  fullName: string;
  phone: string;
  email?: string | null;
  dateOfBirth?: string | null;
  preferredAt?: string | null;
  symptomDescription?: string | null;
  status: AppointmentLifecycleStatus;
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
  keyword?: string;
  page?: number;
  size?: number;
}

export interface AppointmentRequestPage {
  items: AppointmentRequest[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
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

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (!input || typeof input !== 'object') {
    return input as T;
  }
  if ('data' in input && 'statusCode' in input) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const createAppointmentRequest = async (payload: AppointmentRequestPayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest> | AppointmentRequest>('/appointment-requests', payload);
  return unwrap(data);
};

export const fetchAppointmentRequests = async (params: AppointmentRequestQuery = {}): Promise<AppointmentRequest[]> => {
  const response = await fetchAppointmentRequestPage({ ...params, size: 1000 });
  return response.items;
};

export const fetchAppointmentRequestPage = async (
  params: AppointmentRequestQuery = {},
): Promise<AppointmentRequestPage> => {
  const { data } = await http.get<RestResponse<AppointmentRequestPage> | AppointmentRequestPage>(
    '/appointment-requests',
    { params },
  );
  const unwrapped = unwrap(data) as AppointmentRequestPage | AppointmentRequest[];

  if ('items' in unwrapped && 'totalPages' in unwrapped) {
    return unwrapped;
  }

  if (Array.isArray(unwrapped)) {
    return {
      items: unwrapped,
      page: 0,
      size: unwrapped.length,
      totalElements: unwrapped.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
  }

  return {
    items: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
};

export const fetchAppointmentRequestById = async (id: number) => {
  const { data } = await http.get<RestResponse<AppointmentRequest> | AppointmentRequest>(`/appointment-requests/${id}`);
  return unwrap(data);
};

export const approveAppointmentRequest = async (id: number, payload: AppointmentRequestApprovePayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest> | AppointmentRequest>(`/appointment-requests/${id}/approve`, payload);
  return unwrap(data);
};

export const rejectAppointmentRequest = async (id: number, payload: AppointmentRequestRejectPayload) => {
  const { data } = await http.post<RestResponse<AppointmentRequest> | AppointmentRequest>(`/appointment-requests/${id}/reject`, payload);
  return unwrap(data);
};
