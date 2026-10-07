# Arquitectura de Mensajería y Eventos (Patrón Transactional Outbox)

Este documento proporciona una guía detallada sobre la arquitectura de mensajería asíncrona implementada en el módulo `mto-configuration`. El sistema está diseñado para ser robusto, escalable y garantizar la integridad de los datos mediante el patrón **Transactional Outbox**.

---

## ⚠️ El evento `disconnector` gana `normallyOpen`, `driveType`, `kp` y `track`, y su `profile` puede llegar a `null`

Tres cambios, los tres compatibles hacia atrás para quien consume:

- **El seccionador lleva dos claves más**: `normallyOpen` (su estado normal: `true` normalmente
  abierto, `false` normalmente cerrado) y `driveType` (`"MOTOR"` o `"MANUAL"`), las dos `null`
  mientras no se sepan. También en sus copias reducidas dentro de los eventos `station` y
  `profile`. **Solo añade claves**; el detalle está en «Cambios en el contrato del evento
  `disconnector`», más abajo.
- **El poste deja de ser obligatorio.** La clave `profile` ya podía viajar a `null`, pero la API
  exigía `profileId`, así que en la práctica siempre llegaba. Desde `V25` hay seccionadores que no
  están en un poste, y uno que lo está puede desvincularse con su `PUT`: entonces `profile` llega a
  `null`, y `profileId` a `null` en la copia de `station`.
- **Uno sin poste lleva su propio KP y su vía** (`V26`): `kp` (en metros) y `track` (`{id, name}`).
  En uno en un poste van a `null`: son los de su perfil, que el consumidor ya sabe leer. La copia de
  `station` lleva también `kp`.

`mto-maintenance` toma de `kp` y `track` el KP y la vía de un seccionador sin poste, y el paquete de
los perfiles de esa vía, como hace con un aislador (su `docs/06-messaging.md`). Antes de ese cambio
se quedaba sin KP, sin vía y sin paquete, sin fallar. `mto-notification` solo copia de
`values` su lista blanca (`name`, `kp`…), en la que no están. `mto-stock` registra el evento sin
tratarlo.

## ⚠️ El sobre gana `actor` y `correlationId`, nace `job.finished` y las cuatro colas desaparecen

Tres cambios, todos compatibles hacia atrás para quien consume:

- **El sobre `AsynchronousMessage` lleva dos claves más**: `actor` (`{id, username, kind}`, con
  `kind` `PERSON`, `SERVICE` o `SYSTEM`, tal como lo clasifica este servicio al escribir el evento) y
  `correlationId` (el `X-Correlation-Id` de la petición, o el `jobId` del trabajo en segundo plano
  que escribió el evento). **Solo añade claves**: `messageHash` sigue siendo la huella de las siete
  originales, y un consumidor que las ignore no nota nada. Detalle en §2.1.
- **Nace el evento `job.finished`**, por un exchange nuevo, `mto.configuration.exchange`, con clave
  `mto.configuration.job.finished` y `data` en la forma `DomainEvent`. Va aparte del exchange de
  datos maestros para que quien escucha `mto.master-data.#` no reciba nada que no sea un dato
  maestro. Detalle en §2.6.
- **Este servicio deja de declarar colas.** Las cuatro de `mto.master-data.*.queue` no las leía
  nadie: cada consumidor real (`mto-stock`, `mto-maintenance`, `mto-notification`) ya declara la
  suya. Hay que borrarlas del broker a mano, porque nadie más las va a tocar: §9.1.

Un ejemplo completo por evento, comprobado contra el código, en `docs/messaging/examples/`.

## ⚠️ El evento `section-insulator` crece

El aislador de sección gana `kp`, `installationType`, `track`, `connectedTrack` y la lista
`switches` con sus agujas. **Solo añade claves**, así que es compatible hacia atrás; el detalle
está en «Cambios en el contrato del evento `section-insulator`», más abajo.

`mto-maintenance` sí los usa —los lleva a `catenary_asset` y a `catenary_asset_switch`—, y
`mto-stock` sigue registrando el evento sin tratarlo.

## ⚠️ Cambio de contrato de los eventos `profile` y `track`

En `profile`, las claves `sectioning`, `anchorage` y `sectioningFeeding` (objetos) pasan a
`sectionings`, `anchorages` y `sectioningFeedings` (**listas**), porque un perfil puede llevar
varios de cada uno a la vez.

En `track`, la clave `station` (objeto) pasa a `stations` (**lista**): una vía larga atraviesa
varias estaciones sin dejar de ser una vía. Y donde una vía viaja anidada dentro de otro evento
—`profile` y `executionPackage`—, su `stationId` pasa a `stationIds`.

El evento `profile` gana además `orderInTrack`, la posición del perfil a lo largo de la vía.

`mto-stock` consume estos eventos pero solo los registra, así que no hay nada que cambiar allí;
queda escrito porque el contrato lo posee este repositorio.

## 1. Conceptos Fundamentales

### ¿Por qué Transactional Outbox?
En sistemas distribuidos, es crítico que la actualización de la base de datos y el envío de un mensaje a un broker (RabbitMQ) ocurran de forma atómica. Si enviamos el mensaje antes de confirmar la transacción de la DB, y esta falla, habremos enviado un evento falso. Si lo enviamos después y RabbitMQ falla, habremos perdido el evento.

El patrón **Outbox** soluciona esto guardando el mensaje en una tabla de la misma base de datos dentro de la misma transacción de negocio. Un proceso independiente (Scheduler) se encarga de leer esa tabla y enviar los mensajes a RabbitMQ.

### Principios de Diseño
- **Genérico y Extensible**: No es necesario crear lógica de mensajería por cada nueva entidad.
- **Integridad**: Cada mensaje incluye un hash SHA-256 para verificar que no ha sido alterado.
- **Trazabilidad**: Uso de `operationId` (UUID) para seguir el flujo de un evento en todo el sistema.
- **Resiliencia**: Reintentos con backoff exponencial acotado en caso de fallos de red o del broker.
- **Exactamente una publicación por réplica**: el relay reclama los mensajes con `FOR UPDATE SKIP LOCKED`, de modo que varias instancias reparten el trabajo en lugar de duplicarlo.
- **Confirmación del broker**: un mensaje solo pasa a `PUBLISHED` cuando RabbitMQ lo ha aceptado (publisher confirms).
- **Contexto**: cada mensaje dice quién pidió el cambio (`actor`) y bajo qué petición o trabajo (`correlationId`), leídos en el único momento en que existen: al escribir el outbox.

---

## 2. Detalle de Paquetes y Clases

### 2.1. Núcleo de Mensajería (`com.alejandro.mtoconfiguration.core.messaging`)
Este paquete define el contrato corporativo de los mensajes asíncronos.

