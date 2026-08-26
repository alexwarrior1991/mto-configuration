-- Contexto de traza W3C del evento.
--
-- El outbox parte la traza en dos por construccion: el mensaje se escribe dentro
-- de la peticion de negocio y se publica segundos o minutos despues, desde el hilo
-- del scheduler. Sin guardar el contexto, el span de la publicacion cuelga del
-- planificador y no de la operacion que lo origino, que es justo la trazabilidad
-- que el operationId del mensaje intenta reconstruir a mano.
--
-- Los dos campos admiten nulos: los mensajes anteriores a esta migracion no lo
-- tienen, y tampoco lo tendran los eventos generados fuera de una peticion trazada
-- (una tarea programada, por ejemplo). El relay lo trata como opcional.

-- traceparent con version 00 ocupa 55 caracteres; 64 deja margen para versiones
-- futuras del formato.
ALTER TABLE outbox_message ADD COLUMN IF NOT EXISTS trace_parent varchar(64);

-- tracestate admite hasta 512 caracteres segun la especificacion W3C.
ALTER TABLE outbox_message ADD COLUMN IF NOT EXISTS trace_state varchar(512);
