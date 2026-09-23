---
paths:
  - "src/main/java/com/alejandro/mtoconfiguration/controller/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/core/exception/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/configuration/security/**/*.java"
  - "src/main/java/com/alejandro/mtoconfiguration/configuration/web/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/controller/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/configuration/**/*.java"
  - "src/test/java/com/alejandro/mtoconfiguration/core/exception/**/*.java"
---
# Controladores, errores y seguridad de la API

## Rutas y clases base

- Toda ruta cuelga de `ConfigurationApiPaths.BASE_PATH` (`/api/v1/configuration`) o de
  `ASYNC_BASE_PATH` (`…/async`), en el `@RequestMapping` de la clase o a través de una constante que
  parte de ella (`ProfileJobResponseMapper.JOBS_PATH`…). Las reglas de `SecurityConfiguration` solo
  cubren ese prefijo: fuera de él, una ruta cae en el `anyRequest().authenticated()` final y le basta
  un token cualquiera.
- Un maestro nuevo extiende `CRUDController<DTO, Entity>`. Un catálogo nuevo extiende
  `AbstractLovController<DTO>` y no redefine sus métodos: hereda rutas, códigos de respuesta y el
  `@PreAuthorize` de `LOV_MANAGE`. Lo fijan `LovControllerContractTest` y
  `LovControllerGuardInheritanceTest` (un `@Override` sin volver a anotar pierde la comprobación).
- `create`, `bulkCreate` y `bulkUpdate` no se declaran en `SaveController` ni en ninguna interfaz
  compartida, solo en el controlador concreto, que es donde llevan `@Valid`. Un método que sobrescribe
  a otro no puede añadir restricciones a sus parámetros (HV000151): no falla al compilar ni al
  arrancar, sino en la petición, y tumba con un 500 todos los endpoints validados de esa clase. Lo fija
  `ControllerConstraintDeclarationTest`.
- Alta y alta en lote responden **201**; borrar, **204** (el `delete` por defecto de
  `DeleteController` responde 200, y cada maestro lo redeclara). Lanzar un trabajo responde **202** con
  `Location`, o **429** con `Retry-After` si no hay hueco. Lo fijan `ProfileControllerTest`,
  `LovControllerContractTest`, `ProfileJobControllerTest` y `LovImportJobControllerTest`.
- Una página sale como `{content, page: {size, number, totalElements, totalPages}}`
  (`spring.data.web.pageable.serialization-mode: via_dto`), la forma que lee el backoffice. Lo fija
  `ProfileControllerTest`.

## Errores

- Toda respuesta de error es un `ProblemDetail` que monta `RestExceptionHandler` con
  `ProblemDetailFactory`. El código sale de `ErrorCodes` y su estado HTTP lo da `ErrorCatalog`, por
  familia (validación 400, negocio 422, no encontrado 404, concurrencia 409, técnico 500) salvo las
  excepciones que lista. Un código nuevo va a `ErrorCodes` y a `StandardErrorCodes`: lo exigen
  `validator/ErrorCatalogTest` (toda constante tiene entrada) y `core/exception/ErrorCatalogTest`
  (toda entrada tiene estado y título).
- Lo que no existe se lanza como `NotFoundException` (o sale de `getReferenceById` como
  `EntityNotFoundException`): 404 `NOT-001`, al leer, al modificar y al borrar. Un `BaseException`
  cuyas alertas no llevan un código del catálogo acaba en 500 `TEC-999`, y un texto libre no es un
  código. Un `versionNumber` desactualizado es `ConcurrencyException`: 409 `CON-001` (ver
  `services.md`). Lo fija `ProfileControllerTest` («Errores»).
- Validación: el servicio lanza `ValidationException` con las alertas del validador y sale 400
  `VAL-000` con `errors[{field, code, message}]`, donde `field` es la ruta desde la raíz del cuerpo
  (`cantilevers[1].cwHeight`; `[0].name` en un lote). Un `IllegalArgumentException` es 400 `VAL-000`,
  y un valor único repetido (`DataIntegrityViolationException`), 409 `BUS-002`. Lo fija
  `RestExceptionHandlerTest`.

## Seguridad

- El permiso lo decide el verbo, en `SecurityConfiguration`: `GET` y `HEAD` piden `CONFIG_READ`;
  `POST …/search`, `…/filter` y `…/jobs/export` también (`QUERY_BY_POST`); `…/bulk`,
  `…/jobs/bulk-create`, `…/jobs/bulk-update`, `…/jobs/import` y `master-data/republish` piden
  `CONFIG_IMPORT` (`BULK`); el resto de `POST`, `PUT` y `PATCH`, `CONFIG_WRITE`; `DELETE`,
  `CONFIG_DELETE`. Escribir o borrar un catálogo pide además `LOV_MANAGE`. Ningún permiso implica
  otro. Lo fija `ApiAuthorizationRulesTest`, con controladores sonda en las rutas reales.
- Una ruta nueva que consulta por `POST`, o que escribe en masa, va a `QUERY_BY_POST` o a `BULK`; si
  no, cae en la regla general de escritura. Lleva su caso en `ApiAuthorizationRulesTest`.
- Los permisos son roles del cliente de la API; los roles de realm solo llegan como `ROLE_REALM_*`, de
  modo que un rol de realm con el nombre de un permiso no lo concede
  (`KeycloakJwtAuthenticationConverterTest`). Un permiso nuevo de `SecurityRoles` tiene que existir
  también en `keycloak/mto-configuration-partial-import.json`, o no lo tendrá nadie.

## Tests de controlador

- `@WebMvcTest(controllers = X.class)` sin la seguridad (las auto-configuraciones de seguridad y
  OAuth2 en `excludeAutoConfiguration`, `KeycloakJwtAuthenticationConverter` en `excludeFilters`,
  `@AutoConfigureMockMvc(addFilters = false)`), con `@Import` de `RestExceptionHandler`,
  `ProblemDetailFactory`, `ErrorCatalog` y `ApiErrorConfiguration`, y los servicios como
  `@MockitoBean`. La plantilla es `ProfileControllerTest`. La seguridad se prueba solo en
  `ApiAuthorizationRulesTest`.
