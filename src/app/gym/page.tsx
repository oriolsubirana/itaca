import { PageHeader } from "@/components/ui";
import { getServerSupabase } from "@/lib/supabase";
import GymView from "@/components/GymView";

export const dynamic = "force-dynamic";

interface SetJoin {
  peso: number;
  reps: number;
  created_at: string;
  exercises: { nombre: string } | null;
  workouts: { fecha: string } | null;
}

export default async function GymPage() {
  let sets: SetJoin[] = [];
  let workouts: {
    id: string;
    fecha: string;
    completado: boolean;
    routines: { nombre: string } | null;
    sets: { peso: number; reps: number; exercises: { nombre: string } | null }[];
  }[] = [];
  let error = false;

  try {
    const sb = getServerSupabase();
    const [s, w] = await Promise.all([
      sb
        .from("sets")
        .select("peso, reps, created_at, exercises(nombre), workouts(fecha)")
        .order("created_at", { ascending: true }),
      sb
        .from("workouts")
        .select("id, fecha, completado, routines(nombre), sets(peso, reps, exercises(nombre))")
        .order("fecha", { ascending: false })
        .limit(20),
    ]);
    sets = (s.data as unknown as SetJoin[]) ?? [];
    workouts = (w.data as never) ?? [];
  } catch {
    error = true;
  }

  return (
    <>
      <PageHeader title="Gym" subtitle="Progresión, frecuencia e historial" />
      <div className="px-4 py-4 pb-6">
        {error ? (
          <p className="text-sm text-danger">Configura Supabase para ver tus entrenamientos.</p>
        ) : (
          <GymView
            sets={sets.map((s) => ({
              ejercicio: s.exercises?.nombre ?? "?",
              peso: s.peso,
              reps: s.reps,
              fecha: s.workouts?.fecha ?? s.created_at.slice(0, 10),
            }))}
            workouts={workouts.map((w) => ({
              id: w.id,
              fecha: w.fecha,
              completado: w.completado,
              rutina: w.routines?.nombre ?? "—",
              series: (w.sets ?? []).map((x) => ({
                ejercicio: x.exercises?.nombre ?? "?",
                peso: x.peso,
                reps: x.reps,
              })),
            }))}
          />
        )}
      </div>
    </>
  );
}
