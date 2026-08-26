-- Numero de secuencia monotono por mensaje, asignado por la base de datos.
--
-- Resuelve dos cosas.
--
-- 1) ORDEN DETERMINISTA. El relay ordenaba por created_at, y un bulkCreate genera
--    N mensajes con el mismo instante: el orden entre ellos quedaba al azar.
--
-- 2) ORDEN POR AGREGADO. Es el problema serio. Si el mensaje A de station-5 falla
--    y se reprograma, el mensaje B de station-5 (posterior) se publicaba antes, y
--    el consumidor aplicaba el cambio VIEJO encima del nuevo. El dato maestro
--    quedaba mal en silencio. Con una secuencia comparable, el reclamo puede
--    retener B mientras A siga sin publicarse.
--
-- Lo asigna la base de datos y no la aplicacion a proposito: con varias replicas
-- escribiendo a la vez, es el unico sitio donde el contador es de verdad unico y
-- creciente.

CREATE SEQUENCE IF NOT EXISTS outbox_message_sequence;

-- "sequence" es palabra reservada en SQL, de ahi el nombre completo.
ALTER TABLE outbox_message ADD COLUMN IF NOT EXISTS sequence_number bigint;

-- Los mensajes que ya estaban en la tabla reciben su numero respetando el orden
-- en que se crearon, para no alterar la secuencia de lo que aun este sin publicar.
WITH ordenados AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS numero
    FROM outbox_message
    WHERE sequence_number IS NULL
)
UPDATE outbox_message m
SET sequence_number = o.numero
FROM ordenados o
WHERE m.id = o.id;

-- La secuencia arranca por encima de lo ya asignado.
SELECT setval(
    'outbox_message_sequence',
    COALESCE((SELECT MAX(sequence_number) FROM outbox_message), 0) + 1,
    false
);

ALTER TABLE outbox_message ALTER COLUMN sequence_number SET DEFAULT nextval('outbox_message_sequence');
ALTER TABLE outbox_message ALTER COLUMN sequence_number SET NOT NULL;

-- Reclamo: sirve el ORDER BY y corta la exploracion en el tamano del lote.
DROP INDEX IF EXISTS idx_outbox_message_claim;
CREATE INDEX IF NOT EXISTS idx_outbox_message_claim
    ON outbox_message (sequence_number)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

-- Retencion por agregado: resuelve el "existe algo anterior de este mismo
-- agregado todavia sin publicar" sin recorrer la tabla.
CREATE INDEX IF NOT EXISTS idx_outbox_message_aggregate
    ON outbox_message (aggregate_type, aggregate_id, sequence_number)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
