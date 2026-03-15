const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface Developer {
  id: string;
  email: string;
  name: string;
  company?: string;
  createdAt: string;
}

export interface OAuthApp {
  id: string;
  name: string;
  description: string;
  clientId: string;
  clientSecret?: string;
  redirectUris: string[];
  scopes: string[];
  createdAt: string;
}

export interface LoginResponse {
  token: string;
}

class ApiClient {
  private token: string | null = localStorage.getItem('dev_token');

  setToken(token: string | null) {
    this.token = token;
    if (token) {
      localStorage.setItem('dev_token', token);
    } else {
      localStorage.removeItem('dev_token');
    }
  }

  getToken() {
    return this.token;
  }

  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };
    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }

    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
    });

    if (!res.ok) {
      const text = await res.text();
      let message = `HTTP ${res.status}`;
      try {
        const json = JSON.parse(text);
        message = json.message || json.error || message;
      } catch {
        message = text || message;
      }
      throw new Error(message);
    }

    const json: ApiResponse<T> = await res.json();
    if (json.code !== 0) {
      throw new Error(json.message || 'Request failed');
    }
    return json.data;
  }

  // Auth
  async register(data: { email: string; password: string; name: string; company?: string }) {
    return this.request<LoginResponse>('/developer/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async login(data: { email: string; password: string }) {
    return this.request<LoginResponse>('/developer/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getMe() {
    return this.request<Developer>('/developer/me');
  }

  // Apps
  async listApps() {
    return this.request<OAuthApp[]>('/developer/apps');
  }

  async createApp(data: { name: string; description: string; redirectUris: string[]; scopes: string[] }) {
    return this.request<OAuthApp>('/developer/apps', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async deleteApp(id: string) {
    return this.request<void>(`/developer/apps/${id}`, {
      method: 'DELETE',
    });
  }
}

export const apiClient = new ApiClient();
