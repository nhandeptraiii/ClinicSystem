import { http } from './http';

export interface Doctor {
  id: number;
  specialty?: string;
  account?: {
    id: number;
    fullName?: string;
  };
}

export const fetchDoctors = async (specialty?: string) => {
  const { data } = await http.get(
    '/doctors',
    { params: specialty ? { specialty } : undefined },
  );
  // Normalize various possible shapes (array or wrapped content)
  if (Array.isArray(data)) return data as Doctor[];
  if (data && Array.isArray((data as any).content)) return (data as any).content as Doctor[];
  if (data && Array.isArray((data as any).data)) return (data as any).data as Doctor[];
  return [] as Doctor[];
};
