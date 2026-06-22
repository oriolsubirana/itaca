import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { getMe } from "../api/auth";
import { ApiError } from "../api/client";
import { Login } from "../pages/Login";

/**
 * Gates the whole app on auth. Calls /api/auth/me once: a 401 means "log in with Google"
 * (renders the Login screen); anything else (a session, a dev token, or auth disabled
 * locally) lets the app through. The backend bounces a rejected sign-in back with
 * ?login=denied, which we surface on the login screen.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const me = useQuery({ queryKey: ["auth-me"], queryFn: getMe, retry: false });

  if (me.isLoading) {
    return <div className="min-h-[100dvh] bg-paper" />;
  }

  if (me.isError) {
    const unauthorized = me.error instanceof ApiError && me.error.status === 401;
    if (unauthorized) {
      const denied = new URLSearchParams(window.location.search).get("login") === "denied";
      return <Login denied={denied} />;
    }
  }

  return <>{children}</>;
}
