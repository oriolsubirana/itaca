# Ítaca

Dashboard personal (single-user) que unifica **salud (EII)**, **entrenamiento** y **finanzas**. La interfaz principal es un chat con Claude que lee *y escribe* datos: el caso de uso central es entrenar en el gimnasio hablando con Claude mientras él guía la sesión y apunta los resultados.

## Estructura

```
backend/    Kotlin 2.2 · Spring Boot 4.1 · Java 25 · Spring Modulith 2.1 · JPA · Liquibase · JobRunr
frontend/   React 19 · Vite · TypeScript · TanStack Router/Query · Tailwind v4 · PWA
```

### Bounded contexts (módulos Modulith)

| Módulo      | Responsabilidad                                              |
| ----------- | ------------------------------------------------------------ |
| `training`  | Ejercicios, rutinas Push/Pull/Leg, workouts y series          |
| `health`    | Diario EII, comidas, brotes, analíticas con diccionario de analitos |
| `finance`   | Cuentas (CHF/EUR), snapshots de saldo, transacciones          |
| `chat`      | Sesiones y mensajes del chat con Claude, tools de lectura/escritura |
| `ingestion` | Recepción de PDF/CSV vía `/api/ingest` y encolado de jobs     |
| `shared`    | Infraestructura transversal (auth por bearer token)           |

Sin referencias directas ni FKs entre contextos; comunicación solo vía eventos de Modulith (event publication registry como outbox). La regla se verifica en `ModularityTests`.

Cada contexto sigue arquitectura hexagonal (`domain` / `application` / `adapter`) y CQRS ligero.

**Convención de idioma**: todo el código en inglés (identificadores, esquema de BD, tests, comentarios, logs); en español solo los textos de la UI/chat y los datos visibles para el usuario (nombres de ejercicios, nombres de analitos...).

## Desarrollo local

```bash
cp .env.example .env          # rellena los valores

# Solo la base de datos (y el backend con Gradle, más rápido para iterar):
docker compose up -d postgres
cd backend && ./gradlew bootRun

# O todo junto:
docker compose up --build

# Frontend:
cd frontend && npm install && npm run dev
```

El frontend en dev proxya `/api` hacia `http://localhost:8080`. El cliente TypeScript se regenera desde el contrato OpenAPI del backend con `npm run generate:api`.

## Tests

```bash
cd backend && ./gradlew test    # dominio puro + verificación Modulith + integración (Testcontainers)
cd frontend && npm run build    # typecheck + build
```

Los tests de integración necesitan Docker. Si Docker Hub te limita las descargas:
`export TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=mirror.gcr.io/`

## Variables de entorno

Documentadas en [.env.example](.env.example). La API key de Anthropic vive solo en el backend; el frontend nunca habla con Anthropic directamente. La API entera se protege con un bearer token estático (`ITACA_API_TOKEN`) hasta que llegue la fase de Spring Security 7 + JWT.

## Hoja de ruta

1. ✅ Esqueleto: monorepo, módulos Modulith verificados, esquema Liquibase completo con seeds, docker-compose, CI
2. ✅ Chat + modo entreno end-to-end (Spring AI 2.0-RC2, tools `@Tool` de training, SSE, UI móvil con respuestas rápidas)
3. ✅ Módulo health: diario y brotes (chat + formulario) y pipeline de analíticas (PDF → JobRunr → extracción con claude-haiku → revisión manual → gráfica por analito)
4. Home/dashboard
5. ✅ Módulo finance (import CSV con categorización)
6. ✅ Módulo ingestion: entrada única `/api/ingest` (Atajo de iOS / bandeja web), clasificación en dos niveles (determinista + claude-haiku) y enrutado a health/finance vía eventos Modulith
7. ✅ Módulo nutrition: dieta paleo antiinflamatoria — Claude propone comidas según el deporte (hecho o planeado) y los brotes, y registras lo que comes (chat o formulario en Salud)

### Notas de versiones

- **Spring AI 2.0**: fijado en `2.0.0-RC2` (la GA aún no está publicada); Renovate avisará cuando salga.
- **Kotlin 2.2.x** no emite bytecode JVM 25 todavía: el toolchain y el runtime son Java 25, el bytecode target es 24.
