import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export type DashboardSummary = {
  appointmentsToday: number;
  appointmentsConfirmed: number;
  appointmentsCheckedIn: number;
  pendingRequests: number;
  visitsInProgress: number;
  visitsCompleted: number;
  activeDoctors: number;
};

const unwrap = <T>(response: RestResponse<T> | T): T => {
  if (response && typeof response === 'object' && 'data' in (response as RestResponse<T>)) {
    return (response as RestResponse<T>).data;
  }
  return response as T;
};

export const fetchDashboardSummary = async (): Promise<DashboardSummary> => {
  const { data } = await http.get<RestResponse<DashboardSummary> | DashboardSummary>('/dashboard/summary');
  return unwrap(data);
};
