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
- **Resiliencia**: Gestión automática de reintentos con backoff exponencial en caso de fallos de red o del broker.

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
    Define los estados: `PENDING` (pendiente), `PUBLISHED` (enviado), `FAILED` (fallido tras agotar reintentos).

*   **`OutboxService`**:
    Ofrece el método `save()` para persistir eventos. **Debe llamarse siempre dentro de una transacción `@Transactional`**.

*   **`OutboxPublisherScheduler`**:
    El motor del sistema. Se ejecuta cada pocos segundos (configurable) y busca mensajes `PENDING`. Realiza el envío real a RabbitMQ y actualiza el estado. Si falla, programa el siguiente intento aplicando un retardo creciente.

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

## 3. Topología de RabbitMQ

El sistema utiliza un **Topic Exchange** llamado `mto.master-data.exchange`. Esto permite una gran flexibilidad en el enrutado.

| Cola | Binding (Routing Key) | Propósito |
| :--- | :--- | :--- |
| `mto.master-data.events.queue` | `mto.master-data.#` | Procesamiento general de cambios. |
| `mto.master-data.cache.queue` | `mto.master-data.#` | Invalidación/Refresco de cachés distribuidas. |
| `mto.master-data.audit.queue` | `mto.master-data.#` | Registro histórico de auditoría técnica. |
| `mto.master-data.deleted.queue` | `mto.master-data.*.deleted` | Lógica específica para limpieza de datos eliminados. |

---

## 4. Guía de Implementación

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

## 5. Configuración en `application.yaml`

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
    max-attempts: 10
    initial-retry-delay: 5s
    publisher-fixed-delay: 5000 # ms
```

---

## 6. Monitoreo y Resolución de Problemas

1.  **Tabla Outbox**: Si un mensaje no llega a RabbitMQ, consulta la tabla `outbox_message`. El campo `last_error` indicará el motivo del fallo y `attempts` cuántas veces se ha intentado.
2.  **Logs**: Busca el prefijo `Outbox message published` para confirmar envíos exitosos.
3.  **Dead Letter Queues**: Si RabbitMQ recibe el mensaje pero el consumidor falla repetidamente, el mensaje terminará en la cola `.dlq` correspondiente para inspección manual.
