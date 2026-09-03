# Operaciones asíncronas de verdad (trabajos en segundo plano)

Capa **nueva y paralela** para las operaciones largas de perfiles. No sustituye ni modifica nada de
lo que ya había: `POST/PUT /profiles/bulk`, `GET /profiles/track/{id}/export` y todos los endpoints
`/async/profiles` siguen exactamente igual.

---

## 1. El problema que resuelve

Los endpoints `/async` existentes devuelven `CompletableFuture<ResponseEntity<...>>`. Eso cambia el
**hilo** que hace el trabajo, no **quién espera**: la petición HTTP sigue viva hasta que el trabajo
termina, el cliente sigue bloqueado y, si la conexión se corta, el resultado no llega a ninguna
parte. Es concurrencia interna, no una operación asíncrona.

Lo que cambia aquí:

| | `/async/profiles/bulk` | `/profiles/jobs/bulk-create` |
|---|---|---|
| Respuesta | 200 con el resultado final | **202 inmediato** con `jobId` y `Location` |
| Conexión HTTP | abierta hasta el final | cerrada en milisegundos |
| Si el cliente se desconecta | el resultado se pierde | el trabajo **sigue** |
| Progreso | invisible | `processedItems` / `successfulItems` / `failedItems` en BD |
| Un elemento inválido | tumba el lote entero (una transacción) | cuesta ese elemento |
| Tras un reinicio | no queda rastro | la fila del trabajo sigue ahí |

El trabajo no cuelga de la petición ni de un *future* que alguien tenga que resolver: cuelga de una
tarea encolada en el executor y de una fila en `async_job` que cualquier réplica puede consultar.

---

## 2. Endpoints

Prefijo: `ConfigurationApiPaths.BASE_PATH + "/profiles/jobs"` → `/api/v1/configuration/profiles/jobs`

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| POST | `/export?trackId=123&mapperType=basic` | `CONFIG_READ` | 202 · 429 |
| POST | `/bulk-create` | `CONFIG_IMPORT` | 202 · 400 · 429 |
| POST | `/bulk-update` | `CONFIG_IMPORT` | 202 · 400 · 429 |
| GET | `/{jobId}` | `CONFIG_READ` | 200 · 404 |
| GET | `/{jobId}/file` | `CONFIG_READ` | 200 · 404 · 409 · 410 |

`mapperType`: `basic` (por defecto), `default`, `technical`. Un valor desconocido cae en `basic`,
igual que hacía el endpoint antiguo.

### Importación del catálogo maestro de LOVs

Prefijo propio: `ConfigurationApiPaths.BASE_PATH + "/lovs/jobs"` → `/api/v1/configuration/lovs/jobs`

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| POST | `/import?dryRun=false` (multipart, campo `file`) | `CONFIG_IMPORT` + rol `LOV_MANAGE` | 202 · 400 · 429 |
| GET | `/{jobId}` | `CONFIG_READ` | 200 · 404 |
| GET | `/{jobId}/file` | `CONFIG_READ` | 200 · 404 · 409 · 410 |

Carga `data/lov-master.xlsx` (ver `data/README.md`): da de alta o actualiza cada LOV **por su
código**, así que reimportar el mismo fichero es idempotente. Solo se cargan las filas marcadas
`ENABLED=SI`. Con `dryRun=true` no se escribe nada y el informe describe exactamente lo que haría
la carga real, que es la forma prevista de revisarla antes de aplicarla.

El fichero descargable en `/{jobId}/file` es el informe en JSON, y está disponible **también
cuando el trabajo termina con errores**: es justo entonces cuando hace falta leerlo. A diferencia
de las exportaciones, aquí `409` significa "aún no ha terminado", no "no está COMPLETED".

El catálogo también puede sembrarse al arrancar con `app.lov.seed-on-startup=true`
(desactivado por defecto: escribir en base de datos al levantar el proceso es algo que hay que
pedir explícitamente). Ambas vías pasan por el mismo `LovMasterImporter`.

### Arranque de un trabajo

```
HTTP/1.1 202 Accepted
Location: https://host/api/v1/configuration/profiles/jobs/6f1c...-...
Content-Type: application/json

{
  "id": "6f1c...",
  "type": "PROFILE_BULK_CREATE",
  "status": "PENDING",
  "createdAt": "2026-08-27T09:12:03Z",
  "totalItems": 4200,
  "processedItems": 0,
  "successfulItems": 0,
  "failedItems": 0
}
```

Los campos que no aplican se **omiten** en lugar de viajar a `null`.

### Consulta del estado