*   **`AsynchronousMessage<T>` (Record)**:
    Es el contenedor estándar para todos los mensajes.
    - `operationId`: Identificador único de la operación.
    - `referenceId`: Referencia funcional (ej: `station-1`).
    - `origin`: Nombre del servicio que genera el mensaje.
    - `creationDate`: Timestamp de creación.
    - `eventType`: Nombre lógico del evento (ej: `MASTER_DATA_STATION_CREATED`).
    - `data`: El contenido real del evento (payload).
    - `messageHash`: Huella del contenido en el momento de crearlo. **No es una firma** y no sirve para verificar integridad en destino: ver sección 13.
    - `actor`: Quién pidió la operación, como `{ "id", "username", "kind" }`: el `sub` y el `preferred_username` del token, y `kind` `PERSON` (una persona), `SERVICE` (la cuenta de servicio de otro servicio del dominio, `service-account-*`) o `SYSTEM` (nadie autenticado: un proceso de fondo, con `id` y `username` nulos). Lo clasifica este servicio porque es el único que tiene el token delante.
    - `correlationId`: El `X-Correlation-Id` de la petición que causó el evento, o el `jobId` del trabajo en segundo plano que lo escribió (una importación de perfiles emite miles de eventos con el mismo). Nulo fuera de ambos.

    Las dos últimas se añadieron después: **no entran en `messageHash`**, y un consumidor anterior las ignora. En el contrato son opcionales; lo que este servicio publica hoy las lleva siempre (`actor` nunca es nulo: lo que no tiene usuario se dice como `SYSTEM`).

*   **`AsynchronousMessageFactory`**:
    Componente encargado de construir instancias de `AsynchronousMessage`. Automatiza la generación de UUIDs, timestamps, el cálculo del hash inicial y el contexto (`actor` y `correlationId`), que lee de `MessageContextResolver` en el momento de crear el mensaje: dentro de la transacción de negocio, que es cuando el `SecurityContext` de la petición (o el propagado al hilo de un trabajo) y el MDC todavía existen. El relay que publica después corre en un hilo del planificador, sin ninguno de los dos.

*   **`MessageContextResolver`**:
    Resuelve el actor desde `CurrentUserService` (persona, cuenta de servicio o sistema) y el `correlationId` desde el MDC, donde lo dejan `CorrelationIdFilter` en una petición y `JobCorrelation` en un trabajo.

*   **`DomainEvent` (Record)**:
    El `data` de los eventos que **no** son de datos maestros: `{ entityName, entityId, eventName, values }`. Los datos maestros siguen viajando como `MasterDataChangedEvent`, cuya `operation` es un enumerado cerrado que los consumidores deserializan tal cual; lo demás que este servicio cuenta lleva el nombre del evento en texto, y es la forma que usarán los productores de los otros servicios del dominio.

*   **`ConfigurationRabbitMqNames`**:
    El exchange propio, `mto.configuration.exchange`, y sus claves: `mto.configuration.<entidad>.<evento>` de enrutado y `CONFIGURATION_<ENTIDAD>_<EVENTO>` como `eventType`.

*   **`AsynchronousMessageHashService`**:
    Calcula la huella `messageHash` con SHA-256 sobre el contenido antes de serializarlo. **No** ofrece validación en destino: la tenía y devolvía `false` para mensajes legítimos (ver sección 13). Para verificar de verdad está `MessagePayloadSignature`.

*   **`MessagePayloadSignature`**:
    Firma los **bytes** que viajan y la envía en cabecera. Es la única comprobación que el consumidor puede rehacer. Con secreto configurado es HMAC-SHA256; sin él, SHA-256.

---

### 2.2. Infraestructura Outbox (`com.alejandro.mtoconfiguration.core.outbox`)
Implementa la persistencia y el reenvío de mensajes.

*   **`OutboxMessage` (Entidad)**:
    Representa un registro en la tabla `outbox_message`. Almacena el destino (exchange/routing-key), el payload en formato JSON y el estado del envío.

*   **`OutboxStatus` (Enum)**:
    Define los estados: `PENDING` (pendiente), `IN_PROGRESS` (reclamado por un relay que está intentando publicarlo), `PUBLISHED` (aceptado por RabbitMQ) y `FAILED` (agotados los reintentos).

*   **`OutboxService`**:
    Ofrece el método `save()` para persistir eventos. **Debe llamarse siempre dentro de una transacción `@Transactional`**.

*   **`OutboxPublisherScheduler`**:
    El motor del sistema. Se ejecuta cada pocos segundos (configurable): reclama un lote, lo publica y cierra el estado de cada mensaje. **No es transaccional**: la I/O contra RabbitMQ nunca debe retener una conexión de base de datos.

*   **`OutboxRelayService`**:
    La única frontera transaccional del relay, siempre con transacciones cortas. `claimBatch()` reclama un lote con `FOR UPDATE SKIP LOCKED` y lo deja invisible para el resto de réplicas durante `claim-visibility-timeout`; `markPublished()` y `markFailed()` cierran cada mensaje.

*   **`OutboxRabbitPublisher`**:
    Publica y **espera la confirmación del broker**. Detecta las dos formas de fracaso silencioso: el `nack` y el mensaje no enrutable (`basic.return`, que llega siempre antes del ack). Sin publisher confirms configurados, la aplicación no arranca.

*   **`OutboxRetryPolicy`**:
    Backoff exponencial `initial-retry-delay * 2^(intentos-1)`, acotado a `max-retry-delay` y con jitter para que las réplicas no reintenten a la vez.

*   **`OutboxAdminService` / `OutboxEndpoint`**:
    Estado del outbox y *redrive* de los mensajes `FAILED`, expuestos en `/actuator/outbox` (requiere rol `ADMIN` u `OPS`).

---

### 2.3. Configuración RabbitMQ (`com.alejandro.mtoconfiguration.core.rabbitmq`)
Configuración técnica del broker.

*   **`RabbitMqConfiguration`**:
    Configura el `RabbitTemplate`, los convertidores de JSON (Jackson) y el `RabbitAdmin`. Habilita el soporte de Observabilidad para trazabilidad distribuida.

*   **`RabbitMqProperties`**:
    Clase vinculada a `app.rabbitmq` en el YAML. Permite definir dinámicamente exchanges, colas y bindings sin tocar código Java.

*   **`RabbitMqConstants`**:
    Centraliza nombres de beans y cabeceras comunes de RabbitMQ.

---

### 2.4. Dominio Master Data (`com.alejandro.mtoconfiguration.masterdata.messaging`)
Lógica específica para eventos de entidades de infraestructura.

*   **`MasterDataEventPublisher`**:
    El punto de entrada para los desarrolladores. Proporciona métodos `publishCreated`, `publishUpdated` y `publishDeleted`. Es capaz de procesar cualquier entidad JPA.

*   **`MasterDataEntityNameResolver`**:
    Determina el nombre lógico de la entidad (ej: `Anchorage` -> `anchorage`). Soporta la anotación `@PublishMasterDataEvent`.

*   **`MasterDataEntityIdResolver`**:
    Utiliza reflexión para encontrar el valor del campo anotado con `@Id` en cualquier entidad.

*   **`MasterDataEventPayloadExtractor`**:
    Transforma una entidad JPA en un `Map<String, Object>` limpio, eliminando proxies de Hibernate y campos técnicos innecesarios.

*   **`MasterDataRabbitMqNames`**:
    Genera las routing-keys siguiendo el estándar: `mto.master-data.{entity}.{operation}`.

#### Cambios en el contrato del evento `profile`

El payload de `profile` incorpora cinco claves nuevas. **El cambio es compatible hacia atrás**: sólo
añade claves, no renombra ni quita ninguna, y `mto-stock` hoy registra el evento `profile` sin
tratarlo (no tiene `MasterDataEntityHandler` para esa entidad).

