-- Campos tecnicos del perfil que hasta ahora se quedaban en los workbooks.
--
-- Los workbooks de Execution Package traen ~36 columnas por perfil y el dominio solo
-- tenia sitio para 9. Estas cinco son las que se han decidido incorporar; el resto se
-- conserva en la hoja NO_MAPEADO del maestro de perfiles.
--
-- Todas son OPCIONALES: en el origen aparecen entre el 11 % (sectioning_feeding) y el
-- 95 % (span) de las filas, asi que exigirlas dejaria fuera la mayor parte del catalogo.
--
-- Unidades y precisiones, tomadas de los rangos reales medidos sobre los 11 workbooks:
--
--   span                       metros    -15,0 .. 77,2         numeric(6,3)
--   height_cantilever_support  mm           25 .. 7.400        numeric(6,0)
--   pole_gauge_location        mm          843 .. 5.343        numeric(6,0)
--   rail_pole_distance         mm       -6.290 .. 9.125        numeric(6,0)  -- con signo: indica el lado de la via
--
-- span es el vano HASTA EL PERFIL SIGUIENTE, que es como lo anota el origen: el valor
-- vive en la fila intermedia entre dos perfiles (11.475 de 11.490 casos medidos).
--
-- sectioning_feeding reutiliza el catalogo DisconnectorFunction en lugar de crear una
-- LOV nueva: el bloque FEEDING de la leyenda define 22 codigos (Disc, Disc/NS, Disc/IO,
-- Disc/SI, Disc/t, LoadB y variantes, ED, ED/T, SurgeA, VoltageD, SECT-I, CurrentT,
-- FS-1, FS-1D, FS/PP-2, FS/PP-3, PP-2, PP-3, PP-4) y los 22 ya estan en ese catalogo.
--
-- Las columnas gemelas de profile_aud van sin NOT NULL y sin restricciones, como el
-- resto de la tabla de auditoria: Envers guarda una fila por revision y una revision de
-- borrado no trae valores.

ALTER TABLE profile ADD COLUMN IF NOT EXISTS span                      numeric(6,3);
ALTER TABLE profile ADD COLUMN IF NOT EXISTS height_cantilever_support numeric(6,0);
ALTER TABLE profile ADD COLUMN IF NOT EXISTS pole_gauge_location       numeric(6,0);
ALTER TABLE profile ADD COLUMN IF NOT EXISTS rail_pole_distance        numeric(6,0);
ALTER TABLE profile ADD COLUMN IF NOT EXISTS sectioning_feeding_id     bigint;

ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS span                      numeric(6,3);
ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS height_cantilever_support numeric(6,0);
ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS pole_gauge_location       numeric(6,0);
ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS rail_pole_distance        numeric(6,0);
ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS sectioning_feeding_id     bigint;

-- La clave ajena solo en la tabla base. profile_aud no lleva ninguna, igual que las
-- otras ocho LOV del perfil: apuntaria a filas que pueden haber cambiado desde la
-- revision que se esta guardando.
--
-- DROP IF EXISTS + ADD, y no un DO con un SELECT sobre pg_constraint: ese SELECT hay
-- que acotarlo a un esquema, y en cuanto la tabla se resuelve por search_path desde OTRO
-- —que es lo que pasa al adoptar Flyway sobre una base que ya existia— la comprobacion
-- mira donde no es, no encuentra la restriccion y el ALTER revienta por duplicada.
-- Asi es idempotente sin depender de en que esquema se este.
ALTER TABLE profile DROP CONSTRAINT IF EXISTS fk_profile_sectioning_feeding;
ALTER TABLE profile ADD CONSTRAINT fk_profile_sectioning_feeding
    FOREIGN KEY (sectioning_feeding_id) REFERENCES disconnector_function;
