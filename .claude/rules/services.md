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
  abierta al mapear: lo fijan `GetByIdOutsideTransactionIT`, `FilterOutsideTransactionIT` y
  `ProfileWindowsIT` (las ventanas `/profiles/track/{id}/keyset` y `/range`, que respondían 500 con
  cualquier vía con perfiles), que llaman al servicio sin ella. Una consulta nueva de un servicio que
  devuelve DTO lleva su `@Transactional(readOnly = true)` y su caso allí.
- El brazo de una ménsula (`Cantilever.steadyArm`) es el lado inverso de un `@OneToOne`: Hibernate no
  puede dejarlo perezoso, y declarado `LAZY` lo cargaba aparte, con un `select` de `steady_arm` por
  ménsula. Es `EAGER`: una ménsula cargada por id o por la colección de su perfil (una página de
  `/profiles/filter`, el detalle de un perfil o de una vía, su modificación) lo trae unido en la
  misma sentencia. Una **consulta** que devuelve ménsulas (JPQL, QueryDSL, derivada) lo pide ella:
  `left join fetch c.steadyArm` o `@EntityGraph(attributePaths = "steadyArm")`, como las de
  `CantileverRepository` y las ventanas de `ProfileRepository`. `/cantilevers/search` monta su consulta
  con Criteria y lo une en `CantileverCriteriaSearchRepository.fetchForPage`, el gancho de
  `CriteriaSearchRepository` para lo que la página tiene que traer unido (solo relaciones a uno).
  Lo fijan `SteadyArmFetchIT`, que cuenta esa sentencia, y `ProfileWindowsIT`.
- Una lista sin transacción alrededor también cuesta una consulta por fila aunque no falle: el
  mapper resuelve cada referencia a catálogo por su cuenta (el tipo de una ménsula, el de un brazo)
  y, sin sesión, cada una es una consulta suelta, cien en una página de cincuenta ménsulas. Dentro
  de la transacción de la lectura van por lote. `getCantilevers` y `getSteadyArms`
  (`/cantilevers/filter`, `/steady-arms/filter`) no la llevaban. Lo fija `SteadyArmFetchIT`.