| Clave nueva | Tipo | Contenido |
|---|---|---|
| `span` | número o `null` | Vano hasta el perfil siguiente, en metros |
| `heightCantileverSupport` | número o `null` | Altura del soporte de ménsula, en milímetros |
| `poleGaugeLocation` | número o `null` | Separación del poste al gálibo, en milímetros |
| `railPoleDistance` | número o `null` | Distancia carril-poste, en milímetros, **con signo** |
| `sectioningFeeding` | `{ "id", "code" }` o `null` | Elemento de seccionamiento y alimentación |
| `supportType` | `{ "id", "code" }` o `null` | Pieza que sujeta la catenaria en el poste (columna `Supports`) |
| `assemblyConfiguration` | `{ "id", "code" }` o `null` | Configuración de montaje de la catenaria en el apoyo (`C.F.21`, `C.C.2`; desde `V21`, solo la trae el sinóptico de RUBI) |

`sectioningFeeding` sale con `id` **y** `code`, como el resto de LOV del perfil y a diferencia de
`disconnector.disconnectorFunction`, que viaja sólo como `disconnectorFunctionId`: el consumidor
necesita el código para interpretarlo sin resolver la referencia. Ambos apuntan al mismo catálogo,
`DisconnectorFunction`.

Que salga con código obliga a que la relación venga inicializada, así que `sectioningFeeding` entra
en el `@EntityGraph` de `ProfileRepository.findByIdForMessaging`. Es un join a-uno, no multiplica
filas, y `MasterDataPayloadContractIT` sigue exigiendo **una** sentencia por evento.

#### Cambios en el contrato del evento `section-insulator`

El aislador de sección incorpora cinco claves nuevas. **El cambio es compatible hacia atrás**: sólo
añade claves, no renombra ni quita ninguna.

| Clave nueva | Tipo | Contenido |
|---|---|---|
| `kp` | número o `null` | Punto kilométrico del aislador, en **metros** (`110+176` del plano son `110176.000`) |
| `installationType` | `"TRACK_CONNECTION"` / `"IN_TRACK"` / `null` | Si separa dos vías que conectan por una aguja, o está en medio de una sola |
| `track` | `{ "id", "name" }` o `null` | Vía principal |
| `connectedTrack` | `{ "id", "name" }` o `null` | Vía con la que conecta. Nula en un `IN_TRACK` |
| `switches` | lista de `{ "id", "code", "kp", "turnoutDenominator", "turnoutRate", "trackId", "enabled" }` | Las agujas de la conexión, **en orden de KP** |

La tangente del desvío viaja dos veces a propósito: `turnoutDenominator` es el dato —el `9` de
`1:9`, comparable y ordenable— y `turnoutRate` es cómo está escrito en el plano (`"1:9"`), para que
el consumidor no tenga que componer la misma cadena.

Una aguja **deshabilitada viaja igual**, con `"enabled": false`: la colección filtra los borrados
lógicos, no las bajas. Es deliberado y el consumidor cuenta con ello —`mto-maintenance` la guarda
marcada y la imprime `W31 1:9 (out of service)` en el parte de turno—, porque para el equipo que va
de noche no es lo mismo que la aguja no exista a que no pueda contar con ella. Lo que hace
desaparecer una aguja del evento es borrarla, no darla de baja. Es el mismo criterio por el que
`sectioningFeeding` del perfil sale con `id` **y** `code`.

Que el payload lleve las agujas obliga a que la colección venga inicializada, así que `switches`,
`switches.track`, `track` y `connectedTrack` entran en el `@EntityGraph` de
`SectionInsulatorRepository.findByIdForMessaging`. Una colección en el grafo **no** rompe la
propiedad de una sentencia por evento: `ProfileRepository.findByIdForMessaging` ya lleva
`cantilevers` y `cantilevers.steadyArm`, y `MasterDataPayloadContractIT` lo sigue comprobando.

Donde el aislador viaja **anidado** dentro del evento `station`, la copia reducida gana sólo los dos
escalares (`kp`, `installationType`) y **no** las agujas: llevarlas ahí obligaría a meter la
colección en el grafo de mensajería de `Station` y multiplicaría las filas de ese evento por cada
aguja de cada aislador, para un dato que el consumidor ya recibe entero en el evento
`section-insulator`.

#### Cambios en el contrato del evento `disconnector`

El seccionador incorpora cuatro claves nuevas (`V25` y `V26`). **El cambio es compatible hacia
atrás**: sólo añade claves, no renombra ni quita ninguna.

| Clave nueva | Tipo | Contenido |
|---|---|---|
| `normallyOpen` | `true` / `false` / `null` | Estado normal de explotación: `true` normalmente abierto, `false` normalmente cerrado. `null` es «sin dato» |
| `driveType` | `"MOTOR"` / `"MANUAL"` / `null` | Accionamiento: con motor (el círculo del accionamiento en el plano de seccionamiento) o a mano |
| `kp` | número o `null` | KP en **metros** de un seccionador sin poste (`V26`). `null` en uno en un poste: es el de su perfil |
| `track` | `{ "id", "name" }` o `null` | Vía de un seccionador sin poste (`V26`). `null` en uno en un poste: es la de su perfil |

`normallyOpen`, `driveType` y `kp` son columnas de la propia fila, así que, igual que `kp` e
`installationType` del aislador, viajan también en la copia reducida del seccionador dentro de
`station` (`disconnectors[]`), sin una sentencia más; la de `profile` (`disconnector`) lleva las dos
primeras, porque un seccionador colgado de un perfil nunca tiene KP propio. La vía no viaja en las
copias, como la del aislador. Que el evento lleve su nombre obliga a cargarla: `track` entra en el
`@EntityGraph` de `DisconnectorRepository.findByIdForMessaging`, y `MasterDataPayloadContractIT`
sigue exigiendo **una** sentencia por evento.

`profile` no cambia de forma (`{ "id", "profileId", "kp" }` o `null`), pero cambia lo que se puede
esperar de él: desde `V25` el poste es opcional, y un seccionador que no está en un poste lo trae a
`null`. Entonces el KP y la vía son los suyos, `kp` y `track`, que pueden faltar también; nunca vienen
a la vez que un `profile`.

### 2.5. Republicado de lo que ya existe

Los eventos de esta sección solo nacen cuando algo pasa por la capa de servicio. Un consumidor que se
conecta a un dominio **ya poblado** no recibe nada: las filas que ya estaban nunca publicaron, y
nadie va a volver a editarlas para provocarlo. Es lo que le pasó a `mto-maintenance`, que materializa
perfiles, seccionadores y aisladores de sección como `catenary_asset` y nacía con la tabla vacía.

El republicado (`POST /api/v1/configuration/master-data/republish`, ver `README_ASYNC_JOBS.md`)
recorre lo que hay y escribe por cada elemento el mismo evento que habría escrito una edición: misma
relectura con `findByIdForMessaging`, mismo `MasterDataEventPayloadExtractor`, mismo
`MasterDataEventPublisher`, misma fila de `outbox_message`. **El contrato no cambia en nada**, y esa
es la propiedad que importa: un consumidor no puede distinguir un evento republicado de uno real, ni
tiene por qué.

Se publica como `UPDATED`. No hay un valor nuevo en `MasterDataOperation` a propósito: viaja dentro
del payload, así que un valor desconocido rompería a los consumidores ya desplegados al
deserializar.

Lo que el republicado **no** puede garantizar por sí solo es el orden frente a una edición
simultánea. Cada lote lee y escribe dentro de la misma transacción, lo que estrecha la ventana de
todo el trabajo a un lote, pero sin bloqueo pesimista una edición confirmada entre la lectura y el
`INSERT` todavía puede acabar con un `sequence_number` menor que el del republicado y perder frente
a él en la marca de agua del consumidor (§11.4). Conviene lanzarlo en una ventana sin ediciones.