```json
{
  "id": "6f1c...",
  "type": "PROFILE_BULK_CREATE",
  "status": "COMPLETED_WITH_ERRORS",
  "createdAt": "2026-08-27T09:12:03Z",
  "startedAt": "2026-08-27T09:12:03Z",
  "finishedAt": "2026-08-27T09:14:41Z",
  "totalItems": 4200,
  "processedItems": 4200,
  "successfulItems": 4197,
  "failedItems": 3,
  "itemErrors": [
    { "index": 118, "operation": "create", "code": "ValidationException", "message": "kp obligatorio [kp]" }
  ]
}
```

En una exportación terminada aparece además:

```json
"downloadUrl": "/api/v1/configuration/profiles/jobs/6f1c.../file"
```

`downloadUrl` es una **ruta HTTP**, no una ruta del servidor: la ubicación real del CSV no sale
nunca de la aplicación. Tampoco se persiste, se deriva del tipo y del estado — persistirla habría
significado descubrir, el día que cambie el prefijo de la API, que hay miles de filas apuntando a
una ruta que ya no existe.

### Descarga

| Situación | Código | Por qué |
|---|---|---|
| El trabajo no existe | 404 | — |
| El trabajo no produce fichero (carga masiva) | 404 | no hay recurso |
| El trabajo no está `COMPLETED` | **409** | el recurso existe y la petición es legítima; falta esperar. Un 404 mandaría al cliente a buscar un error en su identificador |
| El fichero ya no está | **410** | existió y no está. Un 404 sugiere reintentar; un 410 dice que hay que relanzar la exportación |
| Todo bien | 200 | `text/csv`, `Content-Disposition: attachment` |

---

## 3. Estados

```
PENDING ──▶ RUNNING ──┬──▶ COMPLETED               (nada falló)
                      ├──▶ COMPLETED_WITH_ERRORS   (éxito parcial)
                      └──▶ FAILED                  (error global)

REJECTED  (estado terminal de entrada: nunca llegó a encolarse)
```

Tipos: `PROFILE_EXPORT`, `PROFILE_BULK_CREATE`, `PROFILE_BULK_UPDATE`, `LOV_IMPORT`.

> Añadir un valor a `JobStatus` o `JobType` exige **una migración de Flyway** que amplíe el `CHECK`
> de `async_job`. `ddl-auto: validate` no comprueba los `CHECK`, así que el fallo no saldría al
> arrancar: saldría en el primer `INSERT`, ya en producción.

---

## 4. Capacidad: 429 **y** `REJECTED`

Cuando no hay hueco se hacen las dos cosas:

- el controlador responde **429 Too Many Requests** con `Retry-After`, que es lo que un cliente
  entiende y sabe reintentar. Un 202 con un trabajo que nunca va a correr sería mentirle;
- el trabajo **se persiste** como `REJECTED`, con su `Location`. Eso es lo que hace el rechazo
  observable: queda un identificador que consultar y la métrica
  `mto.jobs.submitted.total{outcome="rejected"}` para contarlos.

Que la ejecución sea en hilos virtuales no elimina la necesidad de un tope, más bien al contrario:
crear diez mil hilos virtuales es trivial, y justamente por eso nada frena por sí solo a diez mil
trabajos peleándose por las conexiones de HikariCP. **El recurso escaso no es el hilo, es la
conexión.** Exportaciones y cargas masivas tienen cupos separados porque no compiten por lo mismo:
una es una lectura larga, la otra una ráfaga de escrituras.

La reserva nunca espera. Encolar la petición HTTP hasta que hubiera hueco sería volver al problema
que esta capa viene a resolver.

### El cupo es del despliegue, no de cada réplica

El tope se cuenta **contra la tabla**, no contra un semáforo en memoria. Un semáforo es uno por JVM,
así que con tres réplicas «máximo 2 exportaciones» eran seis contra la misma base de datos — justo
lo que el tope existía para impedir.

La reserva es un `INSERT` condicionado, atómico:

```
pg_advisory_xact_lock(clave del grupo)      -- serializa el "mira y coge"
SELECT count(*) ... WHERE job_type IN (…)
                      AND status IN ('PENDING','RUNNING')
                      AND heartbeat_at > now() - timeout
if count >= tope  → INSERT ... REJECTED
else              → INSERT ... PENDING
```

Todo en **una** transacción. Separar el «cuenta» del «inserta» reabre una condición de carrera de
manual: dos peticiones simultáneas leen el mismo recuento, las dos ven hueco y las dos entran. Con
un tope de uno, eso son dos cargas masivas a la vez. El cerrojo es *advisory* porque el cupo es un
concepto, no una fila que bloquear, y se suelta solo al confirmar: no hay ruta de error que lo deje
cogido. Cada grupo tiene su clave, así que pedir hueco de exportación no espera por uno de carga.

