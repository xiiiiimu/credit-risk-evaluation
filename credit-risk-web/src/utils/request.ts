import axios, { AxiosError } from 'axios';
import { message } from 'antd';

const TOKEN_KEY = 'credit_risk_token';
const USER_ID_KEY = 'credit_risk_user_id';

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setAuth(userId: string, token: string) {
  localStorage.setItem(USER_ID_KEY, userId);
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearAuth() {
  localStorage.removeItem(USER_ID_KEY);
  localStorage.removeItem(TOKEN_KEY);
}

export function getUserId(): string {
  return localStorage.getItem(USER_ID_KEY) || '';
}

export interface ApiResult<T = unknown> {
  success?: boolean;
  errorMsg?: string;
  data?: T;
}

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
});

request.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.authorization = token;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult;
    if (body && body.success === false) {
      message.error(body.errorMsg || '请求失败');
      return Promise.reject(new Error(body.errorMsg || '请求失败'));
    }
    return response;
  },
  (error: AxiosError<ApiResult>) => {
    const msg =
      error.response?.data?.errorMsg ||
      error.message ||
      '网络异常';
    message.error(msg);
    return Promise.reject(error);
  },
);

export default request;