### 2.6. Eventos propios: `job.finished`

Lo que este servicio cuenta de sí mismo no es un dato maestro y no sale por el mismo exchange. Hoy
hay un evento: el final de un trabajo en segundo plano (`README_ASYNC_JOBS.md` §12).

| | |
| :--- | :--- |
| Exchange | `mto.configuration.exchange` (topic, durable) |
| Clave de enrutado | `mto.configuration.job.finished` |
| `eventType` | `CONFIGURATION_JOB_FINISHED` |
| Agregado (`aggregateType`/`aggregateId`) | `job` / el `jobId` |
| `data` | `DomainEvent`: `entityName` `job`, `entityId` el `jobId`, `eventName` `finished`, `values` con `jobId`, `type`, `status`, `createdBy`, `createdAt`, `startedAt`, `finishedAt`, `totalItems`, `processedItems`, `successfulItems`, `failedItems`, `fileName`, `trackId`, `mapperType`, `errorMessage` |
| `actor` | quien lanzó el trabajo (el executor propaga su `SecurityContext` al hilo de fondo) |
| `correlationId` | el propio `jobId` |
| Quién lo consume | `mto-notification`, con su cola `mto.notification.configuration.queue` bindeada a `mto.configuration.#` |

Lo escribe `JobFinishedEventPublisher` desde `AsyncJobStore.markFinished`, **en la misma transacción
`REQUIRES_NEW` que el estado terminal**: o se confirman los dos o ninguno. El detalle de errores por
elemento no viaja (puede pesar megas; para eso está el trabajo, al que el aviso enlaza), un trabajo
rechazado por capacidad no publica nada, y el que cierra el reaper por falta de latido tampoco.
Ejemplo completo en `docs/messaging/examples/job-finished.json`.

Un evento nuevo de este servicio es una llamada más a `AsynchronousMessageFactory` y `OutboxService`
con `ConfigurationRabbitMqNames.routingKey(entidad, evento)` y `eventType(entidad, evento)`, su
ejemplo en `docs/messaging/examples/` y su aviso al principio de este fichero: la cola que lo
recibe la declara el consumidor.

---

## 3. Infraestructura de RabbitMQ (Exchanges, Colas y Dead Letter)

El sistema utiliza una configuración dinámica basada en propiedades para definir la infraestructura de mensajería, asegurando que el broker (RabbitMQ) esté siempre alineado con los requisitos del código.

### 3.1. Exchanges (Intercambiadores)
Este servicio declara dos **Topic Exchange**, y son su contrato como productor:

| Exchange | Qué sale por él | Quién lo escucha |
| :--- | :--- | :--- |
| `mto.master-data.exchange` | Los datos maestros: `mto.master-data.<entidad>.<created\|updated\|deleted>` | `mto-stock`, `mto-maintenance` y `mto-notification`, con `mto.master-data.#` |
| `mto.configuration.exchange` | Lo que el servicio cuenta de sí mismo: hoy `mto.configuration.job.finished` (§2.6) | `mto-notification`, con `mto.configuration.#` |

- **Tipo Topic**: A diferencia de un exchange directo, este permite una distribución selectiva. Los mensajes se envían con una "Routing Key" (ej: `mto.master-data.station.created`) y el exchange los entrega a las colas cuyo "Binding Pattern" coincida.
- **Durabilidad**: Declarados como `durable`, lo que garantiza que la configuración del exchange sobreviva a un reinicio de RabbitMQ.
- **Auto-declaración**: La clase `RabbitMqConfiguration` lee la lista de exchanges desde el YAML y utiliza `RabbitAdmin` para crearlos automáticamente si no existen. Los consumidores los declaran también, con los mismos argumentos: así no importa quién arranca primero.
- Son dos y no uno a propósito: quien escucha `mto.master-data.#` no tiene por qué recibir el final de un trabajo, y un exchange nuevo no obliga a ningún consumidor existente a cambiar nada.

### 3.2. Colas (Queues)
Las colas son los buzones donde residen los mensajes hasta que un consumidor los procesa, y **este
servicio no declara ninguna**: una cola pertenece a quien la consume, que es quien sabe qué TTL, qué
límite y qué tipo necesita, y quien la declara con sus bindings y su dead letter. `app.rabbitmq.queues`
y `app.rabbitmq.bindings` están vacíos, y `ApplicationRabbitMqTopologyTest` lo fija.

Las colas que hoy existen las declaran sus consumidores, cada uno en su repositorio:

| Cola | Binding (Routing Key) | De quién es |
| :--- | :--- | :--- |
| `mto.stock.master-data.queue` | `mto.master-data.#` | `mto-stock` |
| `mto.maintenance.master-data.queue` | `mto.master-data.#` | `mto-maintenance` |
| `mto.notification.master-data.queue` | `mto.master-data.#` | `mto-notification` |
| `mto.notification.configuration.queue` | `mto.configuration.#` | `mto-notification` |

Las cuatro colas `mto.master-data.{events,cache,audit,deleted}.queue` que este servicio declaraba
no las leía nadie, y se borran del broker a mano (§9.1).

Lo que sigue en esta sección y en la 9 describe lo que `RabbitMqConfiguration` sabe hacer con una
cola, por si algún día vuelve a haber una aquí; hoy no aplica a ninguna:

- **Tipo**: `classic` (por defecto) o `quorum`, replicada entre nodos. Ver sección 9.3.
- **Propiedad**: `declare` indica si este servicio crea la cola o pertenece a su consumidor. Ver sección 9.1.
- **Modo Lazy**: ~~configurable vía propiedades~~. **Obsoleto**: RabbitMQ 3.12 y posteriores ignoran `x-queue-mode`, porque las colas clásicas v2 ya escriben a disco por defecto.
- **Persistencia**: Por defecto son `durable`, asegurando que los mensajes no se pierdan si el broker se apaga.

### 3.3. Estrategia de Dead Letter (DLX/DLQ)
Para garantizar la resiliencia, el sistema implementa un mecanismo automático de gestión de errores mediante **Dead Lettering**. Con la lista de colas vacía, aquí no se crea ninguno: cada consumidor crea el de su cola con el mismo esquema.
- **Dead Letter Exchange (DLX)**: Por cada cola configurada con `dead-letter-enabled: true`, el sistema crea un exchange adicional de tipo `direct` con el sufijo `.dlx` (ej: `mto.notification.master-data.queue.dlx`, que crea `mto-notification` con el mismo esquema).
- **Dead Letter Queue (DLQ)**: Se crea una cola espejo con el sufijo `.dlq` (ej: `mto.notification.master-data.queue.dlq`) conectada al DLX.
- **Flujo de Error**: Cuando un mensaje no puede ser procesado (ej: formato inválido, error persistente en el consumidor o expiración de TTL), RabbitMQ lo mueve automáticamente de la cola principal a la DLQ. Esto permite:
    1. **Aislamiento**: Los mensajes problemáticos no bloquean el procesamiento de los mensajes nuevos.
    2. **Inspección**: Los administradores pueden revisar la DLQ para entender por qué falló el mensaje.
    3. **Recuperación**: Una vez corregido el problema, los mensajes pueden ser reinyectados a la cola principal.

---

## 4. Ciclo de Vida del Mensaje: Proceso Paso a Paso

El flujo de un evento en este módulo sigue un camino estrictamente controlado para evitar la pérdida de datos:

