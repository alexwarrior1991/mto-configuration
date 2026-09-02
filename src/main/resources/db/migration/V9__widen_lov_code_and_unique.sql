-- Amplia LOV.code de varchar(10) a varchar(40) y anade unicidad por codigo.
--
-- POR QUE
-- -------
-- 1) varchar(10) se queda corto para los codigos reales de los workbooks de
--    Execution Package: 'CP/TX-P/1100' (12), '2HEB-300 V (CP)' (15),
--    'AnMC/Tunnel' (11), 'HEB-320LL(P)' (12). El codigo es lo que la gente usa
--    para buscar, asi que truncarlo o renombrarlo no es opcion. 40 deja margen
--    sobre el maximo observado (18 caracteres en el catalogo habilitado).
--
-- 2) No habia NINGUNA restriccion UNIQUE sobre code en ninguna tabla LOV, y
--    AbstractLovCrudService.bulkCreate tampoco comprueba duplicados. Reimportar
--    el mismo fichero duplicaria filas, y LovRepository.findByCode devuelve un
--    unico E: con duplicados reventaria con NonUniqueResultException. El indice
--    unico es lo que hace que el upsert del importador sea idempotente.
--
-- OJO: el indice unico va SOLO en las 16 tablas base. Las gemelas _aud de Envers
-- guardan una fila por revision, asi que alli el codigo se repite por diseno y un
-- indice unico se violaria en cuanto se modificase cualquier LOV.
--
-- ddl-auto: validate NO comprueba longitudes de varchar ni indices, asi que este
-- cambio no fallaria al arrancar si quedase a medias. Lo cubre FlywayMigrationIT.

-- ---------------------------------------------------------------------------
-- 1. Ampliacion de longitud: 16 tablas base + 16 gemelas _aud.
-- ---------------------------------------------------------------------------
ALTER TABLE anchorage                     ALTER COLUMN code TYPE varchar(40);
ALTER TABLE anchorage_aud                 ALTER COLUMN code TYPE varchar(40);
ALTER TABLE anchorage_foundation          ALTER COLUMN code TYPE varchar(40);
ALTER TABLE anchorage_foundation_aud      ALTER COLUMN code TYPE varchar(40);
ALTER TABLE anchorage_foundation_type     ALTER COLUMN code TYPE varchar(40);
ALTER TABLE anchorage_foundation_type_aud ALTER COLUMN code TYPE varchar(40);
ALTER TABLE cantilever_type               ALTER COLUMN code TYPE varchar(40);
ALTER TABLE cantilever_type_aud           ALTER COLUMN code TYPE varchar(40);
ALTER TABLE comercial_entity_type         ALTER COLUMN code TYPE varchar(40);
ALTER TABLE comercial_entity_type_aud     ALTER COLUMN code TYPE varchar(40);
ALTER TABLE disconnector_function         ALTER COLUMN code TYPE varchar(40);
ALTER TABLE disconnector_function_aud     ALTER COLUMN code TYPE varchar(40);
ALTER TABLE foundation                    ALTER COLUMN code TYPE varchar(40);
ALTER TABLE foundation_aud                ALTER COLUMN code TYPE varchar(40);
ALTER TABLE foundation_type               ALTER COLUMN code TYPE varchar(40);
ALTER TABLE foundation_type_aud           ALTER COLUMN code TYPE varchar(40);
ALTER TABLE pole_type                     ALTER COLUMN code TYPE varchar(40);
ALTER TABLE pole_type_aud                 ALTER COLUMN code TYPE varchar(40);
ALTER TABLE portal                        ALTER COLUMN code TYPE varchar(40);
ALTER TABLE portal_aud                    ALTER COLUMN code TYPE varchar(40);
ALTER TABLE portal_type                   ALTER COLUMN code TYPE varchar(40);
ALTER TABLE portal_type_aud               ALTER COLUMN code TYPE varchar(40);
ALTER TABLE profile_status                ALTER COLUMN code TYPE varchar(40);
ALTER TABLE profile_status_aud            ALTER COLUMN code TYPE varchar(40);
ALTER TABLE return_support                ALTER COLUMN code TYPE varchar(40);
ALTER TABLE return_support_aud            ALTER COLUMN code TYPE varchar(40);
ALTER TABLE sectioning                    ALTER COLUMN code TYPE varchar(40);
ALTER TABLE sectioning_aud                ALTER COLUMN code TYPE varchar(40);
ALTER TABLE steady_arm_type               ALTER COLUMN code TYPE varchar(40);
ALTER TABLE steady_arm_type_aud           ALTER COLUMN code TYPE varchar(40);
ALTER TABLE support_type                  ALTER COLUMN code TYPE varchar(40);
ALTER TABLE support_type_aud              ALTER COLUMN code TYPE varchar(40);

-- ---------------------------------------------------------------------------
-- 2. Unicidad del codigo en las 16 tablas base.
--
-- Sin el indice, reimportar el catalogo duplicaria filas y findByCode --que
-- devuelve un unico resultado-- reventaria con NonUniqueResultException.
--
-- IF NOT EXISTS en lugar de consultar pg_indexes: hace falta que la migracion sea
-- reaplicable sobre un esquema adoptado con baseline-on-migrate, donde el indice
-- puede existir ya. Un guard contra pg_indexes filtrando por current_schema() NO
-- vale, y el fallo es sutil: con un search_path de varios esquemas --como el que
-- monta FlywayLegacyAdoptionIT, "esquema_legado, public"-- current_schema()
-- devuelve el primero mientras que la tabla y su indice se resuelven en el
-- segundo. El guard no ve el indice, intenta crearlo y choca con el que ya
-- estaba. IF NOT EXISTS resuelve el nombre igual que lo hace el propio CREATE.
--
-- Solo en las 16 tablas base: las gemelas _aud de Envers guardan una fila por
-- revision, asi que alli el codigo se repite por diseno.
--
-- Si hubiese duplicados previos la creacion falla, y es lo correcto: hay que
-- limpiarlos antes de seguir.
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS ux_anchorage_code                 ON anchorage                 (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_anchorage_foundation_code      ON anchorage_foundation      (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_anchorage_foundation_type_code ON anchorage_foundation_type (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cantilever_type_code           ON cantilever_type           (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_comercial_entity_type_code     ON comercial_entity_type     (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_disconnector_function_code     ON disconnector_function     (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_foundation_code                ON foundation                (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_foundation_type_code           ON foundation_type           (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pole_type_code                 ON pole_type                 (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_portal_code                    ON portal                    (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_portal_type_code               ON portal_type               (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_profile_status_code            ON profile_status            (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_return_support_code            ON return_support            (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sectioning_code                ON sectioning                (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_steady_arm_type_code           ON steady_arm_type           (code);
CREATE UNIQUE INDEX IF NOT EXISTS ux_support_type_code              ON support_type              (code);
