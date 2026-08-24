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
- **Modo Lazy**: Configurable vía propiedades. Permite que los mensajes se guarden en disco inmediatamente, ideal para soportar picos de tráfico masivos sin agotar la memoria RAM del broker.
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

### Índice recomendado en `outbox_message`

El relay consulta en cada pasada por estado y fecha de reintento. Sin índice, cada tick es un *seq scan* sobre una tabla que solo crece:

```sql
CREATE INDEX CONCURRENTLY idx_outbox_message_claim
    ON outbox_message (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
```

Un índice parcial se mantiene diminuto aunque la tabla acumule millones de filas publicadas.