- El recuento de `/search` (`CriteriaSearchRepository.criteriaSearchWithChildren`) solo lleva
  `DISTINCT` cuando el filtro une la raíz con una colección (las estaciones de una vía, los
  seccionamientos de un perfil), que es lo único que repite filas; si no, es un `count` a secas,
  que PostgreSQL resuelve recorriendo la tabla en paralelo en vez de ordenar los ids (con un millón
  de perfiles, 50 ms en lugar de más de 200). Los de `/paged` y `/filter` ya eran un `count` sobre
  la tabla con los `join` del filtro y nada más. Lo fija `SteadyArmFetchIT`.
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
  trabajos escriben a través de los servicios. Y por eso les valen las mismas reglas, también la
  del borrado (abajo): el importador del maestro solo da de alta y modifica, y de las colecciones
  de hijos manda las ménsulas de un perfil y las agujas de un aislador, que se van con su padre; las
  demás van a `null`. Una carga que mandase una colección de hijos que impiden el borrado (los
  perfiles de una vía, las vías de un paquete) podría recibir el 409. Lo fija
  `ProfileMasterImportIT.reimportarConCambiosYConFilasDeMenos`.
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
  mappers, no el servicio (ver `mappers.md`). El hijo que no llega se borra en lógico **con la regla de su
  `DELETE`** (abajo): se va con lo que no existe sin él, y si le queda algo vivo colgando el `PUT`
  entero es un 409 `BUS-002` y no escribe nada. Lo comprueba el mismo `DeletionGuard`, como
  `WrittenEntitiesCheck`, **después del flush** y sobre lo que el mapper apuntó como quitado
  (`EntityChangeTracker.removals()`): cuenta el estado final, así que lo que el mismo cuerpo borra
  en otra parte no cuenta. El error lleva en `errors[]` la colección de la que se quitó (`tracks`).
  Hoy lo pueden dar una vía o una estación quitadas del `PUT` de su paquete y un perfil quitado del
  de su vía. Lo fijan `NestedChildEventsIT`, `DeleteBranchTest` y `EntityChangeTrackerTest`.
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
- Borrar un maestro es lógico: la fila se queda con `deleted = true` (ver `persistence.md`). El
  `DELETE` directo (`CRUDService.softDelete`) hace tres cosas más:
  - **si cuelga algo vivo, 409 `BUS-002` y no escribe nada** (`EntityInUseException`, con qué cuelga
    y cuánto). Lo decide `DeletionGuard`, un `BeforeDeleteCheck` que `CRUDService` llama tras el
    validador y antes de tocar la entidad, con recuentos nativos, no cargando entidades: de un
    paquete, sus vías y estaciones; de una estación, sus vías enlazadas, seccionadores, aisladores y
    puntos de acceso; de una vía, sus perfiles, lo que está en ella o la nombra y sus puntos de
    acceso; de un perfil, los seccionadores de su poste. Una relación nueva hacia un maestro con
    borrado lógico añade allí su recuento;
  - **se lleva los hijos propios** (`deleteWithOwnChildren()`, que no baja a los hijos que impiden
    el borrado; la rama entera, `deleteCascade()`, es solo de la vía borrada entera: las ménsulas de un perfil y sus brazos, el
    brazo de una ménsula, las agujas de un aislador), dentro de `trackingNestedChanges`, así que cada
    uno publica su `DELETED` detrás del de la raíz;
  - **bloquea la fila antes de contar** con un `select … for update` nativo, el único que choca con
    el `for key share` de la clave ajena de quien da de alta o reapunta un hijo a la vez (el
    `PESSIMISTIC_WRITE` de Hibernate es `for no key update` en PostgreSQL y no choca). Si el alta va
    delante, el recuento la espera y es un 409; si va detrás, espera ella y la rechaza
    `LiveParentGuard`, un `WrittenEntitiesCheck` que tras el flush vuelve a mirar, con una consulta
    nueva, que el padre de cada fila creada o reapuntada sigue vivo (400 `BUS-001` en su campo). Una
    referencia nueva a un maestro va también a `LiveParentGuard.referencesOf`.

  Solo la vía se borra entera: `TrackService.deleteWithEverything` (`DELETE /tracks/{id}?cascade=true`)
  borra la vía y lo suyo (perfiles, ménsulas, brazos, los seccionadores que están en ella, sus
  aisladores con sus agujas, sus puntos de acceso) en una transacción, bloqueando antes la vía y su
  rama; lo de otra vía que la nombra no se toca: lo impide, con el mismo 409. Carga la rama por lotes
  con el grafo de su evento, borra por capas con un flush en cada una (para que Hibernate agrupe los
  `UPDATE` en lotes JDBC, que solo activa en esa sesión) y al final comprueba que no queda nada vivo
  apuntando a ella. Lo fijan `DeleteWithDependentsIT` (los 409, la cascada con sus eventos, la
  carrera con dos transacciones y que las lecturas no crecen con los perfiles), `CRUDServiceTest`
  y `LiveParentGuardTest`. Un catálogo no: `AbstractLovCrudService.delete` borra la fila, y si otro
  registro la usa, la clave ajena lo impide al confirmar y sale como 409 `BUS-002`; una entrada en uso
  se retira con `enabled = false`. Lo fijan `LovCrudIT` y `LovControllerContractTest`.
- Lo que no existe se lanza como `NotFoundException` (404), también al modificar y al borrar
  (`BaseService.notFound`). Un `BaseException` con un texto libre sale como 500 (ver
  `controllers.md`).
- Los tests de servicio no levantan Spring: JUnit con Mockito (`@ExtendWith(MockitoExtension.class)`)
  sobre los colaboradores.
