---
paths:
  - "src/main/java/com/alejandro/mtoconfiguration/entity/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/repository/**/*.java"
  - "src/main/resources/db/migration/**"
  - "src/test/java/com/alejandro/mtoconfiguration/entity/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/repository/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/migration/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/support/**/*.java"
---
# Persistencia: entidades, repositorios y Flyway

- El esquema solo cambia con una migración nueva en `src/main/resources/db/migration`
  (`V<n>__<descripcion>.sql`, con el siguiente número). Hibernate arranca con `ddl-auto: validate`: una
  entidad que no case con el esquema no arranca. Una migración ya aplicada no se edita nunca, porque
  Flyway compara su checksum y falla (README_FLYWAY.md §16).
- Todas las entidades salvo `AsyncJob` están auditadas con Envers (`@Audited`) y tienen su gemela
  `<tabla>_aud`, también las tablas de unión. Una migración que añade o cambia una
  columna auditada toca la gemela en la misma migración. Lo fijan `ddl-auto: validate` y
  `FlywayMigrationIT`.
- Borrado lógico: `CRUDEntity` lleva `@SQLRestriction("deleted = false")`, pero la restricción de
  clase no se aplica al cargar una colección. Cada `@OneToMany` (también `Profile.disconnectors`), y
  el lado inverso de cada `@ManyToMany` y de cada `@OneToOne`, repite
  `@SQLRestriction("deleted = false")`; si no, lo borrado sigue apareciendo dentro de su padre. En el
  perfil importa doble: un seccionador borrado conserva su perfil, y la restricción es lo que deja
  solo los vivos. Lo fijan `SoftDeleteIT` y `DisconnectorLinkIT`.
- El lado **dueño** de un `@ManyToMany` también la repite (`Track.stations`): sin ella, una vía seguía
  listando la estación borrada y devolverla tal como se leyó era un 404. Y una relación **a uno**
  hacia un maestro borrado no se puede recorrer: hasta que borrar un maestro con algo colgando fue
  un 409 (ver `services.md`), ni su `DELETE` ni quitarlo del `PUT` de su padre tocaban lo que
  colgaba de él; en esas filas la clave ajena se queda y el proxy no se inicializa
  (`EntityNotFoundException`).
  Quien lee a través de ella pregunta a `LiveReferences.orNull` (los mappers de payload, y
  `DisconnectorMapper` para el código y el KP del poste): borrada es `null`. Quien escribe la
  rechaza con `BaseMapper.requireNotGone` (ver `mappers.md`). Unir una relación a uno borrada en un
  `@EntityGraph` con su proxy ya en la sesión es un `NullPointerException` de Hibernate, que rellena
  el proxy con una fila de nulos: por eso no se llega a escribir. Lo fija `DeletedReferencesIT`.
- Las claves naturales de infraestructura son índices únicos parciales `ux_…` con
  `WHERE deleted = false` (y `upper(...)` en los nombres), para que lo borrado no impida dar de alta
  otra vez el mismo nombre: el de un paquete, el de una estación y el de una vía en su paquete, el
  perfil en su vía (V12, V18), el punto de acceso en su vía (V28) y el aislador en su estación
  (`ux_section_insulator_station_name`, V31). Un repetido sale como 409 `BUS-002`. Una migración que
  crea uno sobre una tabla que la API ya escribía comprueba antes que no haya repetidos y, si los
  hay, falla diciendo cuáles (V31); nunca con el mensaje del motor. El seccionador es la excepción:
  su clave es el nombre en su **paquete**, que no es una columna suya (sale de su estación, de la vía
  de su poste o de su vía propia), así que no tiene índice y la comprueba
  `DisconnectorNaturalKeyGuard` después del flush (ver `services.md`); `NaturalKeyIT`. El perfil de
  un seccionador no es una clave: un poste lleva varios
  (`V29` quitó el `ux_disconnector_profile_id` de `V24`, y queda `idx_disconnector_profile_id` sobre
  los vivos). El brazo de una ménsula sí es 1:1, pero entre los vivos: `V30` cambió el `unique` de
  `steady_arm.cantilever_id` de `V1` por `ux_steady_arm_cantilever_id`, parcial, para que un brazo
  borrado no deje su ménsula ocupada (`SteadyArmLinkIT`). El `code` de los catálogos es único sin más (`ux_<tabla>_code`). Lo fija
  `FlywayMigrationIT`.
- PostgreSQL no indexa una clave ajena por su cuenta. La de un hijo que se lee por su padre lleva su
  índice, parcial sobre los vivos porque toda lectura lleva el `(deleted = false)` literal de
  `@SQLRestriction`: `idx_cantilever_profile_id` sobre `cantilever (profile_id, id)` (`V32`; el `id`
  detrás, por el `@OrderBy("id ASC")`), `idx_disconnector_profile_id` (`V29`) y
  `ux_steady_arm_cantilever_id` (`V30`). Sin él, cada «las ménsulas de este perfil» (el importador por
  fila, el evento de cada perfil, cada página de `/profiles/filter`) recorría la tabla entera. Una
  tabla hija nueva nace con el suyo. Un índice se crea dentro de la transacción de Flyway; sobre una
  tabla que ya sea grande, antes a mano con `CREATE INDEX CONCURRENTLY` y la migración, con su
  `IF NOT EXISTS`, no hace nada. Lo fija `FlywayMigrationIT`.
