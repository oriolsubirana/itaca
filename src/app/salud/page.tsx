import Link from "next/link";
import { PageHeader } from "@/components/ui";
import { getServerSupabase } from "@/lib/supabase";
import SaludView from "@/components/SaludView";

export const dynamic = "force-dynamic";

export default async function SaludPage() {
  let labResults: { analito: string; valor: number | null; unidad: string | null; ref_min: number | null; ref_max: number | null; fecha: string }[] = [];
  let diary: Record<string, unknown>[] = [];
  let flares: { fecha_inicio: string; fecha_fin: string | null; severidad: number | null }[] = [];
  let error = false;

  try {
    const sb = getServerSupabase();
    const from = new Date(Date.now() - 120 * 864e5).toISOString().slice(0, 10);
    const [lr, de, fl] = await Promise.all([
      sb.from("lab_results").select("analito, valor, unidad, ref_min, ref_max, lab_reports!inner(fecha)"),
      sb.from("diary_entries").select("*, meals(descripcion, tags)").gte("fecha", from).order("fecha", { ascending: false }),
      sb.from("flares").select("fecha_inicio, fecha_fin, severidad").order("fecha_inicio", { ascending: false }),
    ]);
    labResults = ((lr.data as never[]) ?? []).map((r: Record<string, unknown>) => ({
      analito: r.analito as string,
      valor: r.valor as number | null,
      unidad: r.unidad as string | null,
      ref_min: r.ref_min as number | null,
      ref_max: r.ref_max as number | null,
      fecha: (r.lab_reports as { fecha: string }).fecha,
    }));
    diary = (de.data as never[]) ?? [];
    flares = (fl.data as never[]) ?? [];
  } catch {
    error = true;
  }

  return (
    <>
      <PageHeader
        title="Salud"
        subtitle="EII · diario y analíticas"
        action={
          <Link href="/salud/analitica" className="tap rounded-full bg-accent px-3 text-sm text-accent-fg grid place-items-center">
            + Analítica
          </Link>
        }
      />
      <div className="px-4 py-4 pb-6">
        {error ? (
          <p className="text-sm text-danger">Configura Supabase para ver tu salud.</p>
        ) : (
          <SaludView labResults={labResults} diary={diary} flares={flares} />
        )}
      </div>
    </>
  );
}
