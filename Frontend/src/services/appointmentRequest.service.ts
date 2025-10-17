import { http } from './http';

export interface AppointmentRequestPayload {
  fullName: string;
  phone: string;
  email?: string;
  dateOfBirth?: string; // ISO date string YYYY-MM-DD
  preferredAt?: string; // ISO datetime string YYYY-MM-DDTHH:mm:ss
  symptomDescription?: string;
}

export interface AppointmentRequestResponse {
  id: number;
  fullName: string;
  phone: string;
  email?: string;
  dateOfBirth?: string;
  preferredAt?: string;
  symptomDescription?: string;
  status: string;
  createdAt?: string;
}

export const createAppointmentRequest = async (
  payload: AppointmentRequestPayload,
) => {
  const { data } = await http.post<AppointmentRequestResponse>(
    '/appointment-requests',
    payload,
  );
  return data;
};

