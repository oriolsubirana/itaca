---
name: sync-api
description: Regenerate the frontend TypeScript client from the backend OpenAPI contract and verify the frontend still compiles. Use after adding or changing any REST endpoint or DTO.
---

# Sync API contract

The OpenAPI spec served by springdoc is the contract between backend and frontend.

## Steps

1. Make sure Postgres is up and start the backend:
   ```bash
   docker compose up -d postgres
   cd backend && ./gradlew bootRun
   ```
   Wait for it to listen on :8080 (poll `curl -s localhost:8080/actuator/health`).
2. Regenerate the client:
   ```bash
   cd frontend && npm run generate:api
   ```
   This writes `src/api/schema.d.ts` from `http://localhost:8080/api/docs/openapi`.
3. Verify: `npm run typecheck`. Fix frontend usages if the contract changed shape.
4. Stop `bootRun` when done.
5. Commit the regenerated `schema.d.ts` together with the backend change — they must
   never drift in the same PR.

## Notes

- If the endpoint is auth-protected remember the static bearer token is disabled
  when `ITACA_API_TOKEN` is empty (default in dev).
- Breaking contract changes: update frontend callers in the same commit.
