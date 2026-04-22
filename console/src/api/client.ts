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
  status?: string;
  createTime?: string;
}

export interface OAuthApp {
  id: string;
  name: string;
  description: string;
  clientId: string;
  clientSecret?: string;
  redirectUris: string[];
  scopes: string[];
  rateLimitTier?: string;
  status?: string;
  createTime?: string;
}

export interface LoginResponse {
  token: string;
}

export interface WebhookConfig {
  id: string;
  appId: string;
  url: string;
  events: string[];
  secret: string;
  status: string;
  createTime: string;
  updateTime?: string;
}

export interface DeveloperStats {
  appCount: number;
  webhookCount: number;
  totalApiRequests: number;
  webhookDeliveryCount: number;
}

interface CreateOAuthAppResponse {
  appId: string;
  clientId: string;
  clientSecret: string;
  name: string;
  description: string;
  redirectUris: string[];
  scopes: string[];
  rateLimitTier?: string;
  status?: string;
  createTime?: string;
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
    const created = await this.request<CreateOAuthAppResponse>('/developer/apps', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    return {
      id: created.appId,
      name: created.name,
      description: created.description,
      clientId: created.clientId,
      clientSecret: created.clientSecret,
      redirectUris: created.redirectUris,
      scopes: created.scopes,
      rateLimitTier: created.rateLimitTier,
      status: created.status,
      createTime: created.createTime,
    } satisfies OAuthApp;
  }

  async deleteApp(id: string) {
    return this.request<void>(`/developer/apps/${id}`, {
      method: 'DELETE',
    });
  }

  // Webhooks
  async listWebhooks(appId: string) {
    return this.request<WebhookConfig[]>(`/developer/apps/${appId}/webhooks`);
  }

  async createWebhook(appId: string, data: { url: string; events: string[] }) {
    return this.request<WebhookConfig>(`/developer/apps/${appId}/webhooks`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async deleteWebhook(appId: string, webhookId: string) {
    return this.request<void>(`/developer/apps/${appId}/webhooks/${webhookId}`, {
      method: 'DELETE',
    });
  }

  async getStats() {
    return this.request<DeveloperStats>('/developer/stats');
  }

  // Developer JWT Tokens
  async createDeveloperToken(appId: string, appSecret: string) {
    return this.request<{ token: string }>('/developer/token/create', {
      method: 'POST',
      body: JSON.stringify({ appId, appSecret }),
    });
  }

  async verifyDeveloperToken(token: string) {
    return this.request<Record<string, unknown>>('/developer/token/verify', {
      method: 'POST',
      body: JSON.stringify({ token }),
    });
  }
}

export const apiClient = new ApiClient();
