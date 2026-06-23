# Despliegue (Fly.io)

Ítaca se despliega como **una sola imagen**: el SPA de React se compila y se empaqueta dentro del
jar de Spring Boot, que lo sirve en el mismo origen que la API. Así la cookie de sesión de Google y
las llamadas a `/api` funcionan sin CORS. La base de datos y el almacenamiento siguen en **Supabase**.

## Una vez (setup)

1. Instala flyctl y entra: `brew install flyctl && fly auth login`.
2. Crea la app sin desplegar todavía:
   ```bash
   fly launch --no-deploy --copy-config --name itaca --region fra
   ```
   Si `itaca` está cogido, Fly te dará otro nombre — actualiza `app` y `ITACA_APP_URL` en `fly.toml`.
   Tu URL será `https://<app>.fly.dev`.
3. **Secrets** (no van en el repo; Fly los inyecta como env vars):
   ```bash
   fly secrets set \
     SPRING_DATASOURCE_URL="jdbc:postgresql://<host>.supabase.com:5432/postgres?sslmode=require" \
     SPRING_DATASOURCE_USERNAME="postgres" \
     SPRING_DATASOURCE_PASSWORD="<...>" \
     SUPABASE_URL="https://<proj>.supabase.co" \
     SUPABASE_SERVICE_KEY="<service_role JWT>" \
     SUPABASE_BUCKET="documents" \
     ANTHROPIC_API_KEY="<...>" \
     ITACA_API_TOKEN="<token largo aleatorio>" \
     ITACA_ALLOWED_EMAIL="tu-correo@gmail.com" \
     GOOGLE_CLIENT_ID="<...>.apps.googleusercontent.com" \
     GOOGLE_CLIENT_SECRET="<...>" \
     GOOGLE_DRIVE_FOLDER_ID="<id de la carpeta>" \
     STRAVA_CLIENT_ID="<...>" STRAVA_CLIENT_SECRET="<...>" STRAVA_REFRESH_TOKEN="<...>"
   ```
   Notas:
   - El `SPRING_DATASOURCE_URL` de Supabase necesita `?sslmode=require`.
   - **No** pongas `VITE_API_TOKEN`: en prod el navegador usa la sesión de Google, no el bearer.
   - `ITACA_APP_URL` ya está en `fly.toml` (apúntalo a tu dominio si añades uno).
4. **Google OAuth → producción** (consola de Google Cloud):
   - Añade el redirect URI: `https://<app>.fly.dev/login/oauth2/code/google`.
   - Publica la pantalla de consentimiento a **"En producción"** (quita el caduca-a-7-días del refresh token).
5. Despliega: `fly deploy`. Comprueba `https://<app>.fly.dev` → debería pedirte login con Google.

## Despliegue continuo (opcional)

`.github/workflows/deploy.yml` despliega en cada push a `main`. Solo necesitas el token:
```bash
fly tokens create deploy -x 999999h   # cópialo
```
y guárdalo como secret del repo `FLY_API_TOKEN` (Settings → Secrets → Actions).

## Después

- **Garmin**: en los secrets del repo (Action de Garmin), pon `ITACA_URL=https://<app>.fly.dev` y el
  mismo `ITACA_API_TOKEN` de prod, para que el sync de wellness apunte a producción.
- **Migraciones**: Liquibase corre al arrancar contra Supabase y crea el esquema en el primer deploy.
- **Logs / estado**: `fly logs`, `fly status`, `fly ssh console`.
