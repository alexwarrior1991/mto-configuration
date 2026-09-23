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
  clase no se aplica al cargar una colección. Cada `@OneToMany`, y el lado inverso de cada
  `@ManyToMany`, repite `@SQLRestriction("deleted = false")`; si no, lo borrado sigue apareciendo
  dentro de su padre. Lo fija `SoftDeleteIT`.
- Las claves naturales de infraestructura son índices únicos parciales `ux_…` con
  `WHERE deleted = false` (y `upper(...)` en los nombres), para que lo borrado no impida dar de alta
  otra vez el mismo nombre. El `code` de los catálogos es único sin más (`ux_<tabla>_code`). Lo fija
  `FlywayMigrationIT`.
- Los perfiles de una vía van en orden físico: `@OrderBy("orderInTrack ASC, kp ASC, id ASC")` en
  `Track.profiles`. `/profiles/track/{id}/keyset` y `/range` paginan por `kp`, y en una vía de dos
  tramos ese orden no es el físico (README_API.md §4).
- El repositorio de una entidad que publica eventos tiene `findByIdForMessaging`, cuyo `@EntityGraph`
  carga todo lo que lee el mapper del payload (ver `messaging.md`).
- Tests de integración (`*IT`, en `./mvnw verify`):
  - los que necesitan el esquema migrado usan `support/PostgresTestDatabase`: un contenedor
    `postgres:17-alpine` para toda la JVM, o una base desechable indicada con
    `-Dmto.test.postgres.url` (más `.username` y `.password`), sin Docker. Sin ninguna de las dos
    cosas fallan, no se saltan;
  - los de búsqueda por criterios heredan `AbstractCriteriaSearchIT`, que arranca su propio
    contenedor con `ddl-auto: create-drop` y necesita Docker siempre.
