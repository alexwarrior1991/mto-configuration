-- Elimina la columna de orden de las colecciones de hijos.
--
-- Track.profiles y Profile.cantilevers usaban @OrderColumn, que guarda el indice del elemento
-- dentro de la lista del padre. Esa columna la mantiene la LISTA del padre, no la clave ajena del
-- hijo: un hijo creado por su cuenta poniendole solo el padre —que es justo lo que hacen
-- POST /profiles con trackId y POST /cantilevers con profileId— entraba con insertion_order a null,
-- y la siguiente lectura de la coleccion moria con "Illegal null value for list index".
--
-- Las dos colecciones pasan a @OrderBy, que resuelve el orden con un ORDER BY en la consulta y por
-- tanto no tiene columna que se pueda quedar a medias: los perfiles por su punto kilometrico, que
-- es el orden fisico a lo largo de la via y el que ya usaba el resto del codigo, y las mensulas
-- por id, que solo necesitan un orden estable.
--
-- La columna es nullable y no forma parte de ninguna clave, asi que se puede borrar directamente.
-- Las tablas _aud son las de Envers y llevan la suya propia.

alter table profile        drop column if exists insertion_order;
alter table profile_aud    drop column if exists insertion_order;
alter table cantilever     drop column if exists insertion_order;
alter table cantilever_aud drop column if exists insertion_order;
