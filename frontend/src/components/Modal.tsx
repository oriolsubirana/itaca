import { useEffect } from "react";
import type { ReactNode } from "react";

/**
 * Mobile-first modal: bottom sheet on phones, centered dialog on desktop.
 * Closes on backdrop tap and Escape.
 */
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-ink/30 sm:items-center"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[85dvh] w-full max-w-md overflow-y-auto rounded-t-2xl bg-paper px-5 pt-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] shadow-xl sm:rounded-2xl sm:pb-5"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xs font-medium uppercase tracking-[0.15em] text-ink-soft">
            {title}
          </h2>
          <button
            onClick={onClose}
            aria-label="Cerrar"
            className="flex size-11 items-center justify-center text-ink-soft"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
