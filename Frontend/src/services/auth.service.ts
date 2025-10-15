import { http } from './http';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
}

export interface LogoutResponse {
  message: string;
}

export const login = async (payload: LoginPayload) => {
  const { data } = await http.post<LoginResponse>('/login', payload);
  return data;
};

export const logout = async () => {
  const { data } = await http.post<LogoutResponse>('/logout');
  return data;
};
