import { NextResponse } from "next/server";
import { getServerSupabase } from "@/lib/supabase";
import type { ContentBlock } from "@/lib/types";

export const runtime = "nodejs";

// Extrae el texto visible de una lista de bloques de contenido.
function visibleText(content: ContentBlock[]): string {
  return content
    .filter((b): b is Extract<ContentBlock, { type: "text" }> => b.type === "text")
    .map((b) => b.text)
    .join("\n\n")
    .trim();
}

async function ensureActiveSession() {
  const sb = getServerSupabase();
  const { data: existing } = await sb
    .from("chat_sessions")
    .select("*")
    .eq("activa", true)
    .order("created_at", { ascending: false })
    .limit(1);
  if (existing?.[0]) return existing[0];
  const { data: created } = await sb
    .from("chat_sessions")
    .insert({ modo: "general", activa: true })
    .select("*")
    .single();
  return created!;
}

// GET /api/chat/session  → sesión activa + mensajes visibles
export async function GET() {
  const sb = getServerSupabase();
  const session = await ensureActiveSession();
  const { data: msgs } = await sb
    .from("chat_messages")
    .select("role, content, created_at")
    .eq("session_id", session.id)
    .order("created_at", { ascending: true });

  const messages = (msgs ?? [])
    .map((m) => ({ role: m.role, text: visibleText(m.content as ContentBlock[]) }))
    .filter((m) => m.text.length > 0);

  return NextResponse.json({ session, messages });
}

// POST /api/chat/session  → cierra la activa y abre una nueva
export async function POST() {
  const sb = getServerSupabase();
  await sb.from("chat_sessions").update({ activa: false, ended_at: new Date().toISOString() }).eq("activa", true);
  const { data: created } = await sb
    .from("chat_sessions")
    .insert({ modo: "general", activa: true })
    .select("*")
    .single();
  return NextResponse.json({ session: created, messages: [] });
}
