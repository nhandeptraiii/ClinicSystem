import { http } from './http';

export interface Doctor {
  id: number;
  specialty?: string;
  account?: {
    id: number;
    fullName?: string;
  };
}

export interface DoctorQueryParams {
  specialty?: string;
  clinicRoomId?: number;
  dayOfWeek?: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
  time?: string; // HH:mm format
}

export const fetchDoctors = async (params?: DoctorQueryParams | string) => {
  // Backward compatibility: nếu là string thì coi như specialty
  const queryParams: DoctorQueryParams = typeof params === 'string' 
    ? { specialty: params }
    : (params || {});
  
  const { data } = await http.get(
    '/doctors',
    { params: queryParams },
  );
  // Normalize various possible shapes (array or wrapped content)
  if (Array.isArray(data)) return data as Doctor[];
  if (data && Array.isArray((data as any).content)) return (data as any).content as Doctor[];
  if (data && Array.isArray((data as any).data)) return (data as any).data as Doctor[];
  return [] as Doctor[];
};
