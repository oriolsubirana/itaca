import { NextResponse } from "next/server";
import { getAnthropic, MODEL_EXTRACT } from "@/lib/anthropic";

export const runtime = "nodejs";
export const maxDuration = 60;

const SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    banco: { anyOf: [{ type: "string" }, { type: "null" }] },
    transacciones: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          fecha: { type: "string" },
          importe: { type: "number" },
          descripcion: { type: "string" },
          categoria: { type: "string" },
        },
        required: ["fecha", "importe", "descripcion", "categoria"],
      },
    },
  },
  required: ["banco", "transacciones"],
};

// POST /api/finance/import  { csv }  → Claude detecta el formato y categoriza
export async function POST(req: Request) {
  const { csv } = (await req.json()) as { csv: string };
  if (!csv?.trim()) return NextResponse.json({ error: "CSV vacío" }, { status: 400 });

  const anthropic = getAnthropic();
  const response = await anthropic.messages.create({
    model: MODEL_EXTRACT,
    max_tokens: 8192,
    output_config: { format: { type: "json_schema", schema: SCHEMA } },
    system:
      "Eres un parser de extractos bancarios. Detecta el banco y el formato del CSV (separador, columnas, formato de fecha e importe europeo). Normaliza cada movimiento: fecha en YYYY-MM-DD, importe numérico (negativo = gasto, positivo = ingreso), descripción limpia y una categoría en español (alimentación, transporte, restaurantes, ocio, hogar, salud, suscripciones, ingresos, transferencias, otros).",
    messages: [
      {
        role: "user",
        content: `Convierte este extracto a movimientos estructurados:\n\n${csv.slice(0, 20000)}`,
      },
    ],
  });

  const textBlock = response.content.find((b) => b.type === "text");
  try {
    const parsed = JSON.parse(textBlock && "text" in textBlock ? textBlock.text : "{}");
    return NextResponse.json(parsed);
  } catch {
    return NextResponse.json({ error: "No he podido interpretar el CSV" }, { status: 422 });
  }
}
