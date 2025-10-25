import { http } from './http';

interface RestResponse<T> {
  statusCode: number;
  message?: string | null;
  error?: string | null;
  data: T;
}

export interface RoleSummary {
  id: number;
  name: string;
  description?: string | null;
}

export interface UserProfile {
  id: number;
  fullName?: string | null;
  email: string;
  phone?: string | null;
  gender?: string | null;
  dateOfBirth?: string | null;
  status?: string | null;
  avatarUrl?: string | null;
  roles?: RoleSummary[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateProfilePayload {
  fullName?: string | null;
  phone?: string | null;
  gender?: string | null;
  dateOfBirth?: string | null;
  password?: {
    currentPassword: string;
    newPassword: string;
  } | null;
}

const unwrap = <T>(input: RestResponse<T> | T): T => {
  if (input && typeof input === 'object' && 'data' in (input as RestResponse<T>)) {
    return (input as RestResponse<T>).data;
  }
  return input as T;
};

export const fetchCurrentUserProfile = async () => {
  const { data } = await http.get<RestResponse<UserProfile> | UserProfile>('/users/me');
  return unwrap(data);
};

export const updateCurrentUserProfile = async (id: number, payload: UpdateProfilePayload) => {
  const { password, ...profileFields } = payload;
  const body: Record<string, unknown> = {};
  if (profileFields.fullName !== undefined) body.fullName = profileFields.fullName?.trim() || null;
  if (profileFields.phone !== undefined) body.phone = profileFields.phone?.trim() || null;
  if (profileFields.gender !== undefined) body.gender = profileFields.gender ? profileFields.gender.toUpperCase() : null;
  if (profileFields.dateOfBirth !== undefined) body.dateOfBirth = profileFields.dateOfBirth || null;

  if (Object.keys(body).length > 0) {
    await http.put(`/users/${id}`, body);
  }

  if (password) {
    await http.put('/users/me/password', password);
  }
};

export const uploadCurrentUserAvatar = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await http.put<RestResponse<UserProfile> | UserProfile>('/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return unwrap(data);
};
