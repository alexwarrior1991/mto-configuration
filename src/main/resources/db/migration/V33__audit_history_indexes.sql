-- El historial de un perfil, de una mensula y de un brazo se lee por indice.
--
-- GET /{recurso}/{id}/revisions lee las gemelas _aud de Envers, y esas tablas solo tenian su
-- clave primaria, (rev, id): con rev delante no sirve para buscar por id, ni por la columna del
-- padre. Y son las que crecen sin limite: una fila por revision de cada perfil, de cada mensula
-- y de cada brazo, y nada las purga.
--
-- Lo que de verdad filtra el SQL de Envers (sacado de su log, no supuesto):
--
--   1. La pagina y el recuento del historial de una fila:
--        from profile_aud p, audit_revision r where p.id = ? and p.rev = r.id order by p.rev desc
--      y la subconsulta con la que reconstruye cada hijo "a fecha de" una revision:
--        (select max(c2.rev) from cantilever_aud c2 where c2.rev <= ? and c2.id = c.id)
--      Las dos piden (id, rev).
--
--   2. Los hijos de cada revision, POR LA COLUMNA DEL PADRE. Envers reconstruye la coleccion de
--      una revision desde la gemela del hijo aunque la relacion diga NOT_AUDITED (eso solo lo
--      respeta en una relacion a uno):
--        from cantilever_aud c where c.profile_id = ? and c.rev = (select max ...)
--        from steady_arm_aud s where s.cantilever_id = ? and s.rev = (select max ...)
--      y las tres tablas de union del perfil, por profile_id, con su propia subconsulta por el
--      par:
--        from profile_sectioning_aud ps, sectioning_aud s where ps.profile_id = ? and
--             ps.rev = (select max(ps2.rev) ... where ps2.profile_id = ps.profile_id
--                                               and ps2.sectioning_id = ps.sectioning_id)
--
-- Con 50.000 perfiles y cuatro revisiones de cada fila (200.000 filas en profile_aud, 252.000 en
-- cantilever_aud, 168.000 en steady_arm_aud), las mensulas de UNA revision de un perfil costaban
-- 233 ms (recorrido entero de cantilever_aud) y el brazo de cada una 64 ms; una pagina de veinte
-- revisiones los paga veinte veces. Con estos indices, 0,1 ms cada una. La pagina del historial
-- de un perfil pasa de 5 ms a 0,2 ms, y la de una mensula de 31 ms a 0,04 ms.
--
-- No se indexan las gemelas pequenas (vias, estaciones, paquetes, seccionadores, aisladores,
-- catalogos, track_station): unos cientos o pocos miles de filas se recorren antes de lo que
-- tarda en usarse un indice. Si alguna crece, lleva los mismos dos indices.
--
-- ddl-auto: validate no comprueba indices; lo cubre FlywayMigrationIT.
--
-- Se crean dentro de la transaccion de Flyway: hoy estas tablas son pequenas. Si alguna vez se
-- aplica sobre gemelas ya grandes, conviene crearlos antes a mano con CREATE INDEX CONCURRENTLY
-- (que no puede ir dentro de una transaccion, y Flyway ejecuta cada migracion en una): el
-- IF NOT EXISTS hace que entonces esta migracion no haga nada.

-- 1) Por id: la pagina del historial y el "a fecha de" de cada hijo.
CREATE INDEX IF NOT EXISTS idx_profile_aud_id_rev    ON profile_aud    (id, rev);
CREATE INDEX IF NOT EXISTS idx_cantilever_aud_id_rev ON cantilever_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_steady_arm_aud_id_rev ON steady_arm_aud (id, rev);

-- 2) Por la columna del padre: los hijos de una revision.
CREATE INDEX IF NOT EXISTS idx_cantilever_aud_profile_id    ON cantilever_aud (profile_id);
CREATE INDEX IF NOT EXISTS idx_steady_arm_aud_cantilever_id ON steady_arm_aud (cantilever_id);

CREATE INDEX IF NOT EXISTS idx_profile_sectioning_aud_profile
    ON profile_sectioning_aud (profile_id, sectioning_id, rev);
CREATE INDEX IF NOT EXISTS idx_profile_anchorage_aud_profile
    ON profile_anchorage_aud (profile_id, anchorage_id, rev);
CREATE INDEX IF NOT EXISTS idx_profile_sectioning_feeding_aud_profile
    ON profile_sectioning_feeding_aud (profile_id, sectioning_feeding_id, rev);
