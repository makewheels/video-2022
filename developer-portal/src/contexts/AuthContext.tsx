import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import { apiClient, type Developer } from '../api/client';

interface AuthContextType {
  developer: Developer | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string, company?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [developer, setDeveloper] = useState<Developer | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = apiClient.getToken();
    if (token) {
      apiClient
        .getMe()
        .then(setDeveloper)
        .catch(() => {
          apiClient.setToken(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiClient.login({ email, password });
    apiClient.setToken(res.token);
    const me = await apiClient.getMe();
    setDeveloper(me);
  }, []);

  const register = useCallback(async (email: string, password: string, name: string, company?: string) => {
    const res = await apiClient.register({ email, password, name, company });
    apiClient.setToken(res.token);
    const me = await apiClient.getMe();
    setDeveloper(me);
  }, []);

  const logout = useCallback(() => {
    apiClient.setToken(null);
    setDeveloper(null);
  }, []);

  return (
    <AuthContext.Provider value={{ developer, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
