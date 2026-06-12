import { Outlet } from "@tanstack/react-router";
import { TabBar } from "./TabBar";

export function Layout() {
  return (
    <div className="min-h-dvh bg-paper text-ink">
      <main className="mx-auto max-w-2xl px-5 pt-6 pb-28">
        <Outlet />
      </main>
      <TabBar />
    </div>
  );
}
