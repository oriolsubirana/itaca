import Link from "next/link";
import { Card, PageHeader, Stat } from "@/components/ui";
import { getTrainingSummary, getNetWorth, getRecentDiary } from "@/lib/queries";
import DiarySparkline from "@/components/charts/DiarySparkline";

export const dynamic = "force-dynamic";

export default async function Home() {
  let training = null;
  let net = null;
  let diary: Awaited<ReturnType<typeof getRecentDiary>> = [];
  let configError = false;
  try {
    [training, net, diary] = await Promise.all([
      getTrainingSummary(),
      getNetWorth(),
      getRecentDiary(30),
    ]);
  } catch {
    configError = true;
  }

  return (
    <>
      <PageHeader title="Ítaca" subtitle="Tu día de un vistazo" />
      <div className="space-y-4 px-4 py-4 pb-6">
        {configError && (
          <Card className="border-danger/40">
            <p className="text-sm text-danger">
              Configura las variables de entorno (Supabase y Anthropic) y aplica las migraciones para ver tus datos.
            </p>
          </Card>
        )}

        {/* CTA chat — interfaz principal */}
        <Link
          href="/chat"
          className="flex items-center justify-between rounded-2xl bg-accent px-5 py-4 text-accent-fg"
        >
          <div>
            <p className="text-lg font-medium">Hablar con Ítaca</p>
            <p className="text-sm opacity-80">Entrena, registra síntomas o consulta finanzas</p>
          </div>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </Link>

        {/* Gym */}
        <Card href="/gym">
          <div className="flex items-center justify-between">
            <Stat
              label="Próxima rutina"
              value={training?.proximaRutina ?? "—"}
              hint={
                training?.ultimaRutina
                  ? `Última: ${training.ultimaRutina}${training.ultimaFecha ? ` · ${training.ultimaFecha}` : ""}`
                  : "Sin sesiones aún"
              }
            />
            <Stat label="Esta semana" value={`${training?.diasEstaSemana ?? 0}`} hint="días activos" />
          </div>
        </Card>

        {/* Salud */}
        <Card href="/salud">
          <p className="text-xs uppercase tracking-wide text-muted">Salud · último mes</p>
          {diary.length > 0 ? (
            <div className="mt-2">
              <DiarySparkline data={diary.map((d) => ({ fecha: d.fecha, dolor: d.dolor ?? 0 }))} />
              <p className="mt-1 text-sm text-muted">{diary.length} registros de diario</p>
            </div>
          ) : (
            <p className="mt-2 text-sm text-muted">Sin registros este mes</p>
          )}
        </Card>

        {/* Finanzas */}
        <Card href="/finanzas">
          <Stat
            label="Patrimonio neto"
            value={net?.totalCHF != null ? `${net.totalCHF.toLocaleString("de-CH")} CHF` : "—"}
            hint={net?.totalCHF != null ? "agregado en CHF" : "Añade saldos para empezar"}
          />
        </Card>
      </div>
    </>
  );
}
