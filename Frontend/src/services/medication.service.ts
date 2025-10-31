import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface Medication {
  id: number;
  name: string;
  activeIngredient?: string | null;
  strength?: string | null; // Hàm lượng / Concentration
  batchNo: string; // Mã lô
  unit?: string | null;
  unitPrice: number;
  manufacturer: string;
  expiryDate: string; // ISO date string
  stockQuantity: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface MedicationPayload {
  name: string;
  activeIngredient?: string | null;
  strength?: string | null;
  batchNo: string;
  unit?: string | null;
  unitPrice: number;
  manufacturer: string;
  expiryDate: string;
  stockQuantity: number;
}

export interface MedicationQuery {
  keyword?: string;
  page?: number;
  size?: number;
}

export interface MedicationPage {
  items: Medication[];
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

export const fetchMedications = async (keyword?: string) => {
  const params = keyword ? { keyword } : undefined;
  const { data } = await http.get<RestResponse<Medication[]> | Medication[]>('/medications', { params });
  const unwrapped = unwrap(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

export const fetchMedicationPage = async (params: MedicationQuery = {}) => {
  const { data } = await http.get<RestResponse<MedicationPage> | MedicationPage | Medication[]>('/medications', {
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
    } satisfies MedicationPage;
  }
  return unwrapped as MedicationPage;
};

export const fetchMedicationById = async (id: number) => {
  const { data } = await http.get<RestResponse<Medication> | Medication>(`/medications/${id}`);
  return unwrap(data);
};

export const createMedication = async (payload: MedicationPayload) => {
  const { data } = await http.post<RestResponse<Medication> | Medication>('/medications', payload);
  return unwrap(data);
};

export const updateMedication = async (id: number, payload: Partial<MedicationPayload>) => {
  const { data } = await http.put<RestResponse<Medication> | Medication>(`/medications/${id}`, payload);
  return unwrap(data);
};

export const deleteMedication = async (id: number) => {
  await http.delete(`/medications/${id}`);
};

