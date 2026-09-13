-- Una via puede llevar DOS tramos concatenados sin dejar de ser una via.
--
-- 'HR Track 1' y 'HR Track 2' de EP9A traen dos tramos seguidos con la kilometracion
-- reiniciada, y hasta ahora se partian en dos vias con 'rows' para que la clave natural
-- no chocara. Se deja de partirlas: son una sola via. Eso rompe DOS cosas, y esta
-- migracion arregla las dos.
--
-- 1. LA CLAVE NATURAL. El identificador de perfil se repite 47 veces en la via 1 y 46 en
--    la via 2, porque cada tramo se numero por su cuenta: '5-1.01' existe en los dos. No
--    son el mismo mastil —uno esta en el KP 5421 y el otro en el 5017—, asi que el
--    identificador por si solo NO identifica un perfil dentro de la via, y el indice
--    tiene que incluir el KP.
--
--    Medido sobre el origen: (profileId, kp) es unico en las dos hojas salvo UN caso,
--    '8-1.12' en el KP 8447 de la via 1, que aparece dos veces con distinto tipo de poste
--    (HEB-240/C3R y HEB-280/PL5). Son dos mastiles distintos con la misma etiqueta Y el
--    mismo punto kilometrico: eso es un error del origen, y el generador lo saca en
--    DESCARTADOS en vez de tragarselo.
--
--    Lo que se pierde: si alguien corrige un KP en el workbook, el reimport crea un
--    perfil nuevo en vez de actualizar el que habia. Es el precio de meter en la clave un
--    campo que puede cambiar, y no habia alternativa: la etiqueta sola ya no distingue.
DROP INDEX IF EXISTS ux_profile_track_profile_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_profile_track_profile_id_kp
    ON profile (track_id, upper(profile_id), kilometric_point) WHERE deleted = false;

-- 2. EL ORDEN. Los perfiles de una via se leian ordenados por KP, que era el orden fisico
--    a lo largo de la via. Con los dos tramos juntos deja de serlo: el segundo empieza en
--    el KP 270 mientras el primero acaba en el 8947, asi que ordenar por KP no los pone
--    detras, los MEZCLA. Hace falta un orden explicito, que el importador rellena con la
--    posicion de la fila en la hoja de origen.
--
--    No es solo cosa de EP9A: de las 176 vias medidas, 31 traen el KP no monotono.
--
--    Columna simple y anulable, no @OrderColumn: la que habia, insertion_order, la
--    mantenia la LISTA del padre y un perfil creado suelto entraba con la columna a null,
--    de modo que la siguiente lectura de la via moria con "Illegal null value for list
--    index" (por eso la quito V8). Aqui un null no rompe nada: en PostgreSQL 'order by
--    ... asc' pone los nulos al final, que es donde tiene que ir un perfil dado de alta
--    por la API sin posicion conocida.
ALTER TABLE profile     ADD COLUMN IF NOT EXISTS order_in_track integer;
ALTER TABLE profile_aud ADD COLUMN IF NOT EXISTS order_in_track integer;
