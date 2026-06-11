import { getServerSupabase } from "./supabase";

export interface RawResult {
  analito: string;
  valor: number | null;
  unidad: string | null;
  ref_min: number | null;
  ref_max: number | null;
}

const norm = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .trim();

/**
 * Normaliza los resultados extraídos contra el diccionario de analitos EII:
 * mapea sinónimos al nombre canónico y rellena unidad/rangos si faltan.
 */
export async function normalizeResults(results: RawResult[]): Promise<RawResult[]> {
  const sb = getServerSupabase();
  const { data: dict } = await sb.from("lab_analytes").select("*");

  type Analyte = { canonico: string; unidad: string | null; ref_min: number | null; ref_max: number | null; sinonimos: string[] };
  const lookup = new Map<string, Analyte>();
  for (const a of dict ?? []) {
    lookup.set(norm(a.canonico), a);
    for (const syn of a.sinonimos ?? []) lookup.set(norm(syn), a);
  }

  return results.map((r) => {
    const match = lookup.get(norm(r.analito));
    if (!match) return r;
    return {
      analito: match.canonico,
      valor: r.valor,
      unidad: r.unidad ?? match.unidad,
      ref_min: r.ref_min ?? match.ref_min,
      ref_max: r.ref_max ?? match.ref_max,
    };
  });
}
