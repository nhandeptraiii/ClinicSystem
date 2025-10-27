import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export type DayOfWeekKey = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY';

export interface WorkScheduleDay {
  dayOfWeek: DayOfWeekKey;
  morning: boolean;
  afternoon: boolean;
  clinicRoomId?: number | null;
  clinicRoomName?: string | null;
  clinicRoomCode?: string | null;
}

export interface WorkSchedulePayload {
  days: WorkScheduleDay[];
  clinicRoomId: number | null;
}

export const WORK_DAY_KEYS: DayOfWeekKey[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const fetchMyWorkSchedule = async () => {
  const { data } = await http.get<RestResponse<WorkScheduleDay[]> | WorkScheduleDay[]>('/users/me/work-schedule');
  return unwrap(data);
};

export const updateMyWorkSchedule = async (payload: WorkSchedulePayload) => {
  const { data } = await http.put<RestResponse<WorkScheduleDay[]> | WorkScheduleDay[]>(
    '/users/me/work-schedule',
    payload,
  );
  return unwrap(data);
};

export const fetchStaffWorkSchedule = async (userId: number) => {
  const { data } = await http.get<RestResponse<WorkScheduleDay[]> | WorkScheduleDay[]>(`/users/${userId}/work-schedule`);
  return unwrap(data);
};

export const updateStaffWorkSchedule = async (userId: number, payload: WorkSchedulePayload) => {
  const { data } = await http.put<RestResponse<WorkScheduleDay[]> | WorkScheduleDay[]>(
    `/users/${userId}/work-schedule`,
    payload,
  );
  return unwrap(data);
};
