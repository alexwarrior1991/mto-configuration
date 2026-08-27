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

Tipos: `PROFILE_EXPORT`, `PROFILE_BULK_CREATE`, `PROFILE_BULK_UPDATE`.

> Añadir un valor a `JobStatus` o `JobType` exige **una migración de Flyway** que amplíe el `CHECK`
> de `async_job`. `ddl-auto: validate` no comprueba los `CHECK`, así que el fallo no saldría al
> arrancar: saldría en el primer `INSERT`, ya en producción.

---

## 4. Capacidad: 429 **y** `REJECTED`

Cuando no hay hueco se hacen las dos cosas:

- el controlador responde **429 Too Many Requests** con `Retry-After`, que es lo que un cliente
  entiende y sabe reintentar. Un 202 con un trabajo que nunca va a correr sería mentirle;
- el trabajo **se persiste** como `REJECTED`, con su `Location`. Eso es lo que hace el rechazo
  observable: queda un identificador que consultar y explotación puede contar cuántas veces se llegó
  al tope sin depender de los logs.

Que la ejecución sea en hilos virtuales no elimina la necesidad de un tope, más bien al contrario:
crear diez mil hilos virtuales es trivial, y justamente por eso nada frena por sí solo a diez mil
trabajos peleándose por las conexiones de HikariCP. **El recurso escaso no es el hilo, es la
conexión.** Exportaciones y cargas masivas tienen semáforos separados porque no compiten por lo
mismo: una es una lectura larga, la otra una ráfaga de escrituras.

La adquisición nunca espera. Encolar la petición HTTP hasta que hubiera hueco sería volver al
problema que esta capa viene a resolver.

---

## 5. Configuración

```yaml
app:
  jobs:
    profile:
      export-max-concurrency: 2      # APP_JOBS_PROFILE_EXPORT_MAX_CONCURRENCY
      bulk-max-concurrency: 1        # APP_JOBS_PROFILE_BULK_MAX_CONCURRENCY
      export-directory: exports      # APP_JOBS_PROFILE_EXPORT_DIRECTORY
      progress-flush-interval: 25
      max-item-errors: 50
      max-item-error-message-length: 500
```

- `progress-flush-interval`: el progreso **no** se escribe en cada elemento. Sería un `UPDATE` por
  cada `INSERT`, es decir, duplicar las escrituras de la carga entera para mover un contador que
  nadie mira con esa resolución.
- `max-item-errors`: una carga de cien mil elementos con un mapeo mal hecho produce cien mil errores
  idénticos. Guardarlos todos convierte la fila del trabajo en varios megas y la respuesta del
  endpoint de estado en una descarga. El recuento completo sigue en `failedItems`.

---

## 6. Por qué las cargas van elemento a elemento

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

## 7. Piezas

| Clase | Papel |
|---|---|
| `controller.synchronous.infraestructure.ProfileJobController` | contrato HTTP. **Ningún** método devuelve `CompletableFuture` |
| `service.infraestructure.jobs.ProfileJobService` | arranque, salto de hilo y cierre del trabajo |
| `service.infraestructure.jobs.AsyncJobStore` | estado del trabajo, cada operación en `REQUIRES_NEW` |
| `service.infraestructure.jobs.ProfileJobConcurrencyGuard` / `JobPermit` | topes de concurrencia |
| `service.infraestructure.jobs.ProfileJobProgress` | contadores + volcado periódico |
| `service.infraestructure.jobs.ProfileExportJobRunner` / `ProfileBulkJobRunner` | el trabajo en sí |
| `service.infraestructure.jobs.ProfileJobFiles` | localiza el CSV; comprueba contención del directorio |
| `entity.jobs.AsyncJob` + `repository.jpa.jobs.AsyncJobRepository` | persistencia |
| `db/migration/V6__create_async_job_table.sql` | tabla `async_job` |

Dos decisiones que sostienen el resto:

- **El executor es el de la aplicación** (`applicationTaskExecutor`), no uno propio. Es el que
  propaga el `SecurityContext`: sin eso el trabajo correría sin identidad y, como el
  `SecurityContextHolder` no protesta por estar vacío, la auditoría de cada perfil creado por una
  carga masiva se habría atribuido a «system» sin que nadie lo notara.
- **El estado se confirma aparte del trabajo** (`REQUIRES_NEW`). Si compartieran transacción, nadie
  vería progreso hasta el final y —peor— el intento de dejar constancia de un fallo se iría abajo
  con la transacción que falló, dejando el trabajo eternamente en `RUNNING` sin explicación.

---

## 8. Limitaciones conocidas

- **Los CSV se escriben en disco local.** Con varias réplicas, `export-directory` debe apuntar a
  almacenamiento compartido; si no, la descarga responderá 410 cuando la sirva una réplica distinta
  de la que generó el fichero.
- **`async_job` no se purga.** Solo crece. Cuando haga falta, el borrado va por `created_at`, que ya
  tiene índice. Los ficheros de `export-directory` tampoco se limpian solos.
- **Un trabajo puede quedarse en `RUNNING`** si muere el proceso que lo ejecutaba. No se marca como
  fallido al arrancar a propósito: con varias réplicas, eso mataría trabajos vivos de otra. Se
  localizan con `AsyncJobRepository.findByStatusInAndCreatedAtBefore`, servido por el índice parcial
  `idx_async_job_active`.
- **No hay cancelación** de un trabajo en curso.

---

## 9. Cambios en lo que ya existía

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
- `application.yaml`: bloque `app.jobs.profile`.
- `FlywayMigrationIT`: la lista de versiones esperadas incluye `6`.
