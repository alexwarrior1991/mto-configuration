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
  el resto del código usa el `@Transactional` de Spring, con `readOnly = true` en las lecturas. Las
  cuatro lecturas de `BaseService` (`getById`, `findAll`, `findAll(Pageable)` y `search`: el detalle,
  `/paged`, `/search` y la lista entera de `/async`) también, con su nombre completo, porque el fichero
  importa el de jakarta. `spring.jpa.open-in-view` es `false`: recorrer una asociación perezosa fuera
  de una transacción lanza `LazyInitializationException`, así que un método que lee un grafo es
  transaccional. Con la caché, la carga se hace dentro de `PageCacheService` y la transacción del
  servicio la envuelve. Los tests con una transacción alrededor no lo ven, porque la sesión sigue
  abierta al mapear: lo fijan `GetByIdOutsideTransactionIT` y `FilterOutsideTransactionIT`, que
  llaman al servicio sin ella.
- El historial de una fila lo lee `AuditService` (el único que habla con el `AuditReader` de Envers)
  y lo sirven `BaseService.findRevisions` y `AbstractLovCrudService.findRevisions`: paginado en la
  consulta, la revisión más reciente primero, y con la entidad de cada revisión mapeada a su DTO
  **dentro** de la transacción de la lectura (sus relaciones son perezosas). Un maestro sale con la
  forma de su fila en las listas (`mapToSummaryDTOs`), no con la del detalle. El borrado lógico de
  un maestro es para Envers una modificación, y se lee como `DELETED` (`RevisionOperation.of`).
  Envers no audita el `@Version`: el `versionNumber` de una revisión va a `null`. Sin revisiones,
  `NotFoundException`. Lo fija `EntityRevisionsIT`.
- Cada escritura de la base publica dos eventos:
  - el de entidad (`publishEntityCreatedEvent`/`Updated`/`Deleted`), que
    `MasterDataEntityChangedEventListener`, un `@EventListener` síncrono, convierte en fila del outbox
    **dentro de la misma transacción** si la entidad lleva `@PublishMasterDataEvent` (ver
    `messaging.md`);
  - `CacheEvictionEvent` con el nombre del servicio, que vacía sus cachés al confirmar (ver
    `cache.md`).

  Lo que se escribe sin pasar por el servicio no publica ninguno de los dos. Por eso importadores y
  trabajos escriben a través de los servicios.
- El evento de entidad no es solo el de la raíz. Los hijos que una escritura crea, modifica o quita
  dentro de su padre publican el suyo (README_MESSAGING.md §2.4): `EntityListener`, el listener JPA
  de `BaseEntity`, apunta en `EntityChangeTracker` cada fila que el flush escribe de verdad, y
  `BaseService` las publica (`publishNestedChanges`) **después del flush** y del evento de la raíz,
  que no sale dos veces. Una escritura nueva en `BaseService` se envuelve en
  `trackingNestedChanges`, y lo que escriba después de `publishNestedChanges` no publica. Un hijo
  devuelto sin cambios no genera `UPDATE` y no publica. Lo fijan `EntityChangeTrackerTest`,
  `EntityListenerTest`, `BaseServiceTest` y `NestedChildEventsIT`.
- Una regla que depende de **otras filas** y tiene que valer por todos los caminos (el alta, el lote
  y el hijo que viaja dentro del `PUT` de su padre, que no pasa por el servicio del hijo) no va en un
  validador, que solo ve un DTO: es un `WrittenEntitiesCheck`, un bean que `BaseService` llama
  **justo después de cada flush** y antes de publicar nada, con lo que el flush ha escrito de verdad
  (`EntityChangeTracker.written()`, con la raíz). Si lanza, la transacción se deshace y no sale
  ningún evento. Hoy hay una: `DisconnectorNaturalKeyGuard`, el nombre del seccionador único en su
  paquete, con la misma consulta con la que lo busca el importador
  (`DisconnectorRepository.findByNameInPackage`); lanza un `BaseException` con `BUS-002`, que es un
  409. Lo que cabe en un índice único va en un índice (ver `persistence.md`), no aquí. Lo fijan
  `BaseServiceTest`, `EntityChangeTrackerTest`, `DisconnectorNaturalKeyGuardTest` y `NaturalKeyIT`.
- El validador devuelve `List<Alert>` y nunca lanza; si hay alertas, `BaseService` lanza
  `ValidationException` (400 `VAL-000`, ver `controllers.md`). Los validadores de maestro extienden
  `NormalEntityValidator` y reparten el trabajo en `validateRequiredFields` (siempre),
  `validateParentReferences` (solo cuando el DTO viaja solo: anidado, la referencia al padre la pone el
  mapper) y `validateNestedDtos`. Cada hijo se valida por su propio id (sin id es un alta), no por la
  operación del padre. El campo de la alerta es la ruta desde la raíz del cuerpo. Lo fijan los
  `*ValidatorTest`, `ChildIdentityTest`, `BulkValidationTest` y `ValidatorBeanContractTest`
  (validadores sin estado y registrados en Spring).
- Una colección de hijos en un `PUT` es el estado final (README_API.md §4). La reconcilian los
  mappers, no el servicio (ver `mappers.md`). El hijo que no llega se borra en lógico con su rama
  (`CRUDEntity.deleteCascade`), a diferencia del `DELETE` directo, que no toca lo que cuelga.
- Bloqueo optimista: `update` y `bulkUpdate` (y los de `AbstractLovCrudService`) comparan el
  `versionNumber` del DTO con el guardado antes de tocar nada (`BaseEntity.validateVersion`), porque
  el `@Version` de JPA no ve el conflicto cuando la entidad se carga en la misma transacción. Si no
  coincide, `ConcurrencyException` (409 `CON-001`) y no se escribe nada. `null` no comprueba nada: el
  importador del maestro y los trabajos escriben así a propósito. Los hijos los compara el mapper (ver
  `mappers.md`). Lo fijan `BaseServiceTest`, `AbstractLovCrudServiceTest`, `BaseEntityTest` y
  `OptimisticLockingIT`. Para que un cliente pueda devolver la versión, el DTO de catálogo
  (`SLovDTO`) la publica en el JSON; lo fijan `LovControllerContractTest` y `LovCrudIT`.
- La respuesta de una modificación lleva la versión que se acaba de escribir: se hace `flush` antes de
  mapearla (`saveAndFlush`, o `saveAll` y `flush` en un lote). Con un `save` a secas la versión sube al
  confirmar, después de mapear, y el cliente que reenvía la respuesta recibe un 409.
- Borrar un maestro es lógico: `CRUDEntity.delete()` marca `deleted = true` y la fila se queda (ver
  `persistence.md`). Un catálogo no: `AbstractLovCrudService.delete` borra la fila, y si otro
  registro la usa, la clave ajena lo impide al confirmar y sale como 409 `BUS-002`; una entrada en uso
  se retira con `enabled = false`. Lo fijan `LovCrudIT` y `LovControllerContractTest`.
- Lo que no existe se lanza como `NotFoundException` (404), también al modificar y al borrar
  (`BaseService.notFound`). Un `BaseException` con un texto libre sale como 500 (ver
  `controllers.md`).
- Los tests de servicio no levantan Spring: JUnit con Mockito (`@ExtendWith(MockitoExtension.class)`)
  sobre los colaboradores.
