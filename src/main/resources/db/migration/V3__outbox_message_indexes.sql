-- Indices de outbox_message.
--
-- Los dos son PARCIALES, y ahi esta la gracia: outbox_message solo crece, pero
-- las filas que consultan el relay y la purga son una fraccion minuscula del
-- total. Un indice parcial se mantiene diminuto aunque la tabla acumule
-- millones de mensajes publicados, y ademas excluye por si mismo el grueso de
-- la tabla sin tener que leerlo.
--
-- Si outbox_message ya es enorme en produccion, conviene crearlos antes a mano
-- con CREATE INDEX CONCURRENTLY (que no puede ir dentro de una transaccion, y
-- Flyway ejecuta cada migracion en una): el IF NOT EXISTS hace que entonces
-- esta migracion no haga nada.

-- Reclamo del relay: filtra por estado y ordena por created_at con LIMIT, de
-- modo que el indice sirve el orden y la exploracion se corta en el tamano del
-- lote sin tocar nada mas.
CREATE INDEX IF NOT EXISTS idx_outbox_message_claim
    ON outbox_message (created_at, id)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

-- Purga de mensajes ya publicados, que busca por antiguedad de published_at.
CREATE INDEX IF NOT EXISTS idx_outbox_message_purge
    ON outbox_message (published_at)
    WHERE status = 'PUBLISHED';

-- Mensajes fallidos: los consulta el redrive y, sobre todo, la metrica que los
-- cuenta cada pocos segundos. Sin indice, esa metrica seria un seq scan
-- periodico sobre toda la tabla, que es un precio absurdo por un contador que
-- casi siempre vale cero.
CREATE INDEX IF NOT EXISTS idx_outbox_message_failed
    ON outbox_message (created_at)
    WHERE status = 'FAILED';
