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
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const fetchClinicRooms = async (floor?: string) => {
  const params = floor ? { floor } : undefined;
  const { data } = await http.get<RestResponse<ClinicRoom[]> | ClinicRoom[]>('/clinic-rooms', { params });
  return unwrap(data);
};
