-- Pone al dia outbox_message en bases que ya existian antes de V1.
--
-- En una base nueva V1 ya la deja asi y esta migracion no cambia nada: las dos
-- partes estan guardadas para que sean idempotentes.

-- 1. payload: de large object (oid) a text.
--
-- @Lob sobre un String hace que PostgreSQL cree una columna oid, es decir, un
-- puntero a pg_largeobject. Dos problemas para el outbox: el contenido solo es
-- legible con la transaccion abierta, y al borrar la fila el large object NO se
-- borra, queda huerfano hasta que alguien pase un vacuumlo. Con la purga
-- automatica de mensajes publicados, la tabla adelgazaria mientras la base de
-- datos sigue engordando sin que se vea por ningun lado.
DO $$
DECLARE
    v_data_type text;
BEGIN
    SELECT data_type INTO v_data_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'outbox_message'
      AND column_name = 'payload';

    IF v_data_type = 'oid' THEN
        -- Los OID hay que guardarlos ANTES del ALTER: despues la columna ya no
        -- los tiene y los large objects quedarian sin nadie que los libere.
        CREATE TEMP TABLE outbox_payload_oids ON COMMIT DROP AS
            SELECT payload AS large_object_id
            FROM outbox_message
            WHERE payload IS NOT NULL;

        ALTER TABLE outbox_message
            ALTER COLUMN payload TYPE text
            USING convert_from(lo_get(payload), 'UTF8');

        PERFORM lo_unlink(large_object_id) FROM outbox_payload_oids;

        RAISE NOTICE 'outbox_message.payload convertido de oid a text';
    END IF;
END $$;

-- 2. Restriccion CHECK del estado, que ahora incluye IN_PROGRESS.
--
-- Hibernate genera un CHECK con los valores del enum. Una base creada antes de
-- que existiera IN_PROGRESS lo rechaza al reclamar un lote. Y ddl-auto: validate
-- NO comprueba las restricciones CHECK, asi que el fallo no sale al arrancar:
-- sale en la primera pasada del relay, en produccion.
DO $$
DECLARE
    v_constraint text;
BEGIN
    FOR v_constraint IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'outbox_message'
          AND nsp.nspname = current_schema()
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE outbox_message DROP CONSTRAINT %I', v_constraint);
    END LOOP;

    ALTER TABLE outbox_message
        ADD CONSTRAINT outbox_message_status_check
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'));
END $$;
