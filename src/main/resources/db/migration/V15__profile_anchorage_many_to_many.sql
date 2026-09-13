-- Un perfil puede llevar VARIOS anclajes a la vez, no uno.
--
-- Mismo caso que el seccionamiento en V14: 'FP+AnMC CP+AnMC' es un anclaje de catenaria
-- CON regulacion de tension y otro SIN ella en el mismo perfil, y 'CP+AnMC AnRW' uno de
-- catenaria mas uno de retorno. La clave ajena admitia uno, asi que esas celdas no eran
-- una errata: era el dominio que no cabia en el esquema. 53 filas de los workbooks.
--
-- Que 'FP+AnMC CP+AnMC' y 'CP+AnMC FP+AnMC' sean el mismo par en distinto orden confirma
-- que el orden no significa nada, y por eso al otro lado hay un Set y no una List.
--
-- Con esto son dos las relaciones N:M del perfil, sectioning y anchorage. Las demas
-- listas de valores llevan una sola, y una celda con dos sigue siendo una anomalia.
create table if not exists profile_anchorage (
    profile_id    bigint not null,
    anchorage_id bigint not null,
    primary key (profile_id, anchorage_id)
);

ALTER TABLE profile_anchorage DROP CONSTRAINT IF EXISTS fk_profile_anchorage_profile;
ALTER TABLE profile_anchorage ADD CONSTRAINT fk_profile_anchorage_profile
    FOREIGN KEY (profile_id) REFERENCES profile;

ALTER TABLE profile_anchorage DROP CONSTRAINT IF EXISTS fk_profile_anchorage_anchorage;
ALTER TABLE profile_anchorage ADD CONSTRAINT fk_profile_anchorage_anchorage
    FOREIGN KEY (anchorage_id) REFERENCES anchorage;

-- Buscar "perfiles que tienen este anclaje" recorre la tabla por el otro lado.
create index if not exists idx_profile_anchorage_anchorage
    on profile_anchorage (anchorage_id);

-- Gemela de Envers, igual que profile_sectioning_aud: una N:M audita la PERTENENCIA,
-- cada alta o baja del par es su propia revision. Anchorage es NOT_AUDITED (es un
-- catalogo), asi que aqui solo viaja su id.
create table if not exists profile_anchorage_aud (
    rev           integer not null,
    profile_id    bigint  not null,
    anchorage_id bigint  not null,
    revtype       smallint,
    primary key (rev, profile_id, anchorage_id)
);

ALTER TABLE profile_anchorage_aud DROP CONSTRAINT IF EXISTS fk_profile_anchorage_aud_rev;
ALTER TABLE profile_anchorage_aud ADD CONSTRAINT fk_profile_anchorage_aud_rev
    FOREIGN KEY (rev) REFERENCES audit_revision;

-- Lo que ya estaba cargado se conserva: cada perfil con anclaje pasa a tener uno.
--
-- Guardado, y no por gusto: la columna puede NO existir. Cuando el esquema se crea desde
-- las entidades actuales —que es lo que hace la adopcion de Flyway sobre una base que ya
-- existia— profile.anchorage_id no llego a existir nunca, porque la entidad ya declara la
-- tabla de union. El EXECUTE mantiene la sentencia sin analizar hasta que se sabe que hay
-- de donde copiar; escrita en linea, PL/pgSQL la analizaria igualmente y fallaria.
--
-- 'profile'::regclass resuelve por search_path, no por current_schema(): el mismo detalle
-- que hizo fallar V11 y V14 en ese mismo test.
DO $$
BEGIN
    -- to_regclass y no 'profile'::regclass: el cast lanza si la tabla no existe, y aqui
    -- puede no existir. to_regclass devuelve NULL y el guardado sigue siendo un guardado.
    IF to_regclass('profile') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute
                    WHERE attrelid = to_regclass('profile')
                      AND attname = 'anchorage_id'
                      AND attnum > 0
                      AND NOT attisdropped) THEN
        EXECUTE 'insert into profile_anchorage (profile_id, anchorage_id)'
             || ' select id, anchorage_id from profile where anchorage_id is not null'
             || ' on conflict do nothing';
    END IF;
END $$;

-- Y se retira la columna, para que no queden dos sitios donde mirar.
alter table profile     drop column if exists anchorage_id;
alter table profile_aud drop column if exists anchorage_id;
