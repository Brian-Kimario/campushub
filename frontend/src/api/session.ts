import { API_BASE_URL, CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE } from './apiConfig'
import type { LoginResponse } from './types'

let inFlight: Promise<LoginResponse | null> | null = null

/**
 * Exchanges the HttpOnly refresh cookie for a new access token (ADR-025). Returns null when there is
 * no valid session. Calls share one request: refresh tokens rotate, so a second parallel call would
 * look like reuse and revoke the whole session.
 */
export function refreshSession(): Promise<LoginResponse | null> {
  if (!inFlight) {
    inFlight = requestRefresh().finally(() => {
      inFlight = null
    })
  }
  return inFlight
}

async function requestRefresh(): Promise<LoginResponse | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { [CLIENT_HEADER_NAME]: CLIENT_HEADER_VALUE },
    })
    if (!response.ok) {
      return null
    }
    return (await response.json()) as LoginResponse
  } catch {
    return null
  }
}
