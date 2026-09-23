---
paths:
  - "src/main/java/com/alejandro/mtoconfiguration/service/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/business/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/validator/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/service/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/validator/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/jobs/**/*.java"
---
# Servicios y validación

- Un maestro es un `CRUDService<DTO, Entity>` (sobre `BaseService`) que aporta su mapper, su
  validador, su repositorio, su repositorio de búsqueda por criterios y su `Business`. Alta,
  modificación, lotes, lectura por id y borrado viven en la base; el servicio concreto solo añade
  consultas propias. Un catálogo es un `AbstractLovCrudService`.
- `BaseService` y `CRUDService` escriben con `jakarta.transaction.Transactional` y su `rollbackOn`;
  el resto del código usa el `@Transactional` de Spring, con `readOnly = true` en las lecturas.
  `spring.jpa.open-in-view` es `false`: recorrer una asociación perezosa fuera de una transacción lanza
  `LazyInitializationException`, así que un método que lee un grafo es transaccional.
- Cada escritura de la base publica dos eventos:
  - el de entidad (`publishEntityCreatedEvent`/`Updated`/`Deleted`), que
    `MasterDataEntityChangedEventListener`, un `@EventListener` síncrono, convierte en fila del outbox
    **dentro de la misma transacción** si la entidad lleva `@PublishMasterDataEvent` (ver
    `messaging.md`);
  - `CacheEvictionEvent` con el nombre del servicio, que vacía sus cachés al confirmar (ver
    `cache.md`).

  Lo que se escribe sin pasar por el servicio no publica ninguno de los dos. Por eso importadores y
  trabajos escriben a través de los servicios.
- El validador devuelve `List<Alert>` y nunca lanza; si hay alertas, `BaseService` lanza
  `ValidationException` (400 `VAL-000`, ver `controllers.md`). Los validadores de maestro extienden
  `NormalEntityValidator` y reparten el trabajo en `validateRequiredFields` (siempre),
  `validateParentReferences` (solo cuando el DTO viaja solo: anidado, la referencia al padre la pone el
  mapper) y `validateNestedDtos`. Cada hijo se valida por su propio id (sin id es un alta), no por la
  operación del padre. El campo de la alerta es la ruta desde la raíz del cuerpo. Lo fijan los
  `*ValidatorTest`, `ChildIdentityTest`, `BulkValidationTest` y `ValidatorBeanContractTest`
  (validadores sin estado y registrados en Spring).
- Una colección de hijos en un `PUT` es el estado final (README_API.md §4). La reconcilian los
  mappers, no el servicio (ver `mappers.md`).
- Bloqueo optimista: `update` y `bulkUpdate` (y los de `AbstractLovCrudService`) comparan el
  `versionNumber` del DTO con el guardado antes de tocar nada (`BaseEntity.validateVersion`), porque
  el `@Version` de JPA no ve el conflicto cuando la entidad se carga en la misma transacción. Si no
  coincide, `ConcurrencyException` (409 `CON-001`) y no se escribe nada. `null` no comprueba nada: el
  importador del maestro y los trabajos escriben así a propósito. Los hijos los compara el mapper (ver
  `mappers.md`). Lo fijan `BaseServiceTest`, `AbstractLovCrudServiceTest`, `BaseEntityTest` y
  `OptimisticLockingIT`.
- La respuesta de una modificación lleva la versión que se acaba de escribir: se hace `flush` antes de
  mapearla (`saveAndFlush`, o `saveAll` y `flush` en un lote). Con un `save` a secas la versión sube al
  confirmar, después de mapear, y el cliente que reenvía la respuesta recibe un 409.
- Borrar es lógico: `CRUDEntity.delete()` marca `deleted = true` y la fila se queda (ver
  `persistence.md`).
- Lo que no existe se lanza como `NotFoundException` (404), también al modificar y al borrar
  (`BaseService.notFound`). Un `BaseException` con un texto libre sale como 500 (ver
  `controllers.md`).
- Los tests de servicio no levantan Spring: JUnit con Mockito (`@ExtendWith(MockitoExtension.class)`)
  sobre los colaboradores.
