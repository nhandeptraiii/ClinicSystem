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
  const { data } = await http.post('/login', payload);
  // Unwrap if backend wraps response as { statusCode, message, data: { accessToken } }
  const token = data?.accessToken ?? data?.data?.accessToken;
  return { accessToken: token } as LoginResponse;
};

export const logout = async () => {
  const { data } = await http.post<LogoutResponse>('/logout');
  return data;
};
