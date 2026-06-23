import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { getMe } from "../api/auth";
import { ApiError } from "../api/client";
import { Login } from "../pages/Login";

/**
 * Gates the whole app on auth. Calls /api/auth/me: a 401 means "log in with Google"; any other
 * error (network/500) is treated the same — show the login screen rather than render the app
 * unauthenticated — but transient errors get a couple of retries first. A success (a session, a
 * dev token, or auth disabled locally) lets the app through. A rejected sign-in comes back with
 * ?login=denied, which we surface on the login screen.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const me = useQuery({
    queryKey: ["auth-me"],
    queryFn: getMe,
    retry: (count, error) => !(error instanceof ApiError && error.status === 401) && count < 2,
  });

  if (me.isLoading) {
    return <div className="min-h-[100dvh] bg-paper" />;
  }

  if (me.isError) {
    const denied = new URLSearchParams(window.location.search).get("login") === "denied";
    return <Login denied={denied} />;
  }

  return <>{children}</>;
}