### Fase 1: Captura del Evento (Base de Datos)
1.  **Operación de Negocio**: Un servicio realiza un cambio en una entidad (ej: `repository.save(station)`).
2.  **Llamada al Publisher**: Dentro de la misma transacción `@Transactional`, se llama a `eventPublisher.publishCreated(savedEntity)`.
3.  **Serialización**: El sistema convierte la entidad en un `AsynchronousMessage<T>`, calculando la huella `messageHash`, asignando un `operationId` y leyendo el contexto (`actor` y `correlationId`) mientras todavía existe. La firma verificable se calcula después, al publicar, sobre los bytes reales (sección 13).
4.  **Persistencia Outbox**: El mensaje se guarda en la tabla `outbox_message` con estado `PENDING`. 
    *   *Importante*: Si la transacción de base de datos falla (rollback), el registro en la tabla Outbox nunca se crea, evitando enviar eventos falsos.

### Fase 2: El Relay (Envío al Broker)
5.  **Scheduler**: El `OutboxPublisherScheduler` despierta cada N segundos.
6.  **Reclamo** (transacción corta): `FOR UPDATE SKIP LOCKED` se lleva un lote **disjunto** del que se lleve cualquier otra réplica. Las filas pasan a `IN_PROGRESS` y quedan invisibles durante `claim-visibility-timeout`. Si el proceso muere antes de cerrarlas, otra réplica las recupera al expirar ese plazo.
7.  **Publicación** (fuera de transacción): se envía el JSON al exchange de la fila (`mto.master-data.exchange` o `mto.configuration.exchange`) y se **espera el publisher confirm**.
8.  **Cierre** (transacción corta):
    *   **Éxito**: con el `ack` del broker el mensaje pasa a `PUBLISHED`.
    *   **Fallo temporal**: `nack`, mensaje no enrutable, o sin respuesta dentro de `confirm-timeout`. Vuelve a `PENDING` con el siguiente intento aplazado por backoff exponencial.
    *   **Fallo definitivo**: agotados los intentos, queda en `FAILED`. **No es terminal**: `POST /actuator/outbox` lo devuelve a `PENDING` una vez corregida la causa.

### Fase 3: Enrutado y Consumo
9.  **Distribución**: El Exchange recibe el mensaje y, basándose en la Routing Key, lo deposita en la cola de cada consumidor cuyo binding coincida.
10. **Procesamiento**: El microservicio destino consume el mensaje. Si falla y el sistema está configurado para no reencolar, el mensaje viaja a la **DLQ** para su posterior análisis.

---

## 5. Guía de Implementación

### Paso 1: Preparar la Entidad
Opcionalmente, usa la anotación para definir un nombre personalizado.

```java
@Entity
@PublishMasterDataEvent(name = "estacion")
public class Station {
    @Id
    private Long id;
    private String code;
    // ...
}
```

### Paso 2: Publicar desde el Servicio
Inyecta `MasterDataEventPublisher` y úsalo en tus métodos transaccionales.

```java
@Transactional
public Station update(Long id, Station data) {
    Station entity = repository.findById(id).orElseThrow();
    entity.setName(data.getName());
    
    Station saved = repository.save(entity);
    
    // Esto guarda el evento en la tabla Outbox
    eventPublisher.publishUpdated(saved);
    
    return saved;
}
```

---

## 6. Configuración en `application.yaml`

```yaml
app:
  rabbitmq:
    enabled: true
    exchanges:
      - name: mto.master-data.exchange
        type: topic
      - name: mto.configuration.exchange
        type: topic
    # Ninguna cola ni binding: pertenecen a los consumidores (seccion 9.1)
    queues: []
    bindings: []

  outbox:
    enabled: true
    # Intentos antes de dar el mensaje por perdido. Con backoff exponencial acotado,
    # 20 intentos cubren mas de una hora de broker caido.
    max-attempts: 20
    initial-retry-delay: 5s
    max-retry-delay: 5m
    retry-jitter: 0.2
    publisher-fixed-delay: 5s
    batch-size: 50
    # Tiempo que un mensaje reclamado queda invisible para el resto de replicas
    claim-visibility-timeout: 5m
    confirm-timeout: 10s

spring:
  rabbitmq:
    # Obligatorio: sin publisher confirms el relay no arranca
    publisher-confirm-type: correlated
    publisher-returns: true
```

---

## 7. Monitoreo y Resolución de Problemas

1.  **Tabla Outbox**: Si un mensaje no llega a RabbitMQ, consulta la tabla `outbox_message`. El campo `last_error` indicará el motivo del fallo y `attempts` cuántas veces se ha intentado.
2.  **Logs**: Busca el prefijo `Outbox message publicado y confirmado` para los envíos con ack del broker.
3.  **Dead Letter Queues**: Si RabbitMQ recibe el mensaje pero el consumidor falla repetidamente, el mensaje terminará en la cola `.dlq` correspondiente para inspección manual.

---

## 8. Explotación

### Estado del outbox

```bash
curl -H "Authorization: Bearer $TOKEN" https://.../actuator/outbox
```

```json
{"pending":3,"inProgress":0,"published":15234,"failed":0,"oldestPendingCreatedAt":"2026-08-24T10:15:02Z"}
```

`oldestPendingCreatedAt` es la señal que conviene vigilar: si envejece, el relay está roto, sea cual sea la causa. Una alerta por encima de 60 segundos cubre casi todos los fallos posibles del circuito.

