# Guía detallada para iniciar Flyway en `mto-configuration`

Esta guía explica, paso a paso, cómo dejar funcionando `Flyway` en el servicio `mto-configuration`, cómo generar el primer script con `Hibernate`, cómo convertirlo en la primera migración oficial y cómo trabajar después con futuras versiones de base de datos.

## 1. Objetivo de Flyway en este proyecto

En este proyecto, la responsabilidad debe quedar separada así:

```text
Hibernate:
  - Lee las entidades JPA.
  - Puede generar un SQL inicial como ayuda.
  - En el arranque normal solo valida la base de datos.

Flyway:
  - Crea el schema.
  - Crea tablas, secuencias, índices y constraints.
  - Ejecuta cambios versionados.
  - Guarda el historial de migraciones aplicadas.
```

La regla principal es:

```text
Hibernate no debe modificar la base de datos en el arranque normal.
Flyway debe ser la fuente oficial de la estructura de la base de datos.
```

Por eso, en el arranque normal se usa:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate

  flyway:
    enabled: true
```

Con esta configuración:

1. `Flyway` ejecuta las migraciones pendientes.
2. Después `Hibernate` valida que las entidades coinciden con la base de datos.
3. Si todo coincide, la aplicación arranca.
4. Si algo no coincide, la aplicación falla y hay que corregir la migración o las entidades.

## 2. Nombres usados en este servicio

Para `mto-configuration`, los nombres definidos son:

```text
Servicio: mto-configuration
Base de datos: mto_configuration_db
Schema: mto_configuration
Usuario PostgreSQL: mto_configuration_user
Password local: mto_configuration_password
```

La ruta de migraciones Flyway es:

```text
src/main/resources/db/migration
```

La primera migración debe llamarse:

```text
V1__init_schema.sql
```

## 3. Archivos de configuración implicados

Los archivos importantes son:

```text
src/main/resources/application.yaml
src/main/resources/application-local.yaml
src/main/resources/application-docker.yaml
src/main/resources/application-schema-generation.yaml
docker-compose.yaml
target/generated-schema/db_create.sql
target/generated-schema/db_drop.sql
```

### `application.yaml`

Contiene la configuración común.

Los puntos más importantes para Flyway son:

```yaml
spring:
  flyway:
    enabled: true
    url: ${MTO_CONFIGURATION_DATASOURCE_URL:jdbc:postgresql://localhost:5432/mto_configuration_db}
    user: ${MTO_CONFIGURATION_DATASOURCE_USERNAME:mto_configuration_user}
    password: ${MTO_CONFIGURATION_DATASOURCE_PASSWORD:mto_configuration_password}
    locations: classpath:db/migration
    default-schema: ${MTO_CONFIGURATION_DB_SCHEMA:mto_configuration}
    schemas: ${MTO_CONFIGURATION_DB_SCHEMA:mto_configuration}
    create-schemas: true
    baseline-on-migrate: false
    validate-on-migrate: true
    clean-disabled: true

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: ${MTO_CONFIGURATION_DB_SCHEMA:mto_configuration}
```

Significado:

- `spring.flyway.enabled: true`: activa Flyway en arranque normal.
- `spring.flyway.locations: classpath:db/migration`: indica dónde buscar scripts SQL.
- `spring.flyway.default-schema`: schema donde Flyway trabaja por defecto.
- `spring.flyway.schemas`: schemas gestionados por Flyway.
- `spring.flyway.create-schemas: true`: permite crear el schema si no existe.
- `spring.flyway.validate-on-migrate: true`: valida migraciones antes de aplicar cambios.
- `spring.flyway.clean-disabled: true`: evita borrar toda la base accidentalmente.
- `spring.jpa.hibernate.ddl-auto: validate`: Hibernate solo valida; no crea ni actualiza tablas.

### `application-local.yaml`

Se usa cuando la aplicación corre en local desde IntelliJ o Maven, pero PostgreSQL está en Docker.

Debe apuntar a:

```text
jdbc:postgresql://localhost:5432/mto_configuration_db
```

### `application-docker.yaml`

Se usa cuando la aplicación corre dentro de Docker Compose.

Debe apuntar a:

```text
jdbc:postgresql://postgres:5432/mto_configuration_db
```

### `application-schema-generation.yaml`

Se usa solo para generar scripts SQL con Hibernate.

Puntos importantes:

```yaml
spring:
  flyway:
    enabled: false

  jpa:
    hibernate:
      ddl-auto: none
    properties:
      jakarta:
        persistence:
          schema-generation:
            scripts:
              action: drop-and-create
              create-target: target/generated-schema/db_create.sql
              drop-target: target/generated-schema/db_drop.sql
```

Significado:

- `spring.flyway.enabled: false`: no queremos que Flyway actúe mientras generamos scripts.
- `spring.jpa.hibernate.ddl-auto: none`: Hibernate no crea la base directamente.
- `schema-generation.scripts.action: drop-and-create`: genera script de creación y borrado.
- `create-target`: archivo generado con el SQL de creación.
- `drop-target`: archivo generado con el SQL de borrado.

## 4. Flujo completo para iniciar Flyway desde cero

Este es el flujo correcto cuando empiezas el proyecto o cuando todavía no has consolidado la primera migración.

```text
1. Tener entidades JPA creadas.
2. Levantar infraestructura Docker.
3. Ejecutar Hibernate con el perfil schema-generation.
4. Generar target/generated-schema/db_create.sql.
5. Revisar el SQL generado.
6. Crear src/main/resources/db/migration/V1__init_schema.sql.
7. Copiar dentro el SQL revisado.
8. Arrancar la aplicación normal con Flyway activo.
9. Comprobar que Flyway aplica V1.
10. Comprobar que Hibernate valida correctamente.
```

## 5. Paso 1: levantar infraestructura

Si vas a ejecutar la aplicación desde IntelliJ o Maven, levanta solo infraestructura:

```powershell
docker compose up -d postgres redis rabbitmq keycloak
```

Esto levanta:

```text
PostgreSQL: localhost:5432
Redis: localhost:6379
RabbitMQ: localhost:5672
RabbitMQ Management: http://localhost:15672
Keycloak: http://auth.mto.local:8082
```

Antes de usar Keycloak, recuerda que en Windows debe existir esta línea en el archivo `hosts`:

```text
127.0.0.1 auth.mto.local
```

## 6. Paso 2: generar el SQL inicial con Hibernate

Para generar los scripts iniciales, ejecuta:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,schema-generation'
```

Se usan dos perfiles:

- `local`: para conectar a PostgreSQL, Redis, RabbitMQ y Keycloak por `localhost` o `auth.mto.local`.
- `schema-generation`: para activar la generación de SQL con Hibernate y desactivar Flyway.

Al terminar, Hibernate debe generar:

```text
target/generated-schema/db_create.sql
target/generated-schema/db_drop.sql
```

El archivo importante para Flyway es:

```text
target/generated-schema/db_create.sql
```

El archivo `db_drop.sql` no se mete dentro de Flyway. Solo sirve como referencia si quieres ver cómo Hibernate borraría el esquema.

## 7. Paso 3: revisar `db_create.sql`

Abre:

```text
target/generated-schema/db_create.sql
```

Revisa:

- Que aparecen todas las tablas esperadas.
- Que aparecen las tablas de auditoría de Envers si corresponden.
- Que aparece la columna `deleted` en las entidades que heredan de `CRUDEntity`.
- Que aparecen columnas comunes como `create_date`, `version_date`, `create_user`, `version_user` y `version_number`.
- Que las claves primarias están bien.
- Que las claves foráneas están al final o en orden correcto.
- Que las secuencias se crean antes de ser usadas.
- Que los tipos de PostgreSQL son razonables.
- Que las tablas se crean en el schema `mto_configuration` o que el script funciona con el schema por defecto.

Si ves tablas sin prefijo de schema, por ejemplo:

```sql
create table cantilever (...);
```

puede funcionar porque `Flyway` y `Hibernate` tienen `mto_configuration` como schema por defecto.

Aun así, para una migración inicial clara, es recomendable que el script empiece con:

```sql
CREATE SCHEMA IF NOT EXISTS mto_configuration;
```

## 8. Paso 4: crear la carpeta de migraciones

La carpeta debe ser:

```text
src/main/resources/db/migration
```

Si no existe, créala.

La estructura debe quedar así:

```text
src/main/resources
└── db
    └── migration
        └── V1__init_schema.sql
```

## 9. Paso 5: crear `V1__init_schema.sql`

Crea este archivo:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

El nombre es obligatorio respetarlo.

Correcto:

```text
V1__init_schema.sql
```

Incorrecto:

```text
V1_init_schema.sql
v1__init_schema.sql
V1-init-schema.sql
init_schema.sql
```

Reglas del nombre:

- Empieza por `V` mayúscula.
- Después va el número de versión.
- Después van dos guiones bajos `__`.
- Después va una descripción.
- Termina en `.sql`.

## 10. Paso 6: contenido recomendado de `V1__init_schema.sql`

El archivo debe empezar así:

```sql
CREATE SCHEMA IF NOT EXISTS mto_configuration;
```

Después copia el contenido revisado de:

```text
target/generated-schema/db_create.sql
```

El resultado conceptual será:

```sql
CREATE SCHEMA IF NOT EXISTS mto_configuration;

create table ...;
create table ...;
alter table ... add constraint ...;
```

No copies el contenido de:

```text
target/generated-schema/db_drop.sql
```

## 11. Paso 7: arrancar Flyway en local

Para probar Flyway desde cero en local, lo más limpio en esta fase es borrar volúmenes y empezar con PostgreSQL limpio.

> Atención: este comando borra datos locales de los contenedores. Es correcto en desarrollo inicial, pero no debe usarse en producción.

```powershell
docker compose down -v
```

Después levanta infraestructura:

```powershell
docker compose up -d postgres redis rabbitmq keycloak
```

Arranca la aplicación con perfil `local`:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

En este arranque no debes usar `schema-generation`.

Correcto:

```text
local
```

Incorrecto para probar Flyway:

```text
local,schema-generation
```

porque `schema-generation` tiene `spring.flyway.enabled=false`.

## 12. Paso 8: arrancar Flyway con todo Docker

Si quieres probar todo dockerizado, ejecuta:

```powershell
docker compose down -v
```

Después:

```powershell
docker compose up -d --build
```

El servicio `app` usa:

```yaml
SPRING_PROFILES_ACTIVE: docker
```

Por tanto cargará:

```text
application.yaml
application-docker.yaml
```

Y conectará a PostgreSQL por:

```text
jdbc:postgresql://postgres:5432/mto_configuration_db
```

## 13. Qué debe aparecer en los logs

Cuando Flyway funciona bien, deberías ver mensajes similares a:

```text
Flyway Community Edition ...
Database: jdbc:postgresql://...
Successfully validated 1 migration
Creating Schema History table "mto_configuration"."flyway_schema_history"
Migrating schema "mto_configuration" to version "1 - init schema"
Successfully applied 1 migration
```

Lo más importante es ver:

```text
Migrating schema "mto_configuration" to version "1 - init schema"
```

Después debe arrancar Hibernate y validar sin errores.

## 14. Qué crea Flyway en PostgreSQL

Flyway crea una tabla de control llamada:

```text
mto_configuration.flyway_schema_history
```

Esa tabla contiene qué migraciones se han aplicado.

Para la primera migración deberías ver algo equivalente a:

```text
version: 1
description: init schema
script: V1__init_schema.sql
success: true
```

## 15. Cómo trabajar con futuras migraciones

Una vez que `V1__init_schema.sql` se ha aplicado correctamente, la regla es:

```text
No modificar migraciones ya aplicadas.
```

Si mañana añades una columna a una entidad, no debes editar `V1`.

Debes crear una nueva migración:

```text
V2__add_xxx_column.sql
```

Ejemplo:

```sql
ALTER TABLE mto_configuration.example_table
ADD COLUMN external_code VARCHAR(100);
```

Si añades una tabla nueva:

```text
V3__create_new_table.sql
```

Si insertas datos maestros iniciales:

```text
V4__insert_initial_master_data.sql
```

## 16. Regla sobre checksums

Flyway calcula un checksum de cada migración aplicada.

Si modificas un archivo ya ejecutado, Flyway detectará que el checksum cambió y fallará.

Esto es bueno porque protege la consistencia de la base de datos.

En desarrollo muy inicial puedes borrar la base con:

```powershell
docker compose down -v
```

y volver a aplicar todo desde cero.

Pero en cuanto haya datos compartidos, más entornos o más personas trabajando, no debes modificar migraciones aplicadas.

## 17. Errores habituales y solución

### Flyway no ejecuta nada

Revisa:

- Que no estás arrancando con el perfil `schema-generation`.
- Que existe `src/main/resources/db/migration/V1__init_schema.sql`.
- Que el nombre del archivo tiene doble guion bajo `__`.
- Que `spring.flyway.enabled` está en `true`.
- Que `spring.flyway.locations` apunta a `classpath:db/migration`.

### Error `Schema-validation: missing table`

Significa que Hibernate esperaba una tabla que Flyway no ha creado.

Causas posibles:

- `V1__init_schema.sql` no contiene esa tabla.
- La tabla se creó en otro schema.
- El schema de Hibernate y el de Flyway no coinciden.
- Estás usando una base antigua con restos.
- No has borrado el volumen local después de cambiar scripts.

Solución en desarrollo inicial:

```powershell
docker compose down -v
docker compose up -d postgres redis rabbitmq keycloak
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

### Error de checksum

Significa que modificaste una migración ya aplicada.

En local inicial puedes limpiar el volumen:

```powershell
docker compose down -v
```

En entornos compartidos, no modifiques la migración aplicada. Crea una nueva `V2`, `V3`, etc.

### Error `database does not exist`

Revisa que PostgreSQL se creó con:

```text
POSTGRES_DB=mto_configuration_db
```

Si el volumen ya existía con una base antigua, PostgreSQL no recrea automáticamente la base.

Solución en local:

```powershell
docker compose down -v
docker compose up -d postgres
```

### Error `role does not exist`

Revisa que PostgreSQL se creó con:

```text
POSTGRES_USER=mto_configuration_user
POSTGRES_PASSWORD=mto_configuration_password
```

Si antes usabas otro usuario y el volumen ya existía, limpia el volumen en local.

### Error de conexión en Docker

Si la app corre dentro de Docker, la URL debe ser:

```text
jdbc:postgresql://postgres:5432/mto_configuration_db
```

No debe ser:

```text
jdbc:postgresql://localhost:5432/mto_configuration_db
```

Dentro de un contenedor, `localhost` es el propio contenedor, no PostgreSQL.

## 18. Comandos resumen

### Generar SQL inicial con Hibernate

```powershell
docker compose up -d postgres redis rabbitmq keycloak
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,schema-generation'
```

### Probar Flyway en local

```powershell
docker compose down -v
docker compose up -d postgres redis rabbitmq keycloak
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

### Probar Flyway con todo Docker

```powershell
docker compose down -v
docker compose up -d --build
```

## 19. Flujo recomendado final

El flujo final de trabajo debe ser:

```text
1. Modificas entidades JPA.
2. Si es la primera vez, usas Hibernate para generar db_create.sql.
3. Creas V1__init_schema.sql con el SQL revisado.
4. Arrancas normal.
5. Flyway aplica V1.
6. Hibernate valida.
7. Para futuros cambios, creas V2, V3, V4...
8. No modificas migraciones ya aplicadas.
```
