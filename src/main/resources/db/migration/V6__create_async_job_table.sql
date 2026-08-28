-- Trabajos en segundo plano con estado persistido (async_job).
--
-- Es la tabla que sostiene el contrato "202 Accepted + jobId": la peticion HTTP
-- responde de inmediato y el progreso vive aqui, no en la memoria del proceso ni
-- en un CompletableFuture que el cliente tenga que esperar. Guardarlo en memoria
-- habria bastado para una demo y habria fallado en lo unico que importa: un
-- reinicio del proceso, o la consulta del estado desde la replica que NO lanzo
-- el trabajo.
--
-- Sin prefijo de schema, como el resto de migraciones: el nombre real es
-- configurable (MTO_CONFIGURATION_DB_SCHEMA) y lo resuelve Flyway con su
-- default-schema. Fijarlo aqui romperia cualquier despliegue que use otro.

CREATE TABLE IF NOT EXISTS async_job (
    id uuid NOT NULL,

    job_type varchar(40) NOT NULL,
    status varchar(30) NOT NULL,

    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    finished_at timestamp(6) with time zone,

    -- Solo para las exportaciones.
    track_id bigint,
    mapper_type varchar(30),
    -- SOLO el nombre del fichero, nunca la ruta absoluta: el directorio de salida
    -- es configuracion del entorno y se resuelve al descargar. Asi la fila sigue
    -- siendo valida si el despliegue cambia de directorio, y ningun nombre
    -- guardado puede apuntar fuera de el.
    file_name varchar(255),

    -- Admite nulo: en una exportacion no se conoce de antemano cuantas filas hay
    -- (la via se recorre por ventanas) y se rellena al terminar.
    total_items integer,
    processed_items integer NOT NULL,
    successful_items integer NOT NULL,
    failed_items integer NOT NULL,

    error_message varchar(1000),
    -- text y no oid: @Lob sobre un String crea en PostgreSQL una columna oid que
    -- apunta a pg_largeobject, solo legible con la transaccion abierta y que
    -- queda huerfana al borrar la fila. Mismo motivo que en outbox_message.
    error_details_json text,

    created_by varchar(150),

    PRIMARY KEY (id)
);

-- Restricciones CHECK de los dos enumerados.
--
-- Son las que genera Hibernate a partir del enum, y se replican aqui porque
-- ddl-auto: validate NO comprueba los CHECK. Consecuencia practica al anadir un
-- valor nuevo a JobType o JobStatus: hace falta una migracion que amplie el
-- CHECK, o el fallo no saldra al arrancar sino en el primer INSERT.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'async_job'
          AND nsp.nspname = current_schema()
          AND con.conname = 'async_job_type_check'
    ) THEN
        ALTER TABLE async_job ADD CONSTRAINT async_job_type_check
            CHECK (job_type IN ('PROFILE_EXPORT', 'PROFILE_BULK_CREATE', 'PROFILE_BULK_UPDATE'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'async_job'
          AND nsp.nspname = current_schema()
          AND con.conname = 'async_job_status_check'
    ) THEN
        ALTER TABLE async_job ADD CONSTRAINT async_job_status_check
            CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS',
                              'FAILED', 'REJECTED'));
    END IF;
END $$;

-- Trabajos vivos. Parcial a proposito, igual que los del outbox: la tabla solo
-- crece y los PENDING/RUNNING son siempre un punado, asi que el indice se
-- mantiene diminuto aunque se acumulen millones de trabajos terminados.
--
-- Lo consulta explotacion para responder "que hay corriendo ahora" y para
-- localizar los trabajos que se quedaron a medias porque murio el proceso que
-- los ejecutaba.
CREATE INDEX IF NOT EXISTS idx_async_job_active
    ON async_job (created_at)
    WHERE status IN ('PENDING', 'RUNNING');

-- Antiguedad: sirve el listado por fecha y, sobre todo, la purga. Esta tabla no
-- se limpia sola todavia (ver README_ASYNC_JOBS.md); cuando se limpie, el borrado
-- ira por created_at.
CREATE INDEX IF NOT EXISTS idx_async_job_created_at
    ON async_job (created_at);
