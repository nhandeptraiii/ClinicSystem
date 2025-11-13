import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface BookingFunnel {
  month: string;
  requestsTotal: number;
  requestsApproved: number;
  requestsCancelled: number;
  appointmentsCreated: number;
  appointmentsFromRequests: number;
  appointmentsCompleted: number;
  appointmentsCancelled: number;
  appointmentsCheckedIn: number;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const fetchBookingFunnel = async (month: string) => {
  const { data } = await http.get<RestResponse<BookingFunnel> | BookingFunnel>('/analytics/booking-funnel', {
    params: { month },
  });
  return unwrap(data);
};

