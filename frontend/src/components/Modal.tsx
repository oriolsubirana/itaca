import type { ReactNode } from "react";
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";

/**
 * Mobile-first modal: bottom sheet on phones, centered dialog on desktop.
 * Headless UI Dialog handles focus trapping, Escape, scroll locking and
 * backdrop dismissal; styling stays ours.
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
  return (
    <Dialog open onClose={onClose} className="relative z-50">
      <DialogBackdrop
        transition
        className="fixed inset-0 bg-ink/30 transition-opacity duration-150 data-[closed]:opacity-0"
      />
      <div className="fixed inset-0 flex items-end justify-center sm:items-center">
        <DialogPanel
          transition
          className="max-h-[85dvh] w-full max-w-md overflow-y-auto rounded-t-2xl bg-paper px-5 pt-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] shadow-xl transition duration-150 ease-out data-[closed]:translate-y-4 data-[closed]:opacity-0 sm:rounded-2xl sm:pb-5"
        >
          <div className="mb-4 flex items-center justify-between">
            <DialogTitle className="text-xs font-medium uppercase tracking-[0.15em] text-ink-soft">
              {title}
            </DialogTitle>
            <button
              onClick={onClose}
              aria-label="Cerrar"
              className="flex size-11 items-center justify-center text-ink-soft"
            >
              ✕
            </button>
          </div>
          {children}
        </DialogPanel>
      </div>
    </Dialog>
  );
}
