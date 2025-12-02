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
  capacity?: number | null;
  doctorCapacity?: number | null;
  staffCapacity?: number | null;
  type?: 'CLINIC' | 'SERVICE' | 'PHARMACY' | 'RECEPTION' | 'TECHNICAL';
  createdAt?: string;
  updatedAt?: string;
}

export interface ClinicRoomPayload {
  code: string;
  name: string;
  floor?: string | null;
  note?: string | null;
  capacity?: number | null;
  doctorCapacity?: number | null;
  staffCapacity?: number | null;
  type?: ClinicRoom['type'];
}

export interface ClinicRoomQuery {
  keyword?: string;
  floor?: string;
  page?: number;
  size?: number;
}

export interface ClinicRoomPage {
  items: ClinicRoom[];
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

export const fetchClinicRooms = async (floor?: string) => {
  const params = floor ? { floor } : undefined;
  const { data } = await http.get<RestResponse<ClinicRoom[]> | ClinicRoom[]>('/clinic-rooms', { params });
  return unwrap(data);
};

export const fetchClinicRoomPage = async (params: ClinicRoomQuery = {}) => {
  const { data } = await http.get<RestResponse<ClinicRoomPage> | ClinicRoomPage | ClinicRoom[]>('/clinic-rooms', {
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
    } satisfies ClinicRoomPage;
  }
  return unwrapped as ClinicRoomPage;
};

export const fetchClinicRoomById = async (id: number) => {
  const { data } = await http.get<RestResponse<ClinicRoom> | ClinicRoom>(`/clinic-rooms/${id}`);
  return unwrap(data);
};

export const createClinicRoom = async (payload: ClinicRoomPayload) => {
  const { data } = await http.post<RestResponse<ClinicRoom> | ClinicRoom>('/clinic-rooms', payload);
  return unwrap(data);
};

export const updateClinicRoom = async (id: number, payload: Partial<ClinicRoomPayload>) => {
  const { data } = await http.put<RestResponse<ClinicRoom> | ClinicRoom>(`/clinic-rooms/${id}`, payload);
  return unwrap(data);
};

export const deleteClinicRoom = async (id: number) => {
  await http.delete(`/clinic-rooms/${id}`);
};

export interface ClinicRoomAvailability {
  id: number;
  code: string;
  name: string;
  floor?: string | null;
  available: boolean;
}

export const fetchAvailableGeneralRooms = async (scheduledAt: string, duration: number): Promise<ClinicRoomAvailability[]> => {
  const { data } = await http.get<RestResponse<ClinicRoomAvailability[]> | ClinicRoomAvailability[]>('/clinic-rooms/available', {
    params: { scheduledAt, duration },
  });
  return unwrap(data);
};
