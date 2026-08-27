-- Señal de vida de los trabajos en curso.
--
-- Es lo que convierte los topes de concurrencia en topes DE TODO EL DESPLIEGUE.
-- Hasta ahora el limite lo ponia un semaforo en memoria, es decir, uno por JVM:
-- con tres replicas, un "maximo 2 exportaciones" se convertia en seis contra la
-- misma base de datos, que es justo lo que el tope existia para impedir.
--
-- Contarlos contra esta tabla resuelve eso, pero abre otro problema: si una
-- replica muere a mitad de una exportacion, su fila se queda en RUNNING para
-- siempre y el hueco no vuelve nunca. El tope quedaria reducido de forma
-- permanente y el sintoma —empiezan a rechazarse trabajos sin motivo aparente—
-- aparece mucho despues de la causa y no apunta a ella.
--
-- Con el latido, un trabajo ocupa sitio mientras DA SEÑALES, no mientras su fila
-- diga RUNNING. Una replica muerta deja de latir y su hueco se libera solo.

ALTER TABLE async_job ADD COLUMN IF NOT EXISTS heartbeat_at timestamp(6) with time zone;

-- Las filas anteriores a esta migracion no tienen latido. Se rellenan con lo mas
-- parecido que tienen para poder poner el NOT NULL; las terminales no vuelven a
-- mirarse y las vivas (si las hubiera) quedan con un latido antiguo, que es
-- exactamente lo correcto: son de un proceso que ya no existe.
UPDATE async_job
SET heartbeat_at = COALESCE(started_at, created_at)
WHERE heartbeat_at IS NULL;

ALTER TABLE async_job ALTER COLUMN heartbeat_at SET NOT NULL;

-- Reparto de cupo: resuelve "cuantos trabajos de este grupo siguen latiendo" sin
-- recorrer la tabla. Parcial, como los del outbox: async_job solo crece, pero los
-- trabajos vivos son siempre un punado, asi que el indice se mantiene diminuto
-- aunque se acumulen millones de trabajos terminados.
--
-- Sirve tambien a la pasada que marca como fallidos los que dejaron de latir, que
-- filtra por los mismos dos campos en el otro sentido.
CREATE INDEX IF NOT EXISTS idx_async_job_slot
    ON async_job (job_type, heartbeat_at)
    WHERE status IN ('PENDING', 'RUNNING');

-- Purga: los candidatos son los TERMINALES antiguos, y el indice de created_at que
-- ya existe no distingue el estado. Este si, y ademas trae el nombre del fichero
-- para poder borrarlo sin volver a la tabla.
CREATE INDEX IF NOT EXISTS idx_async_job_purge
    ON async_job (created_at)
    WHERE status NOT IN ('PENDING', 'RUNNING');
