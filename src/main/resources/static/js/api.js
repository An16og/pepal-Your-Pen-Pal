/**
 * Custom Error class representing an RFC 9457 ProblemDetail or network error.
 */
export class ApiError extends Error {
  constructor(status, title, detail, conflictingEntryId = null, raw = null) {
    super(detail || title || `HTTP error ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.title = title || 'Error';
    this.detail = detail || this.message;
    this.conflictingEntryId = conflictingEntryId;
    this.raw = raw;
  }
}

/**
 * Global fetch wrapper for API communication.
 * Automatically parses RFC 9457 ProblemDetail payloads.
 */
export async function request(path, options = {}) {
  const headers = {
    'Accept': 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers || {})
  };

  try {
    const response = await fetch(path, {
      ...options,
      headers
    });

    if (response.status === 204) {
      return null;
    }

    const contentType = response.headers.get('content-type') || '';
    const isJson = contentType.includes('application/json') || contentType.includes('application/problem+json');
    const data = isJson ? await response.json().catch(() => null) : await response.text().catch(() => null);

    if (!response.ok) {
      let title = `Request failed (${response.status})`;
      let detail = `Server responded with status ${response.status}`;
      let conflictingId = null;

      if (data && typeof data === 'object') {
        title = data.title || data.error || title;
        detail = data.detail || data.message || data.error || detail;
        conflictingId = data.conflictingEntryId ?? (data.properties && data.properties.conflictingEntryId) ?? null;
      } else if (typeof data === 'string' && data.trim()) {
        detail = data;
      }

      throw new ApiError(response.status, title, detail, conflictingId, data);
    }

    return data;
  } catch (err) {
    if (err instanceof ApiError) {
      throw err;
    }
    // Network failures, offline state, or server down
    const friendlyDetail = 'Unable to connect to the local pepal server. Please verify the application is running.';
    throw new ApiError(0, 'Offline / Network Unavailable', friendlyDetail);
  }
}
