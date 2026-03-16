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

export interface WebhookConfig {
  id: string;
  appId: string;
  url: string;
  events: string[];
  secret: string;
  status: string;
  createTime: string;
}

export interface DeveloperAppItem {
  id: string;
  appName: string;
  appId: string;
  appSecret?: string;
  userId: string;
  webhookUrl?: string;
  webhookSecret?: string;
  webhookEvents: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
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

  // Developer Apps (Webhook JWT system)
  async listDeveloperApps() {
    return this.request<DeveloperAppItem[]>('/developer/app/list');
  }

  async createDeveloperApp(data: { appName: string }) {
    return this.request<DeveloperAppItem>('/developer/app/create', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateDeveloperApp(appId: string, data: { webhookUrl?: string; webhookEvents?: string[] }) {
    return this.request<DeveloperAppItem>(`/developer/app/${appId}/update`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteDeveloperApp(appId: string) {
    return this.request<void>(`/developer/app/${appId}/delete`, {
      method: 'DELETE',
    });
  }

  async regenerateDeveloperAppSecret(appId: string) {
    return this.request<DeveloperAppItem>(`/developer/app/${appId}/regenerateSecret`, {
      method: 'POST',
    });
  }

  async testDeveloperWebhook(appId: string) {
    return this.request<void>(`/developer/app/${appId}/testWebhook`, {
      method: 'POST',
    });
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
