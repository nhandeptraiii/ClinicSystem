import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export type StaffRole = 'ADMIN' | 'DOCTOR' | 'NURSE' | 'CASHIER' | 'PHARMACIST';

export interface StaffDoctorInfo {
  id?: number;
  specialty: string;
  licenseNumber: string;
  biography?: string | null;
}

export interface StaffMember {
  id: number;
  fullName: string;
  email: string;
  phone?: string | null;
  gender?: string | null;
  dateOfBirth?: string | null;
  status?: string | null;
  roles: StaffRole[];
  createdAt?: string;
  updatedAt?: string;
  doctor?: StaffDoctorInfo | null;
}

export interface StaffRoleDefinition {
  id: number;
  name: StaffRole;
  description?: string | null;
}

export interface StaffQuery {
  role?: StaffRole | string;
  keyword?: string;
  page?: number;
  size?: number;
}

export interface StaffPage {
  items: StaffMember[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  totalStaff: number;
  roleTotals?: Record<string, number>;
}

export interface StaffCreatePayload {
  fullName: string;
  email: string;
  phone?: string;
  gender?: string;
  dateOfBirth?: string;
  password: string;
  roles: StaffRole[] | string[];
  doctor?: {
    specialty: string;
    licenseNumber: string;
    biography?: string;
  };
}

export interface StaffUpdatePayload {
  fullName?: string;
  email?: string;
  phone?: string;
  gender?: string;
  dateOfBirth?: string;
  password?: string;
  status?: string;
  roles?: StaffRole[] | string[];
  doctor?: {
    specialty: string;
    licenseNumber: string;
    biography?: string;
  };
}

const unwrap = <T>(response: RestResponse<T> | T): T => {
  if (response && typeof response === 'object' && 'data' in (response as RestResponse<T>)) {
    return (response as RestResponse<T>).data;
  }
  return response as T;
};

export const fetchStaff = async (params: StaffQuery = {}) => {
  const { data } = await http.get<RestResponse<StaffPage>>('/staff', { params });
  return unwrap(data);
};

export const fetchStaffById = async (id: number) => {
  const { data } = await http.get<RestResponse<StaffMember>>(`/staff/${id}`);
  return unwrap(data);
};

export const createStaff = async (payload: StaffCreatePayload) => {
  const { data } = await http.post<RestResponse<StaffMember>>('/staff', payload);
  return unwrap(data);
};

export const updateStaff = async (id: number, payload: StaffUpdatePayload) => {
  const { data } = await http.put<RestResponse<StaffMember>>(`/staff/${id}`, payload);
  return unwrap(data);
};

export const fetchStaffRoles = async () => {
  const { data } = await http.get<RestResponse<StaffRoleDefinition[]>>('/roles');
  const unwrapped = unwrap(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
};