### Reencolar los mensajes fallidos

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"limit": 100}' https://.../actuator/outbox
```

Devuelve a `PENDING` los `FAILED` más antiguos con el contador de intentos a cero. Hacerlo **después** de corregir la causa: si no, vuelven a agotar la ventana de reintentos.

### Requisitos de configuración

| Propiedad | Por qué es obligatoria |
| :--- | :--- |
| `spring.rabbitmq.publisher-confirm-type: correlated` | Sin ella el relay no arranca: no podría distinguir un mensaje aceptado por el broker de uno perdido. |
| `spring.rabbitmq.publisher-returns: true` | Detecta los mensajes que no encajan en ninguna cola. |
| `management.endpoints.web.exposure.include` con `outbox` | Necesario para el endpoint de explotación. |

### Métricas

| Métrica | Qué es |
| :--- | :--- |
| `outbox_pending_oldest_age_seconds` | **La que hay que vigilar.** Antigüedad del pendiente más viejo. Si sube, el circuito está roto, sea cual sea la causa. Alerta por encima de 60s. |
| `outbox_messages_pending` | Pendientes de publicar. |
| `outbox_messages_in_progress` | Reclamados por un relay y aún sin cerrar. |
| `outbox_messages_failed` | Han agotado los reintentos y esperan un redrive. Debería ser 0. |
| `outbox_publish_total{result="success"\|"failure"}` | Ritmo de publicación y de fallos. |

Los gauges se refrescan desde una foto periódica (`app.outbox.metrics-refresh-delay`), **no** en cada scrape: un gauge que consulta la base de datos cada vez que Prometheus pregunta convierte la observabilidad en carga. El total de `PUBLISHED` no se publica como gauge a propósito (contarlo recorre el grueso de la tabla); está bajo demanda en `/actuator/outbox`.

### Purga de mensajes publicados

Sin purga `outbox_message` solo crece: cada cambio de dato maestro deja una fila con su JSON, y `bulkCreate`/`bulkUpdate` publican un evento **por entidad**. `OutboxPurgeScheduler` borra los `PUBLISHED` que superan `app.outbox.purge.retention` (7 días por defecto).

Tres decisiones que no son cosméticas:

- **Solo `PUBLISHED`.** Los `FAILED` se quedan: son justo los que hay que mirar, y borrarlos sería tapar el problema. Los `PENDING` todavía no se han enviado.
- **Por lotes, cada uno en su transacción.** Un `DELETE` de millones de filas mantiene una transacción larguísima, hincha el WAL y bloquea el vacuum de la tabla.
- **Con tope de lotes por pasada** (`max-batches-per-run`), para que la primera ejecución sobre una tabla ya enorme no se convierta en un borrado de horas.

### Índices

Los crea `V3__outbox_message_indexes.sql`. Los tres son **parciales**, y ahí está la gracia: `outbox_message` solo crece, pero las filas que consultan el relay, la purga y las métricas son una fracción minúscula del total, de modo que los índices se mantienen diminutos aunque la tabla acumule millones de mensajes.

| Índice | Para qué |
| :--- | :--- |
| `idx_outbox_message_claim` | Reclamo del relay. `FlywayMigrationIT` comprueba con `EXPLAIN` que la consulta puede usarlo. |
| `idx_outbox_message_purge` | Purga por antigüedad de `published_at`. |
| `idx_outbox_message_failed` | Redrive y, sobre todo, la métrica de fallidos, que se consulta cada pocos segundos. |

Si `outbox_message` ya es enorme en producción, conviene crearlos antes a mano con `CREATE INDEX CONCURRENTLY` (que no puede ir dentro de una transacción, y Flyway ejecuta cada migración en una): el `IF NOT EXISTS` hace que entonces la migración no haga nada.

---

## 9. Colas: propiedad, límites y tipo

### 9.1. Una cola pertenece a quien la consume

Este servicio **publica**, no consume: no hay un solo `@RabbitListener` en el repositorio, y **no
declara ninguna cola**. Declaraba cuatro (`mto.master-data.{events,cache,audit,deleted}.queue`) que
no leía nadie: los consumidores reales —`mto-stock`, `mto-maintenance`, `mto-notification`— declaran
cada uno la suya, con sus bindings y su dead letter. Dejar colas huérfanas trae dos problemas:

- **El consumidor es quien sabe lo que necesita** (qué TTL, qué límite, qué tipo de cola). Mientras las declare el productor, esas decisiones se toman en el sitio equivocado.
- **Una cola sin consumidor solo crece.** Bindeada a `mto.master-data.#`, recibe una copia de cada evento y no la vacía nadie; sin límite, acaba llenando el disco del broker, y un broker con el disco lleno bloquea las publicaciones de **todos** los servicios (9.2).

Y si algún día se declarase una cola en los dos lados, valdría lo de siempre: **un desacuerdo rompe el enrutado**. Declarar en los dos lados es idempotente **solo si los argumentos coinciden exactamente**. Si no, el broker responde `PRECONDITION_FAILED (406)` y cierra el canal. `RabbitAdmin` declara en este orden —exchanges, colas, bindings— sobre un único canal, así que los exchanges ya declarados sobreviven, pero se quedan sin declarar el resto de colas del lote y **todos los bindings**. Sin bindings no se enruta nada.

Declarar el **exchange** en ambos lados sí es buena idea: es el contrato del productor, sus argumentos (tipo y durabilidad) casi nunca cambian, y hacerlo en los dos sitios elimina la dependencia de orden en el despliegue. El problema son las colas, cuyos argumentos son justo los que evolucionan.

`declare` sigue existiendo para una cola que se listara aquí sin ser de este servicio (no se declara ni ella, ni su dead letter, ni sus bindings):

```yaml
app:
  rabbitmq:
    defaults:
      declare-queues: true      # global
    queues:
      - name: cola.de.otro.servicio
        declare: false          # esta cola es de otro servicio
```

#### Borrar las colas huérfanas del broker

Quitar las colas del YAML no las quita del broker: una cola durable sobrevive a los reinicios y a
los despliegues hasta que alguien la borra. En cada entorno que arrancó alguna vez con la
configuración anterior hay ocho colas (cuatro principales y sus `.dlq`) y cuatro exchanges `.dlx` que
siguen recibiendo una copia de cada evento y que nadie vacía. Se borran una vez, a mano:

```bash
# En el contenedor del broker de mto-platform: docker compose exec rabbitmq sh -c '...'
for q in events cache audit deleted; do
  rabbitmqctl delete_queue "mto.master-data.$q.queue"
  rabbitmqctl delete_queue "mto.master-data.$q.queue.dlq"
done
# rabbitmqctl no borra exchanges: la API de administracion si (usuario y contrasena del broker)
for q in events cache audit deleted; do
  curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" -X DELETE \
    "http://localhost:15672/api/exchanges/%2F/mto.master-data.$q.queue.dlx"
done
```

Antes de borrar, `rabbitmqctl list_queues name messages consumers` enseña que las cuatro no tienen
ningún consumidor: lo que tengan acumulado son copias de eventos que los consumidores reales ya
recibieron por su propia cola. En el entorno local de `mto-platform`, `docker compose down -v`
(que borra el volumen del broker) lo deja igual de limpio.

Si en algún momento una cola de un consumidor deja de existir, sus mensajes no son enrutables: con `mandatory: true` y publisher returns, el relay lo detecta y marca el mensaje como fallido en lugar de perderlo en silencio.

### 9.2. Los argumentos de una cola existente son inmutables

Esto es lo que más sorpresas da: **no se le pueden añadir argumentos a una cola que ya existe redeclarándola**. Añadir un `max-length` en el YAML a una cola ya creada hace que el broker responda `PRECONDITION_FAILED` y se caiga la declaración entera en el arranque.

Para poner límites a una cola existente se usa una **policy**, que además se puede cambiar en caliente:

```bash
rabbitmqctl set_policy mto-master-data-limits \
  "^mto\.master-data\..*\.queue$" \
  '{"max-length": 100000, "overflow": "reject-publish"}' \
  --apply-to queues
```

`overflow` importa: el valor por defecto de RabbitMQ es `drop-head`, que descarta **los mensajes más antiguos** en silencio — para eventos de datos maestros, eso es pérdida de datos. Con `reject-publish` el publicador recibe un nack, y con el outbox eso es un reintento con backoff en lugar de un evento perdido.

Sin ningún límite, un consumidor parado hace crecer la cola hasta llenar el disco del broker, y **un broker con el disco lleno bloquea las publicaciones de todos los servicios**. El validador avisa al arrancar de cada cola declarada sin límite.

### 9.3. Colas quorum

Las colas actuales son clásicas y sin réplica: si cae el nodo que las aloja, se pierden, por muy `durable` que sean. Las quorum se replican entre nodos.

```yaml
queues:
  - name: mto.<consumidor>.master-data.queue   # en el YAML del consumidor, que es quien la declara
    type: quorum
    delivery-limit: 5     # solo quorum: protección contra mensaje envenenado
```

La DLQ hereda el tipo de su cola principal — de nada sirve replicar la cola de trabajo si los mensajes que fallan acaban en una cola sin réplica, que son justo los que hay que conservar.

**El tipo de una cola no se puede cambiar en caliente.** Migrar una cola existente a quorum es:

1. Parar los consumidores y esperar a que la cola se vacíe (o drenarla a otro sitio).
2. Borrar la cola.
3. Desplegar con `type: quorum`, que la crea replicada.

