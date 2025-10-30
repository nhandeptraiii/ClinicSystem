import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface IndicatorTemplate {
  id: number;
  code: string;
  name: string;
  unit?: string | null;
  normalMin?: number | null;
  normalMax?: number | null;
  criticalMin?: number | null;
  criticalMax?: number | null;
  referenceNote?: string | null;
  category?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface IndicatorTemplatePayload {
  code: string;
  name: string;
  unit?: string | null;
  normalMin?: number | null;
  normalMax?: number | null;
  criticalMin?: number | null;
  criticalMax?: number | null;
  referenceNote?: string | null;
  category?: string | null;
}

export interface IndicatorTemplatePageResponse {
  items: IndicatorTemplate[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ServiceIndicatorMapping {
  id: number;
  medicalService: {
    id: number;
    code: string;
    name: string;
    price?: number;
  };
  indicatorTemplate: IndicatorTemplate;
  required: boolean;
  displayOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
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

const normalizeTemplate = (template: IndicatorTemplate): IndicatorTemplate => ({
  ...template,
  normalMin: normalizeOptionalNumber(template.normalMin),
  normalMax: normalizeOptionalNumber(template.normalMax),
  criticalMin: normalizeOptionalNumber(template.criticalMin),
  criticalMax: normalizeOptionalNumber(template.criticalMax),
});

export const fetchIndicatorTemplates = async (
  page: number,
  size: number,
  keyword?: string,
) => {
  const { data } = await http.get<
    RestResponse<IndicatorTemplatePageResponse> | IndicatorTemplatePageResponse
  >('/indicator-templates', {
    params: {
      page,
      size,
      keyword: keyword || undefined,
    },
  });
  const unwrapped = unwrap(data);
  return {
    ...unwrapped,
    items: unwrapped.items.map(normalizeTemplate),
  };
};

export const fetchIndicatorTemplateById = async (id: number) => {
  const { data } = await http.get<RestResponse<IndicatorTemplate> | IndicatorTemplate>(
    `/indicator-templates/${id}`,
  );
  return normalizeTemplate(unwrap(data));
};

export const createIndicatorTemplate = async (payload: IndicatorTemplatePayload) => {
  const { data } = await http.post<RestResponse<IndicatorTemplate> | IndicatorTemplate>(
    '/indicator-templates',
    payload,
  );
  return normalizeTemplate(unwrap(data));
};

export const updateIndicatorTemplate = async (
  id: number,
  payload: Partial<IndicatorTemplatePayload>,
) => {
  const { data } = await http.put<RestResponse<IndicatorTemplate> | IndicatorTemplate>(
    `/indicator-templates/${id}`,
    payload,
  );
  return normalizeTemplate(unwrap(data));
};

export const deleteIndicatorTemplate = async (id: number) => {
  await http.delete(`/indicator-templates/${id}`);
};

export const fetchMappingsByTemplateId = async (templateId: number) => {
  const { data } = await http.get<
    RestResponse<ServiceIndicatorMapping[]> | ServiceIndicatorMapping[]
  >(`/indicator-mappings/by-template/${templateId}`);
  return unwrap(data);
};

