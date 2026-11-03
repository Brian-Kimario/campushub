// Every API route is under /api/v1 (API_CONTRACT header). The Vite dev proxy forwards this prefix unchanged.
export const API_BASE_URL = '/api/v1'

// The cookie endpoints (refresh, logout) reject requests without this header (ADR-025). A cross-site page
// cannot send it without a CORS preflight, which the API does not allow for other origins.
export const CLIENT_HEADER_NAME = 'X-Requested-With'
export const CLIENT_HEADER_VALUE = 'CampusHub'
