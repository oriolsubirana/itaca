import type { ReactNode } from "react";

export function PageTitle({ children }: { children: ReactNode }) {
  return (
    <h1 className="mb-6 text-xs font-medium uppercase tracking-[0.2em] text-ink-soft">
      {children}
    </h1>
  );
}
