---
paths:
  - "src/test/**/*.java"
---
# Tests

- `*Test` corre en `./mvnw test` (surefire) sin Docker; `*IT` corre en `./mvnw verify` (failsafe)
  contra PostgreSQL, Redis o Keycloak de verdad (Testcontainers). La CI ejecuta
  `./mvnw -B verify`, y antes los tests de los generadores en Python de `data/tools/tests`.
- Aquí hay una clase de test por clase de producción (`ProfileMapperTest`, `TrackControllerTest`,
  `TrackSchematicServiceTest`…) más los tests guardianes; no hay clases por capa como en `mto-stock`
  o `mto-maintenance`. Lo habitual es un `@Nested` por aspecto y un `@DisplayName` con la frase en
  castellano.
- Se afirma con AssertJ (`assertThat`). Fuera de Spring, Mockito con
  `@ExtendWith(MockitoExtension.class)`; dentro de un slice, `@MockitoBean`.
- Los tests guardianes recorren el código y fallan cuando un cambio rompe una regla que el compilador
  no ve: `CacheableServicesTest`, `CacheEvictionListenerTest`, `LovControllerGuardInheritanceTest`,
  `ControllerConstraintDeclarationTest`, `MasterDataPayloadMapperCoverageTest`,
  `ValidatorBeanContractTest` y los dos `ErrorCatalogTest`. Cada regla de `.claude/rules/` dice cuál
  la vigila.
- Para ejecutar un solo IT contra un PostgreSQL existente, sin Docker (solo los que usan
  `PostgresTestDatabase`, ver `persistence.md`):
  `./mvnw verify -Dit.test=XxxIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
  -Dmto.test.postgres.url=jdbc:postgresql://localhost:5432/<base-desechable>`.
