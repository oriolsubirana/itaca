import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { logout } from "../api/auth";
import { getFlares } from "../api/health";
import { getTrainingSummary } from "../api/training";
import {
  ageFromIso,
  computeTargets,
  dailyKcalTarget,
  getProfile,
  saveProfile,
  ACTIVITY_LEVELS,
  GOALS,
  SEXES,
  type Profile,
} from "../api/profile";

interface FormState {
  peso: string;
  altura: string;
  dob: string;
  sex: string;
  activity: string;
  goal: string;
}

function fromProfile(p: Profile): FormState {
  return {
    peso: p.weightKg != null ? String(p.weightKg) : "",
    altura: p.heightCm != null ? String(p.heightCm) : "",
    dob: p.birthDate ?? "",
    sex: p.sex ?? "",
    activity: p.activityLevel ?? "",
    goal: p.goal ?? "",
  };
}

const kfmt = (n: number | null | undefined) => (n != null && isFinite(n) ? Math.round(n).toLocaleString("de-DE") : "—");

export function Perfil() {
  const profileQ = useQuery({ queryKey: ["profile"], queryFn: getProfile });
  if (!profileQ.data) return <p className="pt-10 text-center text-sm text-ink-soft">Cargando…</p>;
  return <PerfilForm initial={profileQ.data} />;
}

