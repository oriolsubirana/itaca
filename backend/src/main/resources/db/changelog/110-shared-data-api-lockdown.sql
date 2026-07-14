--liquibase formatted sql

--changeset oriol:shared-security-001 runAlways:true runOnChange:true splitStatements:false
--comment Lock Supabase's Data API (PostgREST) out of the public schema. Supabase exposes every
--comment public-schema table through its REST API by default; without RLS anyone holding the
--comment project URL + anon key can read and write everything. Ítaca never uses the Data API
--comment (backend goes through JDBC, files through the Storage API), so: enable RLS on every
--comment table (no policies = deny all API roles) and revoke the PostgREST role grants, current
--comment and default. runAlways: tables added by later changesets or at runtime (JobRunr,
--comment Modulith's event_publication) are swept on the next boot. Outside Supabase the role revokes are skipped (roles absent),
--comment and the backend is never affected: it connects as the table owner, which RLS ignores.
DO $$
DECLARE
    tbl text;
    api_role text;
BEGIN
    FOR tbl IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
    END LOOP;

    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', api_role);
        END IF;
    END LOOP;
END $$;
--rollback SELECT 1
