# Ítaca

Dashboard personal de Oriol — unifica **salud**, **entrenamiento** y **finanzas**.
La interfaz principal es un **chat con Claude** que no solo consulta datos, también
los **escribe** (registrar series en el gimnasio, síntomas, etc.). Mobile-first, PWA
instalable, estética minimalista (ref. Margaret Howell). Moneda base CHF con soporte EUR.

## Stack

- **Next.js 16** (App Router, TypeScript, Turbopack) como PWA
- **React 19**, **Tailwind CSS v4**
- **Supabase** (Postgres) — migraciones en `/supabase/migrations`
- **Anthropic API** — `claude-sonnet-4-6` (chat con tool use), `claude-haiku-4-5` (extracción de PDF/CSV)
- **Recharts** para gráficas

## Puesta en marcha

### 1. Variables de entorno

Copia `.env.example` a `.env.local` y rellena:

```bash
NEXT_PUBLIC_SUPABASE_URL=https://xxxx.supabase.co   # Project Settings → API
SUPABASE_SERVICE_ROLE_KEY=eyJ...                     # service role (solo servidor)
ANTHROPIC_API_KEY=sk-ant-...                         # console.anthropic.com
```

> La service role key y la API key de Anthropic **solo se usan en route handlers
> del servidor**, nunca desde el cliente.

### 2. Base de datos

Aplica las migraciones (en orden) sobre tu proyecto Supabase. Con la CLI:

```bash
supabase db push
```

O pega cada archivo de `supabase/migrations/` en el **SQL Editor** de Supabase,
de `0001` a `0005`. El seed (`0005`) carga tus rutinas Push/Pull/Leg, la última
sesión de referencia (Push), las cuentas y el diccionario de analitos EII.

### 3. Desarrollo

```bash
npm install
npm run dev        # http://localhost:3000
npm run build      # build de producción
node scripts/gen-icons.mjs   # regenerar iconos PWA (opcional)
```

## Estructura

```
src/
  app/
    page.tsx              Home / dashboard
    chat/                 Chat (interfaz principal, modo entreno)
    gym/  salud/  finanzas/   Vistas por módulo
    api/
      chat/               Bucle agéntico + sesión de chat
      lab/                Extracción de analíticas (PDF → revisión → insert)
      finance/            Importación CSV + saldos
  components/             UI, nav, charts
  lib/
    tools.ts              Herramientas de Claude (lectura y escritura)
    anthropic.ts          Cliente + system prompt (reglas de entreno)
    supabase.ts  queries.ts  lab.ts  types.ts
supabase/migrations/      Esquema SQL + seed
```

## Notas de diseño

- **Sin auth** en esta fase (despliegue protegido). El código está preparado para
  añadir Supabase Auth + RLS más adelante.
- El chat mantiene el historial de la sesión activa en la tabla `chat_messages`.
- En salud, Claude **nunca da consejo médico**: solo registra, recupera y describe
  datos, y sugiere comentarlo con el gastroenterólogo.
- Reglas de entreno (en el system prompt): definición + fuerza funcional para
  ciclismo, 3×6-8 con 90 s, progresión conservadora (+2.5 kg), **nunca prensa 45°**.
- Sin integraciones bancarias ni Strava todavía; el esquema está preparado para ellas.