function PerfilForm({ initial }: { initial: Profile }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const training = useQuery({ queryKey: ["training-summary"], queryFn: getTrainingSummary });
  const flares = useQuery({ queryKey: ["flares"], queryFn: getFlares });

  const [form, setForm] = useState<FormState>(() => fromProfile(initial));

  const save = useMutation({
    mutationFn: () =>
      saveProfile({
        weightKg: form.peso === "" ? null : Number(form.peso),
        heightCm: form.altura === "" ? null : Number(form.altura),
        birthDate: form.dob || null,
        sex: form.sex || null,
        activityLevel: form.activity || null,
        goal: form.goal || null,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["profile"] });
    },
  });

  const set = (k: keyof FormState, v: string) => setForm((f) => ({ ...f, [k]: v }));
  const age = ageFromIso(form.dob || null);
  const targets = computeTargets({
    weightKg: form.peso === "" ? null : Number(form.peso),
    heightCm: form.altura === "" ? null : Number(form.altura),
    age,
    sex: form.sex || null,
    activityLevel: form.activity || null,
    goal: form.goal || null,
  });
  const sportToday = training.data?.todayActivityKcal ?? 0;
  const flareActive = flares.data?.active != null;
  const objetivo = dailyKcalTarget(targets, sportToday, flareActive);
  const base = targets ? (flareActive ? targets.tdee : targets.baseTarget) : null;

  return (
    <div>
      <header className="-mt-1 flex items-center gap-2 border-b border-line pb-3">
        <button
          onClick={() => void navigate({ to: "/" })}
          aria-label="Volver"
          className="-ml-2 flex size-9 items-center justify-center rounded-full text-ink hover:bg-line/50"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" className="size-5">
            <path d="M15 6l-6 6 6 6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Perfil</h1>
      </header>

      <div className="space-y-9 pt-5">
        <section>
          <SecLabel>Tus datos</SecLabel>
          <div className="space-y-5">
            <Unit label="Peso" unit="kg">
              <input
                type="number"
                inputMode="decimal"
                value={form.peso}
                onChange={(e) => set("peso", e.target.value)}
                className={INPUT}
              />
            </Unit>
            <Unit label="Altura" unit="cm">
              <input
                type="number"
                inputMode="decimal"
                value={form.altura}
                onChange={(e) => set("altura", e.target.value)}
                className={INPUT}
              />
            </Unit>
            <div>
              <FieldLabel>Fecha de nacimiento{age != null ? ` · ${age} años` : ""}</FieldLabel>
              <input
                type="date"
                value={form.dob}
                max="2026-12-31"
                onChange={(e) => set("dob", e.target.value)}
                className={`${INPUT} w-full`}
              />
            </div>
            <Pills label="Sexo" options={SEXES} value={form.sex} onChange={(v) => set("sex", v)} />
            <Pills label="Nivel de actividad" options={ACTIVITY_LEVELS} value={form.activity} onChange={(v) => set("activity", v)} />
            <Pills label="Objetivo" options={GOALS} value={form.goal} onChange={(v) => set("goal", v)} />
          </div>
        </section>

        <div className="border-t border-line" />

        <section>
          <SecLabel>Tu gasto calórico</SecLabel>
          <div className="text-[12px] uppercase tracking-[0.1em] text-ink-soft">Objetivo del día</div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-[40px] font-semibold leading-none tracking-[-0.02em] tabular-nums text-ink">
              {kfmt(objetivo)}
            </span>
            <span className="text-[15px] text-ink-soft">kcal</span>
          </div>
          {objetivo != null && (
            <div className="mt-2.5 text-[13px] text-ink-soft">
              {kfmt(base)} base{sportToday > 0 ? ` + ${kfmt(sportToday)} del deporte de hoy` : ""} = {kfmt(objetivo)} kcal
            </div>
          )}
          {flareActive && (
            <div className="mt-1.5 text-[12px] text-clinical">En brote: objetivo a mantenimiento (sin déficit).</div>
          )}

          <div className="mt-5 flex gap-10 border-t border-line pt-5">
            <div>
              <div className="text-[18px] tabular-nums text-ink">{kfmt(targets?.bmr)}</div>
              <div className="mt-1 text-[11px] uppercase tracking-[0.07em] text-ink-soft">BMR · basal</div>
            </div>
            <div>
              <div className="text-[18px] tabular-nums text-ink">{kfmt(targets?.tdee)}</div>
              <div className="mt-1 text-[11px] uppercase tracking-[0.07em] text-ink-soft">TDEE · diario</div>
            </div>
          </div>

          <p className="mt-5 text-[11.5px] leading-relaxed text-ink-soft">
            Estimación orientativa basada en tus datos. No es una pauta médica.
          </p>
        </section>

        <button
          onClick={() => save.mutate()}
          disabled={save.isPending}
          className="h-12 w-full rounded-md bg-ink text-[15px] font-medium text-paper hover:bg-ink/90 disabled:opacity-40"
        >
          {save.isPending ? "Guardando…" : save.isSuccess ? "Guardado ✓" : "Guardar"}
        </button>

        <div className="border-t border-line pt-6">
          <button
            onClick={() => logout()}
            className="h-11 w-full rounded-md border border-line text-[14px] font-medium text-ink-soft hover:text-ink"
          >
            Cerrar sesión
          </button>
        </div>
      </div>
    </div>
  );
}

const INPUT =
  "h-12 flex-1 rounded-md border border-line bg-paper px-3 text-[15px] tabular-nums text-ink outline-none focus:border-ink/40";

function SecLabel({ children }: { children: string }) {
  return <h2 className="mb-4 text-xs uppercase tracking-[0.13em] text-ink-soft">{children}</h2>;
}

function FieldLabel({ children }: { children: ReactNode }) {
  return <label className="mb-2 block text-[12px] uppercase tracking-[0.07em] text-ink-soft">{children}</label>;
}

function Unit({ label, unit, children }: { label: string; unit: string; children: ReactNode }) {
  return (
    <div>
      <FieldLabel>{label}</FieldLabel>
      <div className="flex items-center gap-3">
        {children}
        <span className="w-8 text-[14px] text-ink-soft">{unit}</span>
      </div>
    </div>
  );
}

function Pills({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { code: string; label: string }[];
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <FieldLabel>{label}</FieldLabel>
      <div className="flex flex-wrap gap-1.5">
        {options.map((o) => (
          <button
            key={o.code}
            onClick={() => onChange(o.code)}
            className={`h-11 rounded-md px-4 text-[14px] transition-colors ${
              value === o.code ? "bg-ink text-paper" : "border border-line text-ink hover:bg-line/40"
            }`}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}
