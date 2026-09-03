-- Amplia async_job_type_check para admitir el nuevo JobType.LOV_IMPORT.
--
-- ddl-auto: validate NO comprueba los CHECK, asi que sin esta migracion el fallo
-- no saldria al arrancar: saldria en el primer INSERT, al lanzar la primera
-- importacion de LOVs, y ya en produccion.
--
-- Se recrea el CHECK entero en lugar de intentar modificarlo porque PostgreSQL no
-- permite alterar la expresion de una restriccion en sitio.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'async_job'
          AND nsp.nspname = current_schema()
          AND con.conname = 'async_job_type_check'
    ) THEN
        ALTER TABLE async_job DROP CONSTRAINT async_job_type_check;
    END IF;

    ALTER TABLE async_job ADD CONSTRAINT async_job_type_check
        CHECK (job_type IN ('PROFILE_EXPORT', 'PROFILE_BULK_CREATE', 'PROFILE_BULK_UPDATE',
                            'LOV_IMPORT'));
END $$;
