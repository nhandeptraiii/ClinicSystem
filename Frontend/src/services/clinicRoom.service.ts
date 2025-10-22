import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface ClinicRoom {
  id: number;
  code: string;
  name: string;
  floor?: string | null;
  note?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export const fetchClinicRooms = async () => {
  const { data } = await http.get<RestResponse<ClinicRoom[]>>('/clinic-rooms');
  if (Array.isArray(data)) {
    return data as ClinicRoom[];
  }
  if (data && Array.isArray((data as any).data)) {
    return (data as any).data as ClinicRoom[];
  }
  return [];
};

