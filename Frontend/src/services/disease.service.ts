import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export interface Disease {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface DiseasePayload {
  code: string;
  name: string;
  description?: string | null;
}

export interface DiseaseQuery {
  keyword?: string;
  page?: number;
  size?: number;
}

export interface DiseasePage {
  items: Disease[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const fetchDiseases = async (keyword?: string) => {
  const params = keyword ? { keyword } : undefined;
  const { data } = await http.get<RestResponse<Disease[]> | Disease[]>('/api/diseases', { params });
  const unwrapped = unwrap(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const fetchDiseasePage = async (params: DiseaseQuery = {}) => {
  const { data } = await http.get<RestResponse<DiseasePage> | DiseasePage | Disease[]>('/api/diseases', {
    params,
  });
  const unwrapped = unwrap(data);
  if (Array.isArray(unwrapped)) {
    const items = unwrapped as Disease[];
    return {
      items,
      page: params.page ?? 0,
      size: params.size ?? items.length,
      totalElements: items.length,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    } satisfies DiseasePage;
  }
  return unwrapped as DiseasePage;
};

export const fetchDiseaseById = async (id: number) => {
  const { data } = await http.get<RestResponse<Disease> | Disease>(`/api/diseases/${id}`);
  return unwrap(data);
};

export const createDisease = async (payload: DiseasePayload) => {
  const { data } = await http.post<RestResponse<Disease> | Disease>('/api/diseases', payload);
  return unwrap(data);
};

export const updateDisease = async (id: number, payload: Partial<DiseasePayload>) => {
  const { data } = await http.put<RestResponse<Disease> | Disease>(`/api/diseases/${id}`, payload);
  return unwrap(data);
};

export const deleteDisease = async (id: number) => {
  await http.delete(`/api/diseases/${id}`);
};
