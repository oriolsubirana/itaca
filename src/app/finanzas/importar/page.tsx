import { getServerSupabase } from "@/lib/supabase";
import ImportCSV from "@/components/ImportCSV";
import type { Account } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function ImportarPage() {
  let accounts: Account[] = [];
  try {
    const sb = getServerSupabase();
    const { data } = await sb.from("accounts").select("*").order("nombre");
    accounts = (data as Account[]) ?? [];
  } catch {
    // sin conexión: la vista mostrará el estado vacío
  }
  return <ImportCSV accounts={accounts} />;
}
