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
  examinationRoom?: string | null;
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
    examinationRoom?: string;
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
    examinationRoom?: string;
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
  const { data } = await http.get<RestResponse<StaffMember[]>>('/staff', { params });
  const unwrapped = unwrap(data);
  return Array.isArray(unwrapped) ? unwrapped : [];
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