- El historial (`GET /{recurso}/{id}/revisions`) lee las gemelas `_aud`, cuya clave primaria es
  `(rev, id)`: no sirve para buscar por `id` ni por la columna del padre. Las que crecen sin límite
  llevan sus índices (`V33`): `(id, rev)` en `profile_aud`, `cantilever_aud` y `steady_arm_aud` (la
  página del historial y el «a fecha de» de cada hijo), la columna del padre en
  `cantilever_aud (profile_id)` y `steady_arm_aud (cantilever_id)`, y `(profile_id, <otro>_id, rev)` en
  las tres gemelas de unión del perfil. Envers reconstruye la colección de cada revisión desde la
  gemela del hijo, **aunque la relación diga `NOT_AUDITED`**: en una colección ese modo no cambia de
  dónde lee (solo lo respeta en una relación a uno), así que una colección auditada nueva en una
  entidad que crece necesita el índice de la columna del padre en la gemela del hijo. Las gemelas
  pequeñas (vías, estaciones, seccionadores, catálogos) no lo llevan. Lo fija `FlywayMigrationIT`.
- Lo que Envers trae en una colección de una revisión no pasa por ningún `@SQLRestriction`: el hijo
  borrado en lógico (para Envers, una modificación) seguía saliendo en todas las revisiones
  posteriores. `BaseService.findRevisions` llama a `CRUDEntity.forgetDeletedChildren()` sobre la
  entidad de la revisión, y la sobrescribe la entidad cuya fila en las listas lleva hijos: el perfil
  (ménsulas y seccionadores), la ménsula (su brazo) y el aislador (sus agujas). Una entidad nueva
  cuya fila lleve una colección de hijos la sobrescribe también; nunca se llama sobre una entidad
  viva. Y como `NOT_AUDITED` no vale en una colección, un conjunto de catálogo (`anchorages`,
  `sectionings`, `sectioningFeedings`) solo enseña en el historial las entradas con alguna revisión:
  una migración que siembre uno de esos tres catálogos inserta también su fila `_aud`
  (`FlywayMigrationIT`). No se añade un `@OneToMany`/`@ManyToMany` con `NOT_AUDITED` esperando que
  se lea «como está hoy». Lo fija `EntityRevisionsIT`.
- Un `@EntityGraph` que trae una lista (`Profile.cantilevers`, `Track.profiles`) junto con un conjunto
  repite cada elemento de la lista por cada fila del producto: Hibernate no quita los repetidos de
  una lista sin índice. El mapper que la recorre la pasa por `distinct()` (los de payload del perfil
  y de la vía). Lo fija `MasterDataPayloadContractIT`, con dos seccionadores en el poste y la vía en
  dos estaciones.
- Los perfiles de una vía van en orden físico: `@OrderBy("orderInTrack ASC, kp ASC, id ASC")` en
  `Track.profiles`. `/profiles/track/{id}/keyset` y `/range` paginan por `kp`, y en una vía de dos
  tramos ese orden no es el físico (README_API.md §4).
- Lo perezoso de una página se carga por lotes de 100 (`hibernate.default_batch_fetch_size`, como en
  `mto-stock` y `mto-maintenance`): una sentencia por cada colección y cada catálogo de la página
  entera. Con lotes de 20, una página de 50 perfiles eran tres por cada uno (32 sentencias; hoy 13).
  Lo fija `SteadyArmFetchIT` (las ménsulas de 40 perfiles, en una sentencia).
- El SQL nativo (`createNativeQuery`, `@Query(nativeQuery = true)`) lleva `{h-schema}` delante de
  **cada** tabla que nombra, también en un `join` o un `exists`: `select … from {h-schema}track`.
  Hibernate antepone el esquema (`hibernate.default_schema`) al SQL que genera él, no al escrito a
  mano, y con el perfil `docker` las tablas viven en `mto_configuration`, que no está en el
  `search_path`. Todos los IT corren en `public`, donde una tabla sin esquema funciona: por eso los
  recuentos y bloqueos de `DeletionGuard` y `LiveParentGuard` pasaron la suite y en la plataforma
  cada alta de estación o de vía y cada borrado eran un 500 (`relation "…" does not exist`). Lo
  llevan `OutboxMessageRepository`, `DeletionGuard`, `LiveParentGuard` y
  `DisconnectorRepository.findLiveIdsOnTrack`. Lo fija `DeleteWithDependentsInOwnSchemaIT`, que
  corre los casos de `DeleteWithDependentsIT` con las tablas en otro esquema; un SQL nativo nuevo
  que no pase por un alta, un borrado o la cascada de una vía añade allí su caso.
- El repositorio de una entidad que publica eventos tiene `findByIdForMessaging`, cuyo `@EntityGraph`
  carga todo lo que lee el mapper del payload (ver `messaging.md`).
- Tests de integración (`*IT`, en `./mvnw verify`):
  - los que necesitan el esquema migrado usan `support/PostgresTestDatabase`: un contenedor
    `postgres:17-alpine` para toda la JVM, o una base desechable indicada con
    `-Dmto.test.postgres.url` (más `.username` y `.password`), sin Docker. Sin ninguna de las dos
    cosas fallan, no se saltan. Cada clase es un contexto de Spring propio (su
    `@DynamicPropertySource`), y todos quedan en caché hasta el final contra la misma base, con sus
    100 conexiones. Por eso `registerProperties` deja el pool con `minimum-idle` 1: con el de
    Hikari por defecto, once contextos agotaban la base;
  - los de búsqueda por criterios heredan `AbstractCriteriaSearchIT`, que arranca su propio
    contenedor con `ddl-auto: create-drop` y necesita Docker siempre.
