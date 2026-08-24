# Arquitectura de Mensajería y Eventos (Patrón Transactional Outbox)

Este documento proporciona una guía detallada sobre la arquitectura de mensajería asíncrona implementada en el módulo `mto-configuration`. El sistema está diseñado para ser robusto, escalable y garantizar la integridad de los datos mediante el patrón **Transactional Outbox**.

---

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
    - `messageHash`: Firma digital del mensaje para asegurar integridad.

*   **`AsynchronousMessageFactory`**:
    Componente encargado de construir instancias de `AsynchronousMessage`. Automatiza la generación de UUIDs, timestamps y el cálculo del hash inicial.

*   **`AsynchronousMessageHashService`**:
    Utiliza Jackson para serializar el contenido y generar un hash SHA-256. Proporciona métodos para calcular y validar la integridad de los mensajes recibidos.

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

---

## 3. Infraestructura de RabbitMQ (Exchanges, Colas y Dead Letter)

El sistema utiliza una configuración dinámica basada en propiedades para definir la infraestructura de mensajería, asegurando que el broker (RabbitMQ) esté siempre alineado con los requisitos del código.

### 3.1. Exchanges (Intercambiadores)
El punto central de recepción de mensajes es el **Topic Exchange** llamado `mto.master-data.exchange`.
- **Tipo Topic**: A diferencia de un exchange directo, este permite una distribución selectiva. Los mensajes se envían con una "Routing Key" (ej: `mto.master-data.station.created`) y el exchange los entrega a las colas cuyo "Binding Pattern" coincida.
- **Durabilidad**: Declarado como `durable`, lo que garantiza que la configuración del exchange sobreviva a un reinicio de RabbitMQ.
- **Auto-declaración**: La clase `RabbitMqConfiguration` lee la lista de exchanges desde el YAML y utiliza `RabbitAdmin` para crearlos automáticamente si no existen.

### 3.2. Colas (Queues)
Las colas son los buzones donde residen los mensajes hasta que un consumidor los procesa.
- **Nomenclatura**: Siguen el estándar `mto.master-data.{propósito}.queue`.
- **Tipo**: `classic` (por defecto) o `quorum`, replicada entre nodos. Ver sección 9.3.
- **Propiedad**: `declare` indica si este servicio crea la cola o pertenece a su consumidor. Ver sección 9.1.
- **Modo Lazy**: ~~configurable vía propiedades~~. **Obsoleto**: RabbitMQ 3.12 y posteriores ignoran `x-queue-mode`, porque las colas clásicas v2 ya escriben a disco por defecto.
- **Persistencia**: Por defecto son `durable`, asegurando que los mensajes no se pierdan si el broker se apaga.

**Topología de Colas Estándar:**

| Cola | Binding (Routing Key) | Propósito |
| :--- | :--- | :--- |
| `mto.master-data.events.queue` | `mto.master-data.#` | Procesamiento general de cambios. |
| `mto.master-data.cache.queue` | `mto.master-data.#` | Invalidación/Refresco de cachés distribuidas. |
| `mto.master-data.audit.queue` | `mto.master-data.#` | Registro histórico de auditoría técnica. |
| `mto.master-data.deleted.queue` | `mto.master-data.*.deleted` | Lógica específica para limpieza de datos eliminados. |

### 3.3. Estrategia de Dead Letter (DLX/DLQ)
Para garantizar la resiliencia, el sistema implementa un mecanismo automático de gestión de errores mediante **Dead Lettering**.
- **Dead Letter Exchange (DLX)**: Por cada cola configurada con `dead-letter-enabled: true`, el sistema crea un exchange adicional de tipo `direct` con el sufijo `.dlx` (ej: `mto.master-data.events.queue.dlx`).
- **Dead Letter Queue (DLQ)**: Se crea una cola espejo con el sufijo `.dlq` (ej: `mto.master-data.events.queue.dlq`) conectada al DLX.
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
3.  **Serialización**: El sistema convierte la entidad en un `AsynchronousMessage<T>`, calculando un hash SHA-256 para integridad y asignando un `operationId`.
4.  **Persistencia Outbox**: El mensaje se guarda en la tabla `outbox_message` con estado `PENDING`. 
    *   *Importante*: Si la transacción de base de datos falla (rollback), el registro en la tabla Outbox nunca se crea, evitando enviar eventos falsos.

