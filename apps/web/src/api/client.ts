const TOKEN_KEY = 'aiaf.token';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

const BASE_URL = import.meta.env['VITE_API_BASE_URL'] ?? '';

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Return the raw text instead of parsing JSON (Markdown exports). */
  raw?: boolean;
}

/**
 * Thin fetch wrapper: attaches the bearer token, unwraps the API's error
 * envelope, and clears the session on a 401 so an expired token cannot leave
 * the UI in a half-authenticated state.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = getToken();
  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(options.body ? { 'content-type': 'application/json' } : {}),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    ...(options.body ? { body: JSON.stringify(options.body) } : {}),
  });

  if (response.status === 401) {
    setToken(null);
    throw new ApiError('Session expired. Please sign in again.', 401, 'UNAUTHORIZED');
  }

  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as
      | { error?: { message?: string; code?: string; details?: unknown } }
      | null;
    throw new ApiError(
      payload?.error?.message ?? `Request failed with status ${response.status}`,
      response.status,
      payload?.error?.code ?? 'UNKNOWN',
      payload?.error?.details,
    );
  }

  if (options.raw) return (await response.text()) as T;
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Downloads a Markdown export without leaving the page. */
export async function downloadMarkdown(path: string, filename: string): Promise<void> {
  const markdown = await apiRequest<string>(path, { raw: true });
  const url = URL.createObjectURL(new Blob([markdown], { type: 'text/markdown' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
