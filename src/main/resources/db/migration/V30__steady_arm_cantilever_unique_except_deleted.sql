-- Una mensula solo admite un brazo VIVO.
--
-- V1 declaro steady_arm.cantilever_id como unique, y el borrado es logico: un brazo
-- borrado conserva su mensula, asi que la dejaba ocupada para siempre. Tras
-- DELETE /steady-arms/{id}, dar de alta otro brazo en esa mensula chocaba con la fila
-- borrada y salia como 409 BUS-002, sin nada visible que lo explicase.
--
-- Es el mismo caso que resolvio V12 con las claves naturales y V24 con el seccionador
-- de un perfil: el indice unico tiene que ser parcial, WHERE deleted = false. La fila
-- borrada se queda como esta, con su mensula, para la auditoria; simplemente deja de
-- contar. A diferencia del seccionador (V29), aqui el 1:1 sigue siendo la regla: una
-- mensula lleva un brazo vivo como mucho.
--
-- Leer el brazo de la mensula no cambia: Cantilever.steadyArm lleva
-- @SQLRestriction("deleted = false"), asi que con uno borrado y otro vivo en la misma
-- mensula se carga el vivo (SteadyArmLinkIT, tambien en el evento de la mensula y en el
-- del perfil, que lo cargan con un join).
--
-- 1) FUERA EL UNIQUE DE V1
-- ------------------------
-- Se busca por su definicion y no por su nombre: en una base creada con V1 se llama
-- steady_arm_cantilever_id_key, pero una base que Hibernate creo antes de Flyway le
-- habria puesto otro. Como en V24, solo en el esquema de la migracion.
DO $$
DECLARE
    v_constraint text;
BEGIN
    FOR v_constraint IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'steady_arm'
          AND nsp.nspname = current_schema()
          AND con.contype = 'u'
          AND pg_get_constraintdef(con.oid) = 'UNIQUE (cantilever_id)'
    LOOP
        EXECUTE format('ALTER TABLE steady_arm DROP CONSTRAINT %I', v_constraint);
    END LOOP;
END $$;

-- 2) EL INDICE UNICO PARCIAL
-- --------------------------
-- No hace falta comprobar duplicados antes: el unique de V1 ya los impedia, y entre
-- las filas vivas sigue sin poder haberlos. ddl-auto: validate no comprueba indices;
-- lo cubre FlywayMigrationIT.
CREATE UNIQUE INDEX IF NOT EXISTS ux_steady_arm_cantilever_id
    ON steady_arm (cantilever_id) WHERE deleted = false;
