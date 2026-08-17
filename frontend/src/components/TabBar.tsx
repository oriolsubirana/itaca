import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";

const tabs: { to: string; label: string; icon: ReactNode }[] = [
  {
    to: "/",
    label: "Home",
    icon: (
      <path d="M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10" strokeLinejoin="round" />
    ),
  },
  {
    to: "/chat",
    label: "Chat",
    icon: (
      <path d="M4 5.5h16v11H9l-4.5 3.5v-3.5H4z" strokeLinejoin="round" />
    ),
  },
  {
    to: "/salud",
    label: "Salud",
    icon: (
      <path d="M3 12h4l2.5-6 4 12L16 12h5" strokeLinejoin="round" />
    ),
  },
  {
    to: "/gym",
    label: "Entreno",
    icon: (
      <path d="M2.5 12h3m13 0h3M7 8v8m10-8v8M7 12h10" strokeLinecap="round" />
    ),
  },
  {
    to: "/finanzas",
    label: "Finanzas",
    icon: (
      <path d="M4 19V9m5.5 10V5m5.5 14v-7m5 7V8" strokeLinecap="round" />
    ),
  },
];

export function TabBar() {
  return (
    <nav className="fixed inset-x-0 bottom-0 border-t border-line bg-paper/95 pb-[env(safe-area-inset-bottom)] backdrop-blur">
      <div className="mx-auto flex max-w-2xl">
        {tabs.map((tab) => (
          <Link
            key={tab.to}
            to={tab.to}
            className="flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 py-2 text-ink-soft"
            activeProps={{ className: "text-ink" }}
            activeOptions={{ exact: tab.to === "/" }}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              className="size-6"
              aria-hidden
            >
              {tab.icon}
            </svg>
            <span className="text-[11px] tracking-wide">{tab.label}</span>
          </Link>
        ))}
      </div>
    </nav>
  );
}
