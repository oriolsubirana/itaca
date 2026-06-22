import { api } from "./client";

export interface Me {
  authenticated: boolean;
  email: string | null;
  name: string | null;
}

/**
 * Where the OAuth login lives. In production the SPA and the API share an origin, so a
 * relative path works; in dev the backend is on :8080 (set VITE_AUTH_BASE), so Google's
 * registered redirect URI (/login/oauth2/code/google on the backend) matches and the
 * callback lands on the backend directly before bouncing back to the SPA.
 */
const AUTH_BASE = (import.meta.env.VITE_AUTH_BASE as string | undefined)?.replace(/\/$/, "") ?? "";

export const googleLoginUrl = `${AUTH_BASE}/oauth2/authorization/google`;

/** Full-page navigation into the Google OAuth flow (not a fetch). */
export function startGoogleLogin(): void {
  window.location.assign(googleLoginUrl);
}

/** Full-page navigation to log out; the backend clears the session and bounces back to the SPA. */
export function logout(): void {
  window.location.assign(`${AUTH_BASE}/logout`);
}

/** 401 here means "log in"; a 200 means the API is usable (Google session, token, or open dev). */
export const getMe = () => api<Me>("/auth/me");
