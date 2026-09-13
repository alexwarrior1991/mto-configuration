-- Amplia async_job_type_check para admitir el nuevo JobType.PROFILE_IMPORT.
--
-- Mismo motivo que V10: ddl-auto: validate NO comprueba los CHECK, asi que sin esta
-- migracion el fallo no saldria al arrancar sino en el primer INSERT, al lanzar la
-- primera importacion del maestro de perfiles, y ya en produccion.
--
-- Se recrea el CHECK entero porque PostgreSQL no permite alterar la expresion de una
-- restriccion en sitio.
--
-- DROP IF EXISTS y no un DO con un SELECT sobre pg_constraint acotado a current_schema():
-- en cuanto la tabla se resuelve por search_path desde otro esquema —lo que pasa al
-- adoptar Flyway sobre una base que ya existia— esa comprobacion mira donde no es.

ALTER TABLE async_job DROP CONSTRAINT IF EXISTS async_job_type_check;
ALTER TABLE async_job ADD CONSTRAINT async_job_type_check
    CHECK (job_type IN ('PROFILE_EXPORT', 'PROFILE_BULK_CREATE', 'PROFILE_BULK_UPDATE',
                        'LOV_IMPORT', 'PROFILE_IMPORT'));
