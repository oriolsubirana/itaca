import Link from "next/link";

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <header
      className="sticky top-0 z-30 flex items-end justify-between gap-3 border-b border-line bg-background/95 px-4 pb-3 pt-4 backdrop-blur"
      style={{ paddingTop: "max(1rem, env(safe-area-inset-top))" }}
    >
      <div>
        <h1 className="text-xl font-medium tracking-tight">{title}</h1>
        {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
      </div>
      {action}
    </header>
  );
}

export function Card({
  children,
  className = "",
  href,
}: {
  children: React.ReactNode;
  className?: string;
  href?: string;
}) {
  const cls = `block rounded-2xl border border-line bg-surface p-4 ${className}`;
  if (href) return <Link href={href} className={cls}>{children}</Link>;
  return <div className={cls}>{children}</div>;
}

export function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-0.5 text-2xl font-medium tabular-nums">{value}</p>
      {hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  );
}