De paso desaparece una clase entera de fallos: **el hueco no lo suelta nadie**. Se deja de ocupar
porque el trabajo sale de un estado vivo, no porque alguien acuerde devolver un permiso. El permiso
no devuelto —que reducía el tope en silencio— ya no puede existir.

### El latido

Contar filas `RUNNING` tiene un problema propio: si una réplica muere a mitad de una exportación, su
fila se queda en `RUNNING` para siempre y el hueco no vuelve nunca.

Por eso un trabajo ocupa sitio **mientras late**, no mientras su fila diga `RUNNING`. Cada réplica
refresca `heartbeat_at` de todos sus trabajos vivos con **un solo UPDATE** cada
`app.jobs.heartbeat.interval`; pasado `app.jobs.heartbeat.timeout` sin latir, el trabajo deja de
contar y su hueco queda libre solo.

El latido es **independiente del progreso**, y esa separación es deliberada: atarlo al volcado de
contadores habría sido gratis, pero un trabajo cuyo elemento en curso tarda cinco minutos habría
dejado de latir estando perfectamente vivo, y otra réplica se habría llevado su hueco.

Una pasada aparte (`app.jobs.heartbeat.reaper-fixed-delay`) cierra como `FAILED` los que dejaron de
latir. **No libera cupo** —eso ya pasó al enfriarse el latido—: solo corrige el estado que se le
enseña a quien consulta, para que un trabajo difunto no aparezca eternamente como `RUNNING`. Y es
segura con varias réplicas precisamente por el latido: la versión ingenua —marcar `RUNNING` como
fallidos al arrancar— habría matado los trabajos vivos de las demás.

## 5. Purga

`async_job` solo crece y el directorio de exportación acumula CSV para siempre. De los dos, **el
disco es el que da problemas antes**: una exportación de una vía grande son megas y nadie los
borraba.

Cada `app.jobs.purge.fixed-delay` se borran los trabajos **terminados** anteriores a
`app.jobs.purge.retention`, por lotes y con una transacción corta por lote — igual que la purga del
outbox, para que el trabajo hecho quede confirmado aunque la pasada se corte y ninguna transacción
bloquee el vacuum de la tabla.

Dos detalles que importan:

- **El fichero se borra antes que la fila.** Al revés, un corte entre las dos operaciones dejaría un
  CSV que ya no referencia nadie y que ninguna pasada volvería a mirar. Así el peor caso es un
  fichero borrado cuya fila sigue ahí, que la siguiente pasada recoge sin inmutarse.
- **Hay una barrida de huérfanos**, porque los huérfanos existen: una exportación que falla a mitad
  deja un CSV escrito a medias cuyo nombre **nunca llegó a guardarse** —solo se guarda cuando el
  volcado termina bien—, así que ninguna fila lo delata. La barrida solo toca ficheros con el patrón
  que genera esta aplicación: el directorio puede estar compartido, y una limpieza que borra lo que
  no reconoce es una limpieza que un día se lleva algo que importaba.

Los trabajos vivos no se purgan aunque sean viejos: borrarle la fila a uno que avanza sería peor que
dejarlo.

## 6. Métricas

Sin ellas, quedarse sin cupo es invisible: la aplicación responde 429, deja su fila y nadie se entera
hasta que alguien se queja de que «no le deja exportar».

| Métrica | Tipo | Etiquetas |
|---|---|---|
| `mto.jobs.submitted.total` | contador | `type`, `outcome` = `accepted`/`rejected` |
| `mto.jobs.finished.total` | contador | `type`, `status` |
| `mto.jobs.duration` | timer | `type`, `status` |
| `mto.jobs.reaped.total` | contador | — |
| `mto.jobs.active` | gauge | `group` |
| `mto.jobs.slots.max` | gauge | `group` |

Las señales que vigilar: **`submitted.total{outcome="rejected"}`** subiendo de forma sostenida (los
topes se han quedado cortos, o hay zombis ocupando sitio) y **`reaped.total`** subiendo (réplicas
muriéndose a mitad de un trabajo; se recupera solo, pero alguien debería mirarlo).

Los gauges de ocupación salen del **mismo recuento que decide si hay hueco**, así que el panel
enseña exactamente lo que aplica el tope y no una aproximación por otro camino. Se refrescan desde
una foto periódica (`app.jobs.metrics-refresh-delay`), no en cada scrape: un gauge que consulta la
base de datos cada vez que Prometheus pregunta convierte la observabilidad en carga.

