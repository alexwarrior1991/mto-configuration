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
-- Se crea condicionalmente para que la migracion sea aplicable tanto a un
-- esquema recien creado como a uno adoptado con baseline-on-migrate, donde el
-- indice podria existir ya. Si hubiese duplicados previos la creacion falla, y
-- eso es lo correcto: hay que limpiarlos antes de seguir.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    lov_table text;
BEGIN
    FOREACH lov_table IN ARRAY ARRAY[
        'anchorage', 'anchorage_foundation', 'anchorage_foundation_type',
        'cantilever_type', 'comercial_entity_type', 'disconnector_function',
        'foundation', 'foundation_type', 'pole_type', 'portal', 'portal_type',
        'profile_status', 'return_support', 'sectioning', 'steady_arm_type',
        'support_type'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE schemaname = current_schema()
              AND tablename = lov_table
              AND indexname = 'ux_' || lov_table || '_code'
        ) THEN
            EXECUTE format('CREATE UNIQUE INDEX %I ON %I (code)',
                           'ux_' || lov_table || '_code', lov_table);
        END IF;
    END LOOP;
END $$;
