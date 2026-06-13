import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from "@headlessui/react";
import { CATEGORY_LABELS, CATEGORY_ORDER } from "../api/categories";
import type { ReportCategory } from "../api/categories";

/** Theme filter chips for a report/document list. */
export function CategoryFilter({
  value,
  present,
  onChange,
}: {
  value: ReportCategory | "all";
  present: ReportCategory[];
  onChange: (v: ReportCategory | "all") => void;
}) {
  const options: (ReportCategory | "all")[] = ["all", ...CATEGORY_ORDER.filter((c) => present.includes(c))];
  return (
    <div className="mb-3 flex flex-wrap gap-2">
      {options.map((c) => (
        <button
          key={c}
          onClick={() => onChange(c)}
          className={`min-h-9 rounded-full border px-3 text-xs ${
            c === value ? "border-ink bg-ink text-paper" : "border-line text-ink-soft"
          }`}
        >
          {c === "all" ? "Todos" : CATEGORY_LABELS[c]}
        </button>
      ))}
    </div>
  );
}

/** Theme selector for a single report/document (review-time override of Claude's guess). */
export function CategoryPicker({
  value,
  onChange,
}: {
  value: ReportCategory | null;
  onChange: (v: ReportCategory | null) => void;
}) {
  const options: (ReportCategory | null)[] = [...CATEGORY_ORDER, null];
  return (
    <Listbox value={value} onChange={onChange}>
      <ListboxButton className="group flex min-h-9 items-center gap-1.5 rounded-full border border-line px-3 text-xs text-ink-soft outline-none data-[open]:border-ink-soft">
        <span>{value ? CATEGORY_LABELS[value] : "Sin clasificar"}</span>
        <svg
          aria-hidden="true"
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.25"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-3 transition-transform group-data-[open]:rotate-180"
        >
          <path d="M4 6l4 4 4-4" />
        </svg>
      </ListboxButton>
      <ListboxOptions
        anchor="bottom start"
        transition
        className="z-50 rounded-lg border border-line bg-paper p-1 shadow-lg [--anchor-gap:4px] outline-none data-[closed]:opacity-0 data-[closed]:transition data-[enter]:duration-100 data-[leave]:duration-75"
      >
        {options.map((c) => (
          <ListboxOption
            key={c ?? "none"}
            value={c}
            className="cursor-pointer rounded-md px-3 py-2 text-sm text-ink-soft data-[focus]:bg-line data-[selected]:text-ink"
          >
            {c ? CATEGORY_LABELS[c] : "Sin clasificar"}
          </ListboxOption>
        ))}
      </ListboxOptions>
    </Listbox>
  );
}
