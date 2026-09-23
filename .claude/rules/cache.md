---
paths:
  - "src/main/java/com/alejandro/mtoconfiguration/configuration/cache/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/service/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/model/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/cache/**/*.java"
---
# Caché (Redis)

- Solo Redis (`spring.cache.type: redis`). Los nombres de caché salen de `CacheNames`, cada uno con su
  TTL en `RedisCacheConfig` y `cache.redis.*` (`normal:item` 6 h, `normal:list` 30 min,
  `normal:page` y `normal:search` 10 min, `lov:*` 24 h). `cacheNames` no admite SpEL: un TTL distinto
  es un nombre distinto.
- La clave la genera `redisCacheKeyGenerator` como `<ClaseDelServicio>:<método>:<parámetros>`, con el
  prefijo `mto-configuration::<caché>::`. La invalidación borra por el nombre del servicio, así que una
  entrada con otra clave no se vacía nunca. Cuando la caché vive en otro bean, la clave se construye
  con `RedisCacheKeyGenerator.buildKey(this, …)`, como en `BaseService.findAll`. Lo fija
  `RedisCacheKeyGeneratorTest`.
- `@Cacheable` en un método que se llama desde su propia clase no hace nada (autoinvocación): el
  método cacheado vive en otro bean (`PageCacheService`, `LovReferenceResolver`,
  `TrackSchematicService`).
- Lo que se guarda tiene que poder leerse. El serializador usa tipado por defecto restringido a
  `cache.redis.allowed-subtypes` (el paquete del proyecto, `java.util`, `java.time`,
  `org.springframework.data.domain`), y escribir funciona aunque luego leer falle:
  - nada de `java.math.BigDecimal`: al leer, el validador de tipos lo rechaza. Los decimales viajan
    como texto (`ProfileDTO.kp`, `DisconnectorDTO.profileKp`, el esquema de vía);
  - nada de listas inmutables (`List.of`, `Stream.toList()`): se escriben sin marcador de tipo y no se
    pueden leer. Siempre `ArrayList`;
  - nada de escalares sueltos: un `Long` vuelve como `Integer` y el proxy lanza `ClassCastException`.
    Se envuelve en un record (`LovReferenceDTO`).

  Lo fija `RedisCacheValueSerializationTest`, que usa el serializador real. Un tipo cacheado nuevo
  añade allí su ida y vuelta.
- Cachear es la excepción. `BaseService.isCacheable()` devuelve `false`, y solo devuelven `true` los
  servicios cuyo DTO no embebe otro DTO de entidad: hoy `SteadyArmService` y `DisconnectorService`. La
  invalidación es por servicio y no por id, así que un DTO con hijos se quedaría obsoleto en cuanto
  cambiase cualquier hijo. Lo fija `CacheableServicesTest`, cuya lista `DECLARADOS_CACHEABLES` tiene
  que coincidir con esos servicios.
- Invalidación: cada escritura publica `CacheEvictionEvent` con el nombre del servicio (los catálogos,
  `LovCacheEvictionEvent`), y `CacheEvictionListener` y `LovCacheEvictionListener` vacían sus cachés y
  las del esquema de vía al confirmar la transacción (`AFTER_COMMIT`), con una sola conexión
  (`RedisCacheEvictService`). Una escritura anidada solo publica el servicio padre: un DTO cacheable
  que copia campos de otra entidad, o cuya fila se escribe anidada desde otro servicio, necesita su
  entrada en `CacheEvictionListener.DEPENDENT_SERVICES`. Lo fija `CacheEvictionListenerTest`, que
  también comprueba que los nombres del mapa existen.
- `TrackSchematicService.getSchematic` es el único DTO con hijos que se cachea (una clave por vía): se
  vacía con cualquier escritura de infraestructura o de catálogo.
- Redis es opcional en ejecución. Si no responde, `ResilientCache` cortocircuita durante
  `cache.redis.degraded-retry-window` (30 s) y se sirve desde la base de datos;
  `ResilientCacheErrorHandler` degrada los fallos de conexión, purga una entrada ilegible y relanza lo
  demás. La invalidación respeta el mismo cortocircuito y guarda lo pendiente para la siguiente
  escritura. Los servicios no capturan excepciones de caché. Lo fijan `ResilientCacheErrorHandlerTest`
  y `RedisCacheEvictServiceTest`.
- Un acierto de caché no se prueba con un IT: los de `RedisCacheIT` están `@Disabled` por
  intermitentes. Se prueba la clave, la serialización y la invalidación con los tests de arriba.
