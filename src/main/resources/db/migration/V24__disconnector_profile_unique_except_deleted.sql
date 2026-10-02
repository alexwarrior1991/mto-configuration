-- Un perfil solo admite un seccionador VIVO.
--
-- V1 declaro disconnector.profile_id como unique, y el borrado es logico: un
-- seccionador borrado conserva su perfil, asi que lo dejaba ocupado para siempre.
-- Colgarle otro, o mover alli uno existente, chocaba con la fila borrada y salia
-- como 409 BUS-002, sin nada visible que lo explicase.
--
-- Es el mismo caso que resolvio V12 con las claves naturales: el indice unico tiene
-- que ser parcial, WHERE deleted = false. La fila borrada se queda como esta, con su
-- perfil, para la auditoria; simplemente deja de contar.
--
-- Leer el seccionador del perfil no cambia: Profile.disconnector lleva
-- @SQLRestriction("deleted = false"), asi que con uno borrado y otro vivo en el
-- mismo perfil se carga el vivo (DisconnectorLinkIT, tambien en el esquema de la via
-- y en el evento del perfil, que lo cargan con un join).
--
-- 1) FUERA EL UNIQUE DE V1
-- ------------------------
-- Se busca por su definicion y no por su nombre: en una base creada con V1 se llama
-- disconnector_profile_id_key, pero una base que Hibernate creo antes de Flyway le
-- habria puesto otro. Como en V2, solo en el esquema de la migracion.
DO $$
DECLARE
    v_constraint text;
BEGIN
    FOR v_constraint IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'disconnector'
          AND nsp.nspname = current_schema()
          AND con.contype = 'u'
          AND pg_get_constraintdef(con.oid) = 'UNIQUE (profile_id)'
    LOOP
        EXECUTE format('ALTER TABLE disconnector DROP CONSTRAINT %I', v_constraint);
    END LOOP;
END $$;

-- 2) EL INDICE UNICO PARCIAL
-- --------------------------
-- No hace falta comprobar duplicados antes: el unique de V1 ya los impedia, y entre
-- las filas vivas sigue sin poder haberlos. ddl-auto: validate no comprueba indices;
-- lo cubre FlywayMigrationIT.
CREATE UNIQUE INDEX IF NOT EXISTS ux_disconnector_profile_id
    ON disconnector (profile_id) WHERE deleted = false;