### Fase 2: El Relay (Envío al Broker)
5.  **Scheduler**: El `OutboxPublisherScheduler` despierta cada N segundos.
6.  **Reclamo** (transacción corta): `FOR UPDATE SKIP LOCKED` se lleva un lote **disjunto** del que se lleve cualquier otra réplica. Las filas pasan a `IN_PROGRESS` y quedan invisibles durante `claim-visibility-timeout`. Si el proceso muere antes de cerrarlas, otra réplica las recupera al expirar ese plazo.
7.  **Publicación** (fuera de transacción): se envía el JSON al exchange `mto.master-data.exchange` y se **espera el publisher confirm**.
8.  **Cierre** (transacción corta):
    *   **Éxito**: con el `ack` del broker el mensaje pasa a `PUBLISHED`.
    *   **Fallo temporal**: `nack`, mensaje no enrutable, o sin respuesta dentro de `confirm-timeout`. Vuelve a `PENDING` con el siguiente intento aplazado por backoff exponencial.
    *   **Fallo definitivo**: agotados los intentos, queda en `FAILED`. **No es terminal**: `POST /actuator/outbox` lo devuelve a `PENDING` una vez corregida la causa.

### Fase 3: Enrutado y Consumo
9.  **Distribución**: El Exchange recibe el mensaje y, basándose en la Routing Key, lo deposita en una o varias colas (Audit, Events, Cache, etc.).
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
    queues:
      - name: mto.master-data.events.queue
        dead-letter-enabled: true
      # ... otras colas
    bindings:
      - queue: mto.master-data.events.queue
        exchange: mto.master-data.exchange
        routing-key: mto.master-data.#

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

Este servicio **publica**, no consume: no hay un solo `@RabbitListener` en el repositorio. Sin embargo declara las cuatro colas de datos maestros, que consumen otros servicios. Eso trae dos problemas:

- **El consumidor es quien sabe lo que necesita** (qué TTL, qué límite, qué tipo de cola). Mientras las declare el productor, esas decisiones se toman en el sitio equivocado.
- **Un desacuerdo tumba toda la topología.** Si otro servicio declara `mto.master-data.audit.queue` con un argumento distinto, el broker responde `PRECONDITION_FAILED (406)` y Spring aborta el bloque entero de declaraciones — incluido el exchange que sí es de este servicio.

Por eso existe `declare`:

```yaml
app:
  rabbitmq:
    defaults:
      declare-queues: true      # global
    queues:
      - name: mto.master-data.audit.queue
        declare: false          # esta cola es de otro servicio
```

Con `declare: false` no se declara ni la cola, ni su dead letter, ni sus bindings.

**Sigue en `true`** porque las colas ya existen en los entornos y las consumen otros servicios: pasarlo a `false` hay que coordinarlo. El traspaso es:

1. El servicio consumidor empieza a declarar la cola, su DLX/DLQ y su binding, **con exactamente los mismos argumentos** que usa hoy este servicio (si no, `PRECONDITION_FAILED` en el consumidor).
2. Se despliega el consumidor y se comprueba que la cola sigue viva.
3. Se pone `declare: false` aquí y se despliega.

Si en algún momento la cola deja de existir, los mensajes no son enrutables: con `mandatory: true` y publisher returns, el relay lo detecta y marca el mensaje como fallido en lugar de perderlo en silencio.

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
  - name: mto.master-data.events.queue
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

### 10.3. Qué pasa sin trazabilidad

Si `management.tracing.enabled=false` no hay beans `Tracer` ni `Propagator`, y el outbox usa `NoOpOutboxTracing`: no captura nada, no abre ningún ámbito y publica exactamente igual. **Publicar eventos es el trabajo del outbox; trazarlos es un extra que no puede condicionar su arranque.** Lo mismo vale para un contexto corrupto en la tabla: se registra un aviso y el mensaje sale.

Las columnas `trace_parent` y `trace_state` admiten nulos por lo mismo: los mensajes anteriores a `V4` no lo tienen, y tampoco lo tendrán los eventos generados fuera de una petición trazada (una tarea programada, por ejemplo).

### 10.4. La cabecera del mensaje

`OutboxRabbitPublisher` hace dos cosas:

1. **Abre un ámbito** con el contexto guardado, de modo que el span `outbox publish` pertenece a la traza original en lugar de al scheduler.
2. **Escribe `traceparent`/`tracestate` en las cabeceras AMQP** como suelo de propagación. Con la instrumentación de Spring AMQP activa (`setObservationEnabled(true)`), esa cabecera se sobrescribe con la del span hijo — mismo `trace-id`, y enlaza mejor todavía. Sin instrumentación, es lo que hace que el consumidor siga perteneciendo a la traza original en vez de empezar una nueva.
