import { createClient, type SupabaseClient } from "@supabase/supabase-js";

/**
 * Cliente de Supabase para uso EXCLUSIVO en el servidor (route handlers,
 * server components). Usa la service role key, por lo que nunca debe
 * importarse desde código de cliente.
 *
 * App single-user sin auth: el acceso queda protegido a nivel de despliegue.
 * Preparado para migrar a Supabase Auth + RLS más adelante.
 */
let cached: SupabaseClient | null = null;

export function getServerSupabase(): SupabaseClient {
  if (cached) return cached;

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;

  if (!url || !key) {
    throw new Error(
      "Faltan variables de entorno: NEXT_PUBLIC_SUPABASE_URL y/o SUPABASE_SERVICE_ROLE_KEY",
    );
  }

  cached = createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return cached;
}