## 7. Configuración

```yaml
app:
  jobs:
    metrics-refresh-delay: 30s
    heartbeat:
      interval: 15s              # un UPDATE por réplica y pasada, no uno por trabajo
      timeout: 2m                # silencio a partir del cual el hueco se libera
      reaper-fixed-delay: 1m     # solo corrige el estado; el hueco ya estaba libre
    purge:
      enabled: true
      retention: 7d
      batch-size: 200
      max-batches-per-run: 20
      fixed-delay: 1h
    profile:
      export-max-concurrency: 2  # APP_JOBS_PROFILE_EXPORT_MAX_CONCURRENCY — de TODO el despliegue
      bulk-max-concurrency: 1    # APP_JOBS_PROFILE_BULK_MAX_CONCURRENCY  — de TODO el despliegue
      max-bulk-items: 50000
      export-directory: exports  # APP_JOBS_PROFILE_EXPORT_DIRECTORY
      progress-flush-interval: 2s
      max-item-errors: 50
      max-item-error-message-length: 500
```

- `heartbeat.timeout` debe quedar **holgado** respecto a `heartbeat.interval`. Con varios latidos de
  margen, una pausa larga de GC o un pico en la base de datos no bastan para que un trabajo
  perfectamente vivo sea declarado difunto y su hueco entregado a otro.
- `max-bulk-items` **llega tarde y conviene reconocerlo**: para cuando se comprueba, el cuerpo entero
  ya está deserializado en memoria en el hilo de la petición, así que no protege del cliente que
  manda un fichero de un giga. Lo que hace es convertir en un 400 explícito lo que si no sería un
  trabajo de horas que nadie pidió conscientemente. La protección de verdad es un límite de tamaño
  de petición en el proxy de entrada.
- `progress-flush-interval`: el progreso se acota **por tiempo**, no por número de elementos. Un
  elemento de una carga masiva es una transacción entera (un `UPDATE` cada 25 sería un 4% de coste),
  pero uno de una exportación es una línea de CSV: con cadencia por recuento, ese mismo 25
  significaba **cuatro mil escrituras** para exportar una vía de cien mil perfiles. Por tiempo, un
  único valor sirve para las dos clases de trabajo y el número de escrituras deja de depender del
  tamaño del trabajo.

  > **Presupuesto de conexiones.** Durante una exportación este volcado ocurre *dentro* de la
  > transacción de solo lectura que recorre la vía y, al ir en `REQUIRES_NEW`, pide una **segunda**
  > conexión mientras la primera sigue retenida. Cada exportación en curso puede ocupar dos
  > conexiones a la vez, así que la suma de los topes debe quedar holgadamente por debajo del
  > tamaño del pool de HikariCP.

## 8. Por qué las cargas van elemento a elemento

`ProfileService.bulkCreate` / `bulkUpdate` son **una sola transacción para todo el lote**. Para una
petición síncrona de veinte elementos, todo o nada es la garantía correcta. Para una carga de miles
lanzada en segundo plano es lo contrario de lo que hace falta: no hay progreso que enseñar —nada
está confirmado hasta el final— y un único elemento inválido en la posición 9.999 deshace los 9.998
anteriores.

Por eso el trabajo llama a `create` / `update` **por elemento**: cada uno abre su propia transacción
(son métodos `@Transactional` de otro bean, así que el proxy las separa de verdad) y con ellos vienen
intactas las validaciones, los mappers, la auditoría, los eventos del outbox y la invalidación de
caché. Reimplementar el bucle contra el repositorio habría sido más rápido y se habría saltado todo
eso en silencio.

**El precio es real**: N transacciones pequeñas en vez de una grande, y un evento de outbox por
elemento. Se paga a cambio de progreso visible y de que un fallo cueste un elemento. Si algún día
pesa demasiado, el paso siguiente es un método transaccional **por bloques** (cien elementos por
transacción), que conserva el progreso parcial con una fracción de los commits. Lo que no se debe
hacer es meter los miles de elementos en una única transacción.

En `bulk-update` el `id` es obligatorio y se comprueba explícitamente: sin esa comprobación,
`update` descarta el DTO sin `id` por su filtro `Utils::exists` y devuelve sin hacer nada, de modo
que el elemento se contaría como exitoso y el cliente creería haber modificado algo que nunca se
tocó. En `bulk-create` no se exige.

---

## 9. Piezas

