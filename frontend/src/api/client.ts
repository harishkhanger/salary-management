import axios, { AxiosError } from 'axios'
import type { ApiEnvelope } from './types'

/**
 * One place understands the ApiResponse envelope: success -> unwrapped data,
 * failure -> ApiError thrown with the machine-readable code the backend
 * guarantees. 401 anywhere (except on the login call itself) bounces to /login.
 */
export class ApiError extends Error {
  code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

const http = axios.create({ baseURL: '/api' })

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const envelope = error.response?.data
    if (envelope?.error) {
      // Hard-redirect only when a real session EXPIRES mid-use: never for auth
      // endpoints (the bootstrap /auth/me probe is EXPECTED to 401 when logged
      // out — AuthContext handles it), and never when already on /login.
      // Redirecting on the probe caused an infinite reload loop.
      const isAuthCall = error.config?.url?.includes('/auth/')
      const onLoginPage = window.location.pathname === '/login'
      if (envelope.error.code === 'UNAUTHENTICATED' && !isAuthCall && !onLoginPage) {
        window.location.href = '/login'
      }
      throw new ApiError(envelope.error.code, envelope.error.message)
    }
    throw new ApiError('NETWORK', 'Cannot reach the server')
  },
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiEnvelope<T>>(url, { params })
  return res.data.data as T
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.post<ApiEnvelope<T>>(url, body)
  return res.data.data as T
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.put<ApiEnvelope<T>>(url, body)
  return res.data.data as T
}

export async function del(url: string): Promise<void> {
  await http.delete(url)
}
