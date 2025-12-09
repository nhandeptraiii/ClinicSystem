import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

const unwrap = <T>(payload: RestResponse<T> | T): T => {
  if (payload && typeof payload === 'object' && 'data' in (payload as RestResponse<T>)) {
    return (payload as RestResponse<T>).data;
  }
  return payload as T;
};

const toNumber = (value: unknown): number => {
  if (typeof value === 'number') {
    return Number.isNaN(value) ? 0 : value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? 0 : parsed;
  }
  return 0;
};

export type BillingStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export type BillingItemType = 'SERVICE' | 'MEDICATION' | 'OTHER';

export interface BillingItem {
  id: number;
  billing?: {
    id: number;
  } | null;
  itemType: BillingItemType;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  serviceOrderId?: number | null;
  medicalServiceId?: number | null;
  prescriptionItemId?: number | null;
  medicationId?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface Billing {
  id: number;
  visit?: {
    id: number;
    primaryAppointment?: {
      id: number;
      scheduledAt?: string | null;
    } | null;
  } | null;
  patient?: {
    id: number;
    code?: string | null;
    fullName?: string | null;
    phone?: string | null;
  } | null;
  status: BillingStatus;
  serviceTotal: number;
  medicationTotal: number;
  otherTotal: number;
  totalAmount: number;
  paymentMethod?: string | null;
  notes?: string | null;
  issuedAt?: string;
  items?: BillingItem[];
  createdAt?: string;
  updatedAt?: string;
}

export interface BillingPage {
  items: Billing[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface BillingQuery {
  keyword?: string;
  status?: BillingStatus;
  patientId?: number;
  page?: number;
  size?: number;
}

export interface BillingStatusUpdatePayload {
  status: BillingStatus;
  paymentMethod?: string | null;
  notes?: string | null;
}

export interface BillingItemCreatePayload {
  itemType: BillingItemType;
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface BillingItemUpdatePayload {
  itemType?: BillingItemType;
  description?: string;
  quantity?: number;
  unitPrice?: number;
}

const normalizeBillingItem = (item: BillingItem): BillingItem => ({
  ...item,
  quantity: item.quantity ?? 0,
  unitPrice: toNumber(item.unitPrice),
  amount: toNumber(item.amount),
});

const normalizeBilling = (billing: Billing): Billing => ({
  ...billing,
  status: billing.status ?? 'UNPAID',
  serviceTotal: toNumber(billing.serviceTotal),
  medicationTotal: toNumber(billing.medicationTotal),
  otherTotal: toNumber(billing.otherTotal),
  totalAmount: toNumber(billing.totalAmount),
  items: Array.isArray(billing.items) ? billing.items.map((item) => normalizeBillingItem(item)) : [],
});

const normalizePage = (page: BillingPage): BillingPage => ({
  ...page,
  items: Array.isArray(page.items) ? page.items.map((item) => normalizeBilling(item)) : [],
});

export const fetchBillingPage = async (params: BillingQuery = {}): Promise<BillingPage> => {
  const { data } = await http.get<RestResponse<BillingPage> | BillingPage>('/billings', { params });
  const normalized = normalizePage(unwrap(data) as BillingPage);
  return normalized;
};

export const fetchBillingById = async (id: number): Promise<Billing> => {
  const { data } = await http.get<RestResponse<Billing> | Billing>(`/billings/${id}`);
  return normalizeBilling(unwrap(data) as Billing);
};

export const generateBillingForVisit = async (visitId: number): Promise<Billing> => {
  const { data } = await http.post<RestResponse<Billing> | Billing>(`/billings/visits/${visitId}/generate`);
  return normalizeBilling(unwrap(data) as Billing);
};

export const updateBillingStatus = async (
  billingId: number,
  payload: BillingStatusUpdatePayload,
): Promise<Billing> => {
  const { data } = await http.put<RestResponse<Billing> | Billing>(`/billings/${billingId}/status`, payload);
  return normalizeBilling(unwrap(data) as Billing);
};

export const addBillingItem = async (
  billingId: number,
  payload: BillingItemCreatePayload,
): Promise<Billing> => {
  const { data } = await http.post<RestResponse<Billing> | Billing>(`/billings/${billingId}/items`, payload);
  return normalizeBilling(unwrap(data) as Billing);
};

export const updateBillingItem = async (
  billingId: number,
  itemId: number,
  payload: BillingItemUpdatePayload,
): Promise<BillingItem> => {
  const { data } = await http.put<RestResponse<BillingItem> | BillingItem>(
    `/billings/${billingId}/items/${itemId}`,
    payload,
  );
  return normalizeBillingItem(unwrap(data) as BillingItem);
};

export const deleteBillingItem = async (billingId: number, itemId: number): Promise<void> => {
  await http.delete(`/billings/${billingId}/items/${itemId}`);
};

export const printBilling = async (id: number): Promise<Blob> => {
  const response = await http.get(`/billings/${id}/print`, { responseType: 'arraybuffer' as const });
  const blob = new Blob([response.data], { type: 'application/pdf' });
  return blob;
};

export const deleteBilling = async (id: number): Promise<void> => {
  await http.delete(`/billings/${id}`);
};