| Clase | Papel |
|---|---|
| `controller.synchronous.infraestructure.ProfileJobController` | contrato HTTP. **Ningún** método devuelve `CompletableFuture` |
| `service.infraestructure.jobs.ProfileJobService` | arranque, salto de hilo y cierre del trabajo |
| `service.infraestructure.jobs.AsyncJobStore` | estado y reserva de cupo, cada operación en `REQUIRES_NEW` |
| `service.infraestructure.jobs.AsyncJobHeartbeat` | señal de vida de los trabajos de esta réplica |
| `service.infraestructure.jobs.AsyncJobReaper` | cierra los que dejaron de latir |
| `service.infraestructure.jobs.AsyncJobPurgeService` / `…Scheduler` | borrado de filas y ficheros viejos |
| `service.infraestructure.jobs.AsyncJobMetrics` / `…Scheduler` | Micrometer |
| `service.infraestructure.jobs.ProfileJobProgress` | contadores + volcado periódico |
| `service.infraestructure.jobs.ProfileExportJobRunner` / `ProfileBulkJobRunner` | el trabajo en sí |
| `service.infraestructure.jobs.ProfileJobFiles` | dueño del directorio de exportación: nombra, localiza, borra |
| `enums.jobs.JobSlotGroup` | grupos de cupo y claves de cerrojo |
| `entity.jobs.AsyncJob` + `repository.jpa.jobs.AsyncJobRepository` | persistencia |
| `db/migration/V6…`, `V7__async_job_heartbeat.sql` | tabla `async_job` y latido |

Cuatro decisiones que sostienen el resto:

- **El executor es el de la aplicación** (`applicationTaskExecutor`), no uno propio. Es el que
  propaga el `SecurityContext`: sin eso el trabajo correría sin identidad y, como el
  `SecurityContextHolder` no protesta por estar vacío, la auditoría de cada perfil creado por una
  carga masiva se habría atribuido a «system» sin que nadie lo notara.
- **El estado se confirma aparte del trabajo** (`REQUIRES_NEW`). Si compartieran transacción, nadie
  vería progreso hasta el final y —peor— el intento de dejar constancia de un fallo se iría abajo
  con la transacción que falló, dejando el trabajo eternamente en `RUNNING` sin explicación.
- **Un fallo global conserva el diagnóstico.** Al cerrar en `FAILED` viajan los contadores y los
  errores por elemento ya recogidos: son lo único que dice por dónde iba el trabajo cuando se cayó.
  En una exportación el nombre del fichero solo se fija cuando el CSV está completo, así que un
  volcado a medias no llega a ofrecerse para descarga.
- **El cupo se reserva en la base de datos, no en memoria**, y se ocupa mientras el trabajo late.
  Así el tope es del despliegue entero y ningún proceso muerto se queda un hueco para siempre.

## 10. Limitaciones conocidas

- **Los CSV se escriben en disco local.** Con varias réplicas, `export-directory` debe apuntar a
  almacenamiento compartido; si no, la descarga responderá 410 cuando la sirva una réplica distinta
  de la que generó el fichero. Es la limitación que queda con más filo: el cupo ya es de clúster,
  pero el fichero no.
- **No hay cancelación** de un trabajo en curso.
- **`max-bulk-items` no protege de la memoria**, solo del trabajo inútil (ver §7).
- **Un trabajo cuya réplica muere se pierde, no se reanuda.** Se cierra como `FAILED` y hay que
  volver a lanzarlo; el progreso parcial de una carga masiva ya está confirmado, así que relanzarla
  reintenta también lo que ya se hizo.

## 11. Cambios en lo que ya existía

Todo aditivo, sin romper contratos:

- `ProfileExportService`: deja de crear su propio `ExecutorService` (que nunca se cerraba y corría
  **sin identidad**) y recibe `applicationTaskExecutor`. Se extrae `writeCsv(...)` síncrono, se
  añaden `resolveMapper` / `resolveHeader` / `resolveMapperName`, los fallos de E/S salen como
  `UncheckedIOException` y se crean los directorios que falten. `exportToCsvAsync` produce el mismo
  fichero que antes —**sin cabecera**, que es lo que esperan los consumidores actuales—;
  `exportToCsvNoWindowAsync` queda `@Deprecated` (carga la vía entera en memoria: no falla despacio,
  falla de golpe con un `OutOfMemoryError`).
- `SecurityConfiguration`: se añaden los patrones de las rutas nuevas. Sin ellos habrían caído en la
  regla general del verbo y **bastaría `config-write` para lanzar una carga masiva**.
- `application.yaml`: bloque `app.jobs`.
- `FlywayMigrationIT`: versiones esperadas hasta `7`, más comprobaciones de `async_job` (columnas,
  latido obligatorio, índices parciales y `CHECK` de estado).