Mientras la cola no existe, los mensajes no son enrutables y el relay los deja en reintento, así que la ventana es recuperable, pero conviene hacerlo con el outbox vigilado.

`x-queue-mode: lazy` está **obsoleto**: RabbitMQ 3.12 y posteriores lo ignoran, porque las colas clásicas v2 ya escriben a disco por defecto. El validador avisa si alguna cola lo declara.

### 9.4. Qué se valida al arrancar

`RabbitMqTopologyValidator` corre antes de mandar nada al broker. Falla el arranque, con todos los problemas juntos en un solo mensaje, ante:

- Nombres de cola o exchange duplicados.
- Cola quorum `exclusive`, `auto-delete`, no durable o con `lazy`.
- `delivery-limit` en una cola clásica (`x-delivery-limit` solo existe en quorum).
- `overflow` sin ningún límite: el argumento no llegaría a aplicarse nunca.
- Un binding a una cola que no está en `queues`, o que tiene `declare: false`.
- Valores negativos o a cero en los límites.

Y avisa (sin fallar) de colas sin límite, colas sin binding y del `lazy` obsoleto.

`ApplicationRabbitMqTopologyTest` aplica estas mismas comprobaciones a la topología real de `application.yaml`, para que un fallo salga en el build y no en el arranque del entorno.

---

## 10. Trazabilidad distribuida

### 10.1. Por qué el outbox rompe la traza

El outbox parte la traza en dos **por construcción**: el mensaje se escribe dentro de la petición de negocio y se publica segundos o minutos después, desde el hilo del scheduler. Sin nada que los una, el span de la publicación cuelga del planificador y no de la operación que lo originó — y se pierde justo la trazabilidad que el `operationId` del mensaje intenta reconstruir a mano.

La solución es guardar el contexto W3C en la propia fila del outbox:

```
PUT /stations/42  ──┐
                    │ traceparent capturado dentro de la transacción
                    ▼
            outbox_message (trace_parent, trace_state)
                    │
                    │ ...minutos después, hilo del scheduler
                    ▼
            span "outbox publish"  ──►  RabbitMQ  ──►  consumidor
            (mismo trace-id que la petición original)
```

### 10.2. Configuración

Se usa `spring-boot-starter-opentelemetry`, que trae `micrometer-tracing-bridge-otel`, el exportador OTLP **y** los módulos de autoconfiguración de Boot. Esto último importa: igual que pasaba con Flyway, la librería por sí sola no basta — sin `spring-boot-micrometer-tracing-opentelemetry` no habría ni `Tracer` ni `Propagator` y todo esto quedaría inerte.

```yaml
management:
  tracing:
    enabled: ${MTO_TRACING_ENABLED:true}
    sampling:
      probability: ${MTO_TRACING_SAMPLING_PROBABILITY:0.1}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
    metrics:
      export:
        enabled: false    # las métricas van por Prometheus
```

Dos detalles que suelen confundir:

- **Muestrear poco no rompe la cadena.** Un span no muestreado sigue propagando su `traceparent`, con el flag a `00`. La correlación entre servicios se mantiene aunque no se exporte.
- **Las métricas siguen yendo por Prometheus.** El starter de OpenTelemetry arrastra también un registro OTLP de métricas; se desactiva su exportación explícitamente para no duplicar el camino que ya se montó en `/actuator/prometheus`.

**Dónde acaban las trazas.** El colector es un **Jaeger** *all-in-one* que levanta `mto-platform` (servicio `jaeger`): habla OTLP de forma nativa, así que no hace falta un OpenTelemetry Collector delante. Interfaz en http://localhost:16686, ingesta OTLP HTTP en el `4318`. Lo comparten los tres servicios del dominio, que lo alcanzan por el nombre `otel.mto.local` resuelto por el host — el mismo idioma que `auth.mto.local` para Keycloak. Uno solo para los tres: un colector por servicio dejaría cada traza partida entre visores distintos.

**Y el otro extremo ya escucha.** Desde que `mto-stock` lleva el mismo starter y activa `spring.rabbitmq.listener.simple.observation-enabled`, las cabeceras que escribe `OutboxRabbitPublisher` (ver 10.4) no acaban en el vacío: el consumidor las lee y **continúa** la traza en lugar de abrir una nueva. Una operación que empieza como una petición HTTP en `mto-gateway` y termina cambiando una fila en `mto-stock` es **una sola traza**, salto por la cola incluido.

### 10.3. Qué pasa sin trazabilidad

Si `management.tracing.enabled=false` no hay beans `Tracer` ni `Propagator`, y el outbox usa `NoOpOutboxTracing`: no captura nada, no abre ningún ámbito y publica exactamente igual. **Publicar eventos es el trabajo del outbox; trazarlos es un extra que no puede condicionar su arranque.** Lo mismo vale para un contexto corrupto en la tabla: se registra un aviso y el mensaje sale.

Las columnas `trace_parent` y `trace_state` admiten nulos por lo mismo: los mensajes anteriores a `V4` no lo tienen, y tampoco lo tendrán los eventos generados fuera de una petición trazada (una tarea programada, por ejemplo).

### 10.4. La cabecera del mensaje

`OutboxRabbitPublisher` hace dos cosas:

1. **Abre un ámbito** con el contexto guardado, de modo que el span `outbox publish` pertenece a la traza original en lugar de al scheduler.
2. **Escribe `traceparent`/`tracestate` en las cabeceras AMQP** como suelo de propagación. Con la instrumentación de Spring AMQP activa (`setObservationEnabled(true)`), esa cabecera se sobrescribe con la del span hijo — mismo `trace-id`, y enlaza mejor todavía. Sin instrumentación, es lo que hace que el consumidor siga perteneciendo a la traza original en vez de empezar una nueva.

---

## 11. Orden de los eventos

### 11.1. El problema, y por qué apareció al arreglar otra cosa

Si el mensaje A de `station-5` falla y se reprograma con backoff, el mensaje B de `station-5` —posterior— se publica antes. El consumidor aplica el cambio **viejo encima del nuevo** y el dato maestro queda mal, en silencio.

Lo interesante es que **antes esto casi no se manifestaba**, y no porque el sistema fuera correcto: un mensaje que no se confirmaba se marcaba `PUBLISHED` y se perdía. Al perderse el viejo llegaba solo el nuevo, y el resultado final salía bien de casualidad. Al arreglar la pérdida (publisher confirms + reintentos), A sí llega — y puede pisar a B.

Es el precio de la corrección anterior, y hay que pagarlo aquí.

### 11.2. Número de secuencia

`V5` añade `sequence_number`, un contador monótono que **asigna la base de datos** (`DEFAULT nextval(...)`). Lo asigna la base y no la aplicación a propósito: con varias réplicas escribiendo a la vez, es el único sitio donde el contador es de verdad único y creciente.

De paso resuelve el orden no determinista: el relay ordenaba por `created_at`, y un `bulkCreate` genera N mensajes con el mismo instante — el orden entre ellos quedaba al azar. Además `created_at` lo pone la aplicación, así que un reloj desajustado entre réplicas podía darlo del revés.

### 11.3. Retención por agregado

El reclamo no entrega un mensaje si su agregado tiene otro anterior sin publicar:

```sql
and not exists (
    select 1 from outbox_message anterior
    where anterior.aggregate_type = o.aggregate_type
      and anterior.aggregate_id   = o.aggregate_id
      and anterior.status in ('PENDING', 'IN_PROGRESS')
      and anterior.sequence_number < o.sequence_number
)
```

