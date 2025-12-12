import { http } from './http';
import type { ClinicRoom } from './clinicRoom.service';
import type { IndicatorTemplate } from './indicatorTemplate.service';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface ServiceIndicatorMapping {
  id: number;
  indicatorTemplate: IndicatorTemplate;
  required: boolean;
  displayOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface MedicalService {
  id: number;
  code: string;
  name: string;
  basePrice: number;
  description?: string | null;
  category?: string | null;
  status?: string | null;
  type?: 'CLINICAL' | 'SUB_CLINICAL' | null;
  estimatedDuration?: number | null;
  clinicRoom?: ClinicRoom | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ServiceIndicatorMappingPayload {
  indicatorTemplateId: number;
  required?: boolean;
  displayOrder?: number;
}

export interface MedicalServicePayload {
  code: string;
  name: string;
  basePrice: number;
  clinicRoomId: number;
  description?: string | null;
  category?: string | null;
  status?: string | null;
  type?: 'CLINICAL' | 'SUB_CLINICAL' | null;
  estimatedDuration?: number | null;
}

export interface MedicalServiceQuery {
  keyword?: string;
  category?: string;
  clinicRoomId?: number;
  page?: number;
  size?: number;
}

export interface MedicalServicePage {
  items: MedicalService[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

const normalizeBasePrice = (value: unknown): number => {
  if (typeof value === 'number' && !Number.isNaN(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    if (!Number.isNaN(parsed)) {
      return parsed;
    }
  }
  return 0;
};

const normalizeMedicalServiceData = (input: MedicalService): MedicalService => {
  const rawBasePrice = (input as unknown as { basePrice?: unknown }).basePrice ?? input.basePrice;
  return {
    ...input,
    basePrice: normalizeBasePrice(rawBasePrice),
  };
};

const normalizeMedicalServiceList = (items?: MedicalService[]): MedicalService[] => {
  return (items ?? []).map((item) => normalizeMedicalServiceData(item));
};

const normalizeOptionalNumber = (value: unknown): number | null | undefined => {
  if (value === null || value === undefined) {
    return value as null | undefined;
  }
  if (typeof value === 'number') {
    return Number.isNaN(value) ? null : value;
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    const parsed = Number(trimmed);
    if (!Number.isNaN(parsed)) {
      return parsed;
    }
  }
  return null;
};

const normalizeMappingData = (mapping: ServiceIndicatorMapping): ServiceIndicatorMapping => ({
  ...mapping,
  indicatorTemplate: {
    ...mapping.indicatorTemplate,
    normalMin: normalizeOptionalNumber(mapping.indicatorTemplate.normalMin),
    normalMax: normalizeOptionalNumber(mapping.indicatorTemplate.normalMax),
    criticalMin: normalizeOptionalNumber(mapping.indicatorTemplate.criticalMin),
    criticalMax: normalizeOptionalNumber(mapping.indicatorTemplate.criticalMax),
  },
  required: Boolean(mapping.required ?? true),
  displayOrder: mapping.displayOrder ?? 0,
});

export const fetchMedicalServicePage = async (params: MedicalServiceQuery = {}) => {
  const { data } = await http.get<
    RestResponse<MedicalServicePage> | MedicalServicePage | MedicalService[]
  >('/medical-services', {
    params,
  });
  const unwrapped = unwrap(data);
  if (Array.isArray(unwrapped)) {
    const normalized = normalizeMedicalServiceList(unwrapped as MedicalService[]);
    return {
      items: normalized,
      page: params.page ?? 0,
      size: params.size ?? normalized.length,
      totalElements: normalized.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    } satisfies MedicalServicePage;
  }
  const typed = unwrapped as MedicalServicePage;
  return {
    ...typed,
    items: normalizeMedicalServiceList(typed.items),
  } as MedicalServicePage;
};

export const fetchMedicalServiceById = async (id: number) => {
  const { data } = await http.get<RestResponse<MedicalService> | MedicalService>(
    `/medical-services/${id}`,
  );
  return normalizeMedicalServiceData(unwrap(data));
};

export const createMedicalService = async (payload: MedicalServicePayload) => {
  const { data } = await http.post<RestResponse<MedicalService> | MedicalService>(
    '/medical-services',
    payload,
  );
  return normalizeMedicalServiceData(unwrap(data));
};

export const updateMedicalService = async (id: number, payload: Partial<MedicalServicePayload>) => {
  const { data } = await http.put<RestResponse<MedicalService> | MedicalService>(
    `/medical-services/${id}`,
    payload,
  );
  return normalizeMedicalServiceData(unwrap(data));
};

export const deleteMedicalService = async (id: number) => {
  await http.delete(`/medical-services/${id}`);
};

export const fetchServiceIndicatorMappings = async (serviceId: number) => {
  const { data } = await http.get<
    RestResponse<ServiceIndicatorMapping[]> | ServiceIndicatorMapping[]
  >(`/medical-services/${serviceId}/indicators`);
  const mappings = unwrap(data);
  return Array.isArray(mappings) ? mappings.map((item) => normalizeMappingData(item)) : [];
};

export const createServiceIndicatorMapping = async (
  serviceId: number,
  payload: ServiceIndicatorMappingPayload,
) => {
  const { data } = await http.post<
    RestResponse<ServiceIndicatorMapping> | ServiceIndicatorMapping
  >(`/medical-services/${serviceId}/indicators`, payload);
  return normalizeMappingData(unwrap(data));
};

export const updateServiceIndicatorMapping = async (
  serviceId: number,
  mappingId: number,
  payload: Partial<ServiceIndicatorMappingPayload>,
) => {
  const { data } = await http.put<
    RestResponse<ServiceIndicatorMapping> | ServiceIndicatorMapping
  >(`/medical-services/${serviceId}/indicators/${mappingId}`, payload);
  return normalizeMappingData(unwrap(data));
};

export const deleteServiceIndicatorMapping = async (serviceId: number, mappingId: number) => {
  await http.delete(`/medical-services/${serviceId}/indicators/${mappingId}`);
};
