import { NextResponse } from "next/server";
import type Anthropic from "@anthropic-ai/sdk";
import { getAnthropic, MODEL_CHAT, SYSTEM_PROMPT } from "@/lib/anthropic";
import { TOOLS, executeTool } from "@/lib/tools";
import { getServerSupabase } from "@/lib/supabase";

export const runtime = "nodejs";
export const maxDuration = 60;

const MAX_TURNS = 8;

// POST /api/chat  { sessionId, text }
// Ejecuta el bucle agéntico (lectura+escritura), persiste cada turno y
// devuelve los bloques de texto generados por el asistente.
export async function POST(req: Request) {
  const { sessionId, text } = await req.json();
  if (!sessionId || typeof text !== "string") {
    return NextResponse.json({ error: "Faltan sessionId o text" }, { status: 400 });
  }

  const sb = getServerSupabase();
  const anthropic = getAnthropic();

  // 1. Cargar historial de la sesión
  const { data: history } = await sb
    .from("chat_messages")
    .select("role, content")
    .eq("session_id", sessionId)
    .order("created_at", { ascending: true });

  const messages: Anthropic.MessageParam[] = (history ?? []).map((m) => ({
    role: m.role as "user" | "assistant",
    content: m.content as Anthropic.ContentBlockParam[],
  }));

  // 2. Mensaje del usuario
  const userBlocks = [{ type: "text" as const, text }];
  messages.push({ role: "user", content: userBlocks });
  await sb.from("chat_messages").insert({ session_id: sessionId, role: "user", content: userBlocks });

  // 3. Bucle agéntico
  const replyTexts: string[] = [];
  for (let turn = 0; turn < MAX_TURNS; turn++) {
    const response = await anthropic.messages.create({
      model: MODEL_CHAT,
      max_tokens: 2048,
      thinking: { type: "disabled" },
      output_config: { effort: "low" },
      system: SYSTEM_PROMPT,
      tools: TOOLS,
      messages,
    });

    messages.push({ role: "assistant", content: response.content });
    await sb
      .from("chat_messages")
      .insert({ session_id: sessionId, role: "assistant", content: response.content });

    for (const block of response.content) {
      if (block.type === "text") replyTexts.push(block.text);
    }

    if (response.stop_reason !== "tool_use") break;

    // Ejecutar herramientas y devolver resultados
    const toolResults: Anthropic.ContentBlockParam[] = [];
    for (const block of response.content) {
      if (block.type === "tool_use") {
        const result = await executeTool(
          block.name,
          block.input as Record<string, unknown>,
          { sessionId },
        );
        toolResults.push({ type: "tool_result", tool_use_id: block.id, content: result });
      }
    }
    messages.push({ role: "user", content: toolResults });
    await sb.from("chat_messages").insert({ session_id: sessionId, role: "user", content: toolResults });
  }

  return NextResponse.json({ reply: replyTexts.join("\n\n") });
}