Dos decisiones dentro de esa consulta:

- **Solo retienen `PENDING` e `IN_PROGRESS`**, que son los que todavía van a llegar.
- **Un `FAILED` no retiene.** Ya no se va a publicar por sí mismo, y dejarlo bloquear pararía el agregado de forma indefinida — peor que el desorden. Si luego se hace redrive de ese mensaje, vuelve a `PENDING` con su secuencia original, así que se ordena solo.

El coste: un mensaje atascado frena a los de **su** agregado, solo a esos. El resto del circuito sigue. Se puede desactivar con `app.outbox.strict-ordering-per-aggregate: false` para vaciar un atasco a costa del orden, pero por defecto pesa más no corromper el dato.

### 11.4. Defensa en profundidad para el consumidor

Cada mensaje viaja con la cabecera `sequenceNumber`. El relay ya publica en orden, pero la entrega es *at-least-once* y un redrive puede reenviar algo antiguo: con ese número, el consumidor puede descartar lo que sea anterior a lo que ya aplicó para ese agregado. No es obligatorio, pero es la red que cubre lo que el orden en origen no puede.

### 11.5. Índices

`V5` sustituye el índice de reclamo (ahora sobre `sequence_number`) y añade `idx_outbox_message_aggregate` sobre `(aggregate_type, aggregate_id, sequence_number)`, que es el que resuelve la retención sin recorrer la tabla. Ambos parciales sobre `PENDING`/`IN_PROGRESS`, así que siguen siendo diminutos.

---

## 12. Latencia y configuración del consumidor

### 12.1. Publicación inmediata al confirmar

El sondeo cada 5 segundos es la red de seguridad, pero también era el **suelo de latencia de todos los eventos**: un cambio de dato maestro tardaba entre 0 y 5 segundos en salir por motivos que no tienen nada que ver con el negocio.

Ahora `OutboxService.save()` publica un evento de Spring y un `@TransactionalEventListener(AFTER_COMMIT)` despierta al relay. `AFTER_COMMIT` y no antes: publicar con la transacción aún abierta significaría anunciar un cambio que después puede hacer rollback — que es exactamente la razón por la que existe el outbox.

Dos detalles del despertador (`OutboxDispatchTrigger`):

- **Agrupa.** Un `bulkCreate` de mil entidades escribe mil mensajes y pide mil despertares; basta con uno.
- **Libera el indicador ANTES de ejecutar.** Así lo que se guarde mientras el relay trabaja provoca otra pasada, en vez de quedarse esperando al siguiente sondeo — que es justo la latencia que se quiere quitar.

Corre en un hilo propio (`outbox-dispatch`), de modo que la petición HTTP no paga la publicación, y cualquier error se traga y se registra: el planificador vuelve a pasar de todas formas. Se desactiva con `app.outbox.immediate-dispatch: false`.

### 12.2. La configuración del listener ya se aplica

Este servicio declaraba a mano un bean de `SimpleRabbitListenerContainerFactory`, y eso **sustituye** al que crea Spring Boot. Como no pasaba por el `SimpleRabbitListenerContainerFactoryConfigurer`, toda la configuración de `spring.rabbitmq.listener.simple.*` se descartaba en silencio: el bloque de reintentos, el backoff y el `acknowledge-mode` estaban escritos en `application.yaml` sin que nadie los leyera. Un consumidor que fallara no habría reintentado nunca las 3 veces configuradas.

Es un fallo que no se ve, porque la configuración está ahí puesta y parece activa.

```java
configurer.configure(factory, connectionFactory);   // primero el YAML de Boot
factory.setMessageConverter(rabbitMessageConverter); // después lo del servicio
```

De paso desaparece `app.rabbitmq.listener`: duplicaba una a una las propiedades de Boot (`concurrent-consumers` ↔ `concurrency`, `prefetch-count` ↔ `prefetch`…), y esa duplicación era justo la que hacía creer que algo estaba configurado cuando no lo estaba. Esos valores viven ahora donde Boot los lee:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 1
        max-concurrency: 5
        prefetch: 10
        default-requeue-rejected: false
        receive-timeout: 1s
        acknowledge-mode: auto
        retry:
          enabled: true
          max-retries: 3
```

**Si tu servicio consumidor también declara su propia factory, revisa lo mismo** — es fácil que el patrón esté copiado.

---

## 13. Integridad de los mensajes

### 13.1. Lo que había, y por qué no funcionaba

El `messageHash` que viaja **dentro** del payload se calcula sobre el objeto **antes** de serializarlo. Para comprobarlo, el consumidor tiene que deserializar y volver a serializar — y esa ida y vuelta **no es la identidad**.

Medido sobre payloads reales de este dominio:

| Contenido | ¿El consumidor puede validar? |
| :--- | :--- |
| String, Long, Boolean | ✅ |
| `BigDecimal 1.5` | ✅ |
| **`BigDecimal 1.50`** | ❌ vuelve como `1.5` |
| **`BigDecimal 2.00`** | ❌ vuelve como `2` |
| **anidado con decimales** | ❌ |

No es un caso rebuscado: `Cantilever` tiene **seis** campos `BigDecimal` y `Profile` tiene el `kp`. Los decimales llegan de la base de datos con su escala, así que un `1.50` es lo normal.

Existía un `isValid()` que prometía esa verificación y **devolvía `false` para mensajes perfectamente legítimos**. Se ha eliminado: un método que dice que un mensaje bueno es malo es peor que no tenerlo.

Y aunque la ida y vuelta funcionase, un SHA-256 **sin secreto no protege de manipulación**: quien altere el mensaje recalcula el hash y listo.

### 13.2. Lo que hay ahora

La firma va sobre **los bytes que se envían**, en dos cabeceras:

| Cabecera | Contenido |
| :--- | :--- |
| `messageSignature` | La firma en hexadecimal |
| `messageSignatureAlgorithm` | `HMAC-SHA256` o `SHA-256` |

El consumidor firma los bytes que ha recibido y compara. **No deserializa nada**, así que los decimales dan igual.

```java
@RabbitListener(queues = "...")
public void onMessage(Message message,
                      @Header("messageSignature") String firma) {

    if (!signature.verify(message.getBody(), firma)) {
        throw new IllegalStateException("Firma no valida");
    }
    // ...
}
```

### 13.3. El secreto

```yaml
app:
  messaging:
    signature:
      secret: ${MTO_MESSAGING_SIGNATURE_SECRET:}
```

- **Con secreto** → HMAC-SHA256. Protege de manipulación: sin conocer el secreto no se puede producir una firma válida, por mucho que se conozca el algoritmo.
- **Sin secreto** → SHA-256. Detecta corrupción, **no** manipulación. Se registra un aviso al arrancar para que no se confunda una cosa con la otra.

Va vacío por defecto porque repartir un secreto entre servicios es una decisión de explotación; exigirlo impediría arrancar a quien no lo necesite. Si lo activáis, el consumidor necesita **el mismo valor**.

### 13.4. Qué pasa con el `messageHash`

Se queda en el payload: quitarlo cambiaría el contrato del mensaje y no hace falta para nada de esto. Pero ya está documentado por lo que es — **una huella del contenido, útil para correlacionar, no una garantía de integridad**. Eliminarlo es una limpieza para cuando toque coordinar un cambio de contrato con los consumidores.
