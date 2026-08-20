import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { get, post } from '../api/client'
import type { SessionUser } from '../api/types'

interface AuthState {
  user: SessionUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState>(null!)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    get<SessionUser>('/auth/me')
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  const login = async (username: string, password: string) => {
    setUser(await post<SessionUser>('/auth/login', { username, password }))
  }

  const logout = async () => {
    await post('/auth/logout')
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
