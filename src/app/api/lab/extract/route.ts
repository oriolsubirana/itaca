import { NextResponse } from "next/server";
import { getAnthropic, MODEL_EXTRACT } from "@/lib/anthropic";
import { normalizeResults, type RawResult } from "@/lib/lab";

export const runtime = "nodejs";
export const maxDuration = 60;

const nullable = (t: string) => ({ anyOf: [{ type: t }, { type: "null" }] });

const SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    fecha: nullable("string"),
    laboratorio: nullable("string"),
    resultados: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          analito: { type: "string" },
          valor: nullable("number"),
          unidad: nullable("string"),
          ref_min: nullable("number"),
          ref_max: nullable("number"),
        },
        required: ["analito", "valor", "unidad", "ref_min", "ref_max"],
      },
    },
  },
  required: ["fecha", "laboratorio", "resultados"],
};

// POST /api/lab/extract  (multipart con campo "file" = PDF)
export async function POST(req: Request) {
  const form = await req.formData();
  const file = form.get("file");
  if (!(file instanceof File)) {
    return NextResponse.json({ error: "Falta el PDF" }, { status: 400 });
  }
  const base64 = Buffer.from(await file.arrayBuffer()).toString("base64");

  const anthropic = getAnthropic();
  const response = await anthropic.messages.create({
    model: MODEL_EXTRACT,
    max_tokens: 4096,
    output_config: { format: { type: "json_schema", schema: SCHEMA } },
    messages: [
      {
        role: "user",
        content: [
          {
            type: "document",
            source: { type: "base64", media_type: "application/pdf", data: base64 },
          },
          {
            type: "text",
            text:
              "Extrae los resultados de esta analítica. Devuelve SOLO los datos: fecha (YYYY-MM-DD), laboratorio, y un array de resultados con analito, valor numérico, unidad y rango de referencia (ref_min, ref_max). Usa null si un campo no aparece. No inventes valores.",
          },
        ],
      },
    ],
  });

  const textBlock = response.content.find((b) => b.type === "text");
  let parsed: { fecha: string | null; laboratorio: string | null; resultados: RawResult[] };
  try {
    parsed = JSON.parse(textBlock && "text" in textBlock ? textBlock.text : "{}");
  } catch {
    return NextResponse.json({ error: "No he podido leer el PDF" }, { status: 422 });
  }

  const resultados = await normalizeResults(parsed.resultados ?? []);
  return NextResponse.json({
    fecha: parsed.fecha,
    laboratorio: parsed.laboratorio,
    resultados,
  });
}
