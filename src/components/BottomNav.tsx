"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const tabs = [
  { href: "/", label: "Home", icon: "M3 11l9-8 9 8M5 10v10h14V10" },
  { href: "/chat", label: "Chat", icon: "M4 5h16v11H8l-4 4V5z" },
  { href: "/salud", label: "Salud", icon: "M12 21s-7-4.5-9.5-9A5 5 0 0112 5a5 5 0 019.5 7c-2.5 4.5-9.5 9-9.5 9z" },
  { href: "/gym", label: "Gym", icon: "M6.5 6.5l11 11M4 8l-1 1 2 2M20 16l1-1-2-2M8 4l-1 1 2 2M16 20l1-1-2-2" },
  { href: "/finanzas", label: "Finanzas", icon: "M4 19V5m0 14h16M8 16V9m4 7V6m4 10v-4" },
];

export default function BottomNav() {
  const pathname = usePathname();
  return (
    <nav
      className="sticky bottom-0 z-40 w-full max-w-screen-sm mx-auto border-t border-line bg-surface/95 backdrop-blur"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      <ul className="grid grid-cols-5">
        {tabs.map((t) => {
          const active = t.href === "/" ? pathname === "/" : pathname.startsWith(t.href);
          return (
            <li key={t.href}>
              <Link
                href={t.href}
                className={`tap flex flex-col items-center justify-center gap-1 py-2 text-[11px] ${
                  active ? "text-accent" : "text-muted"
                }`}
              >
                <svg
                  width="22"
                  height="22"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d={t.icon} />
                </svg>
                <span>{t.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
