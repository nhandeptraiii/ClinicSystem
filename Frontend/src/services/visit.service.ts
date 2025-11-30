import { http } from './http';
import type { Prescription } from './prescription.service';

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
    gender?: string | null;
    dateOfBirth?: string | null;
  } | null;
  primaryAppointment?: {
    id: number;
    scheduledAt?: string;
    status?: string;
    reason?: string | null;
    notes?: string | null;
    doctor?: {
      id: number;
      specialty?: string | null;
      account?: {
        id: number;
        fullName?: string | null;
        username?: string | null;
        email?: string | null;
      } | null;
    } | null;
    clinicRoom?: {
      id: number;
      code?: string;
      name?: string;
    } | null;
  } | null;
  provisionalDiagnosis?: string | null;
  clinicalNote?: string | null;
  status?: string;
  serviceOrders?: ServiceOrder[];
  prescriptions?: Prescription[];
  createdAt?: string;
  updatedAt?: string;
}

export interface PatientVisitPage {
  items: PatientVisit[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ServiceOrder {
  id: number;
  visit?: {
    id: number;
  } | null;
  medicalService?: {
    id: number;
    code?: string;
    name?: string;
  } | null;
  assignedDoctor?: {
    id: number;
    account?: {
      id: number;
      fullName?: string | null;
    } | null;
  } | null;
  performedBy?: {
    id: number;
    account?: {
      id: number;
      fullName?: string | null;
    } | null;
  } | null;
  performedAt?: string | null;
  status?: string;
  note?: string | null;
  resultNote?: string | null;
  indicatorResults?: ServiceOrderResult[];
  createdAt?: string;
  updatedAt?: string;
}

export interface ServiceOrderResult {
  id: number;
  serviceOrder?: {
    id: number;
  } | null;
  indicatorTemplate?: {
    id: number;
    name?: string;
    unit?: string | null;
    normalMin?: number | null;
    normalMax?: number | null;
    criticalMin?: number | null;
    criticalMax?: number | null;
  } | null;
  indicatorNameSnapshot?: string | null;
  unitSnapshot?: string | null;
  measuredValue?: number;
  evaluation?: string;
  note?: string | null;
  recordedAt?: string;
}

export interface ServiceOrderCreatePayload {
  medicalServiceId: number;
  note?: string | null;
}

export interface ServiceOrderResultEntryPayload {
  indicatorId: number;
  value: number;
  note?: string | null;
}

export interface ServiceOrderResultPayload {
  performedById?: number | null;
  performedAt?: string | null;
  overallConclusion?: string | null;
  indicators: ServiceOrderResultEntryPayload[];
}

export interface PatientVisitStatusUpdatePayload {
  status: string;
}

export interface PatientVisitUpdatePayload {
  provisionalDiagnosis?: string | null;
  clinicalNote?: string | null;
}

export const fetchVisits = async (params?: { patientId?: number }): Promise<PatientVisit[]> => {
  const { data } = await http.get<RestResponse<PatientVisit[]> | PatientVisit[]>('/visits', { params });
  const unwrapped = normalizeResponse(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const createVisit = async (payload: PatientVisitCreatePayload): Promise<PatientVisit> => {
  const { data } = await http.post<RestResponse<PatientVisit>>('/visits', payload);
  return normalizeResponse(data);
};

export const fetchVisitById = async (id: number): Promise<PatientVisit> => {
  const { data } = await http.get<RestResponse<PatientVisit> | PatientVisit>(`/visits/${id}`);
  return normalizeResponse(data);
};

export const updateVisitStatus = async (
  id: number,
  payload: PatientVisitStatusUpdatePayload,
): Promise<PatientVisit> => {
  const { data } = await http.patch<RestResponse<PatientVisit>>(`/visits/${id}/status`, payload);
  return normalizeResponse(data);
};

export const updateVisitClinicalInfo = async (
  id: number,
  payload: PatientVisitUpdatePayload,
): Promise<PatientVisit> => {
  const { data } = await http.patch<RestResponse<PatientVisit>>(`/visits/${id}`, payload);
  return normalizeResponse(data);
};

export const fetchVisitPage = async (
  params?: {
    keyword?: string;
    status?: string;
    page?: number;
    size?: number;
    patientId?: number;
  },
): Promise<PatientVisitPage> => {
  const { data } = await http.get<RestResponse<PatientVisitPage> | PatientVisitPage | PatientVisit[]>('/visits', {
    params,
  });
  const unwrapped = normalizeResponse(data);
  if (Array.isArray(unwrapped)) {
    const items = unwrapped as PatientVisit[];
    return {
      items,
      page: params?.page ?? 0,
      size: params?.size ?? items.length,
      totalElements: items.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
  }
  const typed = unwrapped as PatientVisitPage;
  return {
    ...typed,
    items: Array.isArray(typed.items) ? typed.items : [],
  };
};

export const fetchCompletedVisitsWithoutBilling = async (
  params?: {
    keyword?: string;
    page?: number;
    size?: number;
  },
): Promise<PatientVisitPage> => {
  const { data } = await http.get<RestResponse<PatientVisitPage> | PatientVisitPage>(
    '/visits/completed-without-billing',
    { params },
  );
  const unwrapped = normalizeResponse(data);
  if (Array.isArray(unwrapped)) {
    const items = unwrapped as PatientVisit[];
    return {
      items,
      page: params?.page ?? 0,
      size: params?.size ?? items.length,
      totalElements: items.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
  }
  const typed = unwrapped as PatientVisitPage;
  return {
    ...typed,
    items: Array.isArray(typed.items) ? typed.items : [],
  };
};

export const fetchServiceOrders = async (visitId: number): Promise<ServiceOrder[]> => {
  const { data } = await http.get<RestResponse<ServiceOrder[]> | ServiceOrder[]>(`/visits/${visitId}/service-orders`);
  const unwrapped = normalizeResponse(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const createServiceOrders = async (
  visitId: number,
  payloads: ServiceOrderCreatePayload[],
): Promise<ServiceOrder[]> => {
  const { data } = await http.post<RestResponse<ServiceOrder[]>>(`/visits/${visitId}/service-orders`, payloads);
  return normalizeResponse(data);
};

export const fetchServiceOrderResults = async (serviceOrderId: number): Promise<ServiceOrderResult[]> => {
  const { data } = await http.get<RestResponse<ServiceOrderResult[]> | ServiceOrderResult[]>(
    `/service-orders/${serviceOrderId}/results`,
  );
  const unwrapped = normalizeResponse(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const recordServiceOrderResults = async (
  serviceOrderId: number,
  payload: ServiceOrderResultPayload,
): Promise<ServiceOrderResult[]> => {
  const { data } = await http.post<RestResponse<ServiceOrderResult[]> | ServiceOrderResult[]>(
    `/service-orders/${serviceOrderId}/results`,
    payload,
  );
  const unwrapped = normalizeResponse(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};
