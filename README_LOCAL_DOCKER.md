# Guía detallada para arrancar `mto-configuration` en local y con Docker

Esta guía explica cómo arrancar el proyecto `mto-configuration` en dos modos de trabajo:

```text
Modo local:
  La aplicación Spring Boot se ejecuta desde IntelliJ o Maven.
  La infraestructura la levanta mto-platform.

Modo Docker completo:
  La aplicación Spring Boot también se ejecuta dentro de Docker Compose.
  La infraestructura la levanta mto-platform.
```

> **La infraestructura ya no vive aquí.** PostgreSQL, Redis, RabbitMQ, Keycloak y el colector de
> trazas los levanta [`mto-platform`](https://github.com/alexwarrior1991/mto-platform), que es el
> único entorno local compartido del dominio. Este repositorio solo trae `compose.yaml` con la
> aplicación.
>
> No es una cuestión de orden. Mientras cada repositorio levantaba su propio RabbitMQ, esta
> aplicación publicaba los eventos de datos maestros en un broker y `mto-stock` escuchaba en otro:
> no llegaba nada y no fallaba nada. Con un solo broker no puede pasar.

## 0. Antes de nada: credenciales

Hay **dos** ficheros `.env`, ninguno versionado. El de `mto-platform` define las credenciales de la
infraestructura; el de aquí, con qué credenciales se conecta esta aplicación a ella. Tienen que
coincidir.

```bash
cd ../mto-platform && cp .env.example .env
cd ../mto-configuration && cp .env.example .env
```

Si falta, `docker compose` se niega a arrancar y dice qué variable no encuentra, en lugar de
levantar el stack con una contraseña de ejemplo.

La configuración base (`application.yaml`) tampoco lleva valores por defecto para los secretos:
un entorno desplegado al que le falte una variable no arranca. Es deliberado — es preferible a
que arranque con la credencial de ejemplo y nadie lo note.

## 1. Arquitectura local del proyecto

El stack de `mto-platform/compose.yaml` contiene estos servicios:

```text
postgres  → PostgreSQL 17, con las bases de las dos aplicaciones
redis     → Redis 7.4
rabbitmq  → RabbitMQ 4 con panel de administración
keycloak  → Keycloak 26.1
jaeger    → Jaeger all-in-one, colector de trazas y visor
```

La infraestructura no lleva perfil, así que arranca siempre; cada aplicación lleva el suyo
(`--profile configuration`, `--profile stock`, `--profile gateway`, o `--profile all` para las
tres). El `compose.yaml` de este repositorio contiene un único servicio, `app`, y sirve para probar
una construcción local contra esa infraestructura.

Puertos publicados en tu máquina:

```text
PostgreSQL:           localhost:5432
Redis:                localhost:6379
RabbitMQ AMQP:        localhost:5672
RabbitMQ Management:  http://localhost:15672
Keycloak:             http://auth.mto.local:8082
Jaeger (interfaz):    http://localhost:16686
Jaeger (OTLP HTTP):   localhost:4318
Aplicación:           http://localhost:8081 cuando corre en Docker
```

El colector de trazas lo comparten los tres servicios del dominio, que lo alcanzan por el nombre
`otel.mto.local`, resuelto por el host igual que `auth.mto.local` (ver el apartado 4). Un colector
por stack haría que cada uno viera solo sus propios tramos y una traza que empieza en el gateway y
termina en `mto-stock` no se vería entera en ningún sitio.

Las trazas viven en memoria: se pierden al parar el contenedor. Persistirlas pediría Elasticsearch o
Cassandra detrás, mucho aparato para mirar una traza mientras se depura.

## 2. Requisitos previos

Antes de arrancar el proyecto necesitas:

- Docker Desktop funcionando.
- Java compatible con el proyecto.
- Maven Wrapper disponible mediante `mvnw.cmd`.
- El archivo `hosts` de Windows configurado para Keycloak y el colector de trazas.
- El proyecto abierto desde la raíz `mto-configuration`.

La raíz del proyecto debe contener:

```text
Dockerfile
compose.yaml
mvnw.cmd
pom.xml
src/main/resources/application.yaml
src/main/resources/application-local.yaml
src/main/resources/application-docker.yaml
src/main/resources/application-schema-generation.yaml
```

## 3. Configuración del archivo `hosts`

Para evitar problemas de `issuer` entre navegador, aplicación local y aplicación dockerizada, Keycloak usa un hostname único. El colector de trazas usa otro por el mismo motivo:

```text
auth.mto.local
otel.mto.local
```

En Windows debes editar como administrador este archivo:

```text
C:\Windows\System32\drivers\etc\hosts
```

Añade esta línea:

```text
127.0.0.1 auth.mto.local otel.mto.local
```

Los dos nombres los resuelven por su cuenta los contenedores, con `extra_hosts`. Aquí hacen falta
para lo que corre FUERA de compose: el navegador, un `curl` que pida un token, y sobre todo la
aplicación arrancada desde el IDE, que es el flujo habitual con la infraestructura de
`mto-platform`. Sin `otel.mto.local` el exportador no encuentra el colector y las trazas se pierden
dejando solo un aviso en el log.

No borres las líneas añadidas por Docker Desktop, por ejemplo:

```text
# Added by Docker Desktop
172.x.x.x host.docker.internal
172.x.x.x gateway.docker.internal
# End of section
```

Después de guardarlo, prueba en el navegador:

```text
http://auth.mto.local:8082
```

Si no resuelve, ejecuta:

```powershell
ipconfig /flushdns
```

## 4. Por qué usamos `auth.mto.local`

Keycloak emite tokens JWT con un campo llamado `iss`.

Ese valor debe coincidir exactamente con el `issuer-uri` de Spring Security.

El issuer oficial local del proyecto es:

```text
http://auth.mto.local:8082/realms/mto
```

Debe usarse en:

- `mto-configuration` local.
- `mto-configuration` dockerizado.
- Futuro `mto-gateway`.
- Futuro `mto-production`.
- Frontend Angular si obtiene tokens desde Keycloak.

No conviene mezclar estos valores:

```text
http://localhost:8082/realms/mto
http://keycloak:8080/realms/mto
http://localhost:8080/realms/mto
```

Aunque apunten al mismo Keycloak, para OAuth2/JWT son issuers distintos.

## 5. Perfiles de Spring usados

El proyecto usa estos perfiles:

```text
local
docker
schema-generation
```

### Perfil `local`

Archivo:

```text
src/main/resources/application-local.yaml
```

Se usa cuando la aplicación corre fuera de Docker desde IntelliJ o Maven.

Conecta a:

```text
PostgreSQL: jdbc:postgresql://localhost:5432/mto_configuration_db
Redis: localhost:6379
RabbitMQ: localhost:5672
Keycloak issuer: http://auth.mto.local:8082/realms/mto
```

### Perfil `docker`

Archivo:

```text
src/main/resources/application-docker.yaml
```

Se usa cuando la aplicación corre dentro de Docker Compose.

Conecta a:

```text
PostgreSQL: jdbc:postgresql://postgres:5432/mto_configuration_db
Redis: redis:6379
RabbitMQ: rabbitmq:5672
Keycloak issuer: http://auth.mto.local:8082/realms/mto
```

### Perfil `schema-generation`

Archivo:

```text
src/main/resources/application-schema-generation.yaml
```

Solo se usa para generar scripts SQL iniciales con Hibernate.

No se debe usar para arrancar la aplicación normalmente.

## 6. Diferencia entre local y Docker

La diferencia principal es cómo se llega a los servicios de infraestructura.

Cuando la app corre en local:

```text
La app está en tu Windows.
PostgreSQL está publicado en localhost:5432.
Redis está publicado en localhost:6379.
RabbitMQ está publicado en localhost:5672.
Keycloak está publicado en auth.mto.local:8082.
```

Cuando la app corre en Docker:

```text
La app está dentro de la red de Docker Compose.
PostgreSQL se llama postgres.
Redis se llama redis.
RabbitMQ se llama rabbitmq.
Keycloak mantiene el issuer oficial auth.mto.local:8082.
```

Regla importante:

```text
PostgreSQL, Redis y RabbitMQ cambian entre local y Docker.
Keycloak issuer no cambia.
```

## 7. Modo local: infraestructura en Docker y app en IntelliJ/Maven

Este es el modo recomendado para desarrollar normalmente.

### Paso 1: levantar infraestructura

Desde `mto-platform`:

```powershell
docker compose up -d
./keycloak/apply-partials.sh
```

Sin perfil levanta solo la infraestructura:

```text
postgres
redis
rabbitmq
keycloak
jaeger
```

No levanta ninguna aplicación, porque esta la vas a ejecutar desde IntelliJ o Maven.

`apply-partials.sh` completa el realm: el contenedor de Keycloak solo importa el realm base, y los
clientes y roles de cada servicio los aportan sus importaciones parciales, en orden.

### Paso 2: comprobar servicios

Comprueba estas URLs:

```text
Keycloak: http://auth.mto.local:8082
RabbitMQ Management: http://localhost:15672
Jaeger: http://localhost:16686
```

Credenciales por defecto:

```text
Keycloak admin: admin / admin
RabbitMQ: guest / guest
PostgreSQL: mto_configuration_user / mto_configuration_password
```

El realm lo trae `mto-platform`: el contenedor de Keycloak importa el realm base al arrancar y
`./keycloak/apply-partials.sh` aplica encima lo que aporta cada servicio. Al terminar, el realm
`mto` tiene sus clientes, permisos, perfiles y los cinco usuarios de desarrollo de esta aplicación
(contraseña `local`):

```text
config.lector       mto-viewer
config.editor       mto-editor
config.responsable  mto-admin
config.auditor      mto-auditor
config.ops          mto-ops
```

Para comprobar que quedó importado:

```text
http://auth.mto.local:8082/realms/mto/.well-known/openid-configuration
```

Keycloak no reimporta sobre un realm que ya existe. Si cambias el JSON, hay que partir de cero con
`docker compose down -v`. El detalle del realm está en `keycloak/README.md`.

### Paso 3: arrancar la aplicación desde Maven

Ejecuta:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Spring cargará:

```text
application.yaml
application-local.yaml
```

La aplicación usará:

```text
Datasource: jdbc:postgresql://localhost:5432/mto_configuration_db
Redis: localhost:6379
RabbitMQ: localhost:5672
Keycloak: http://auth.mto.local:8082/realms/mto
```

### Paso 4: arrancar la aplicación desde IntelliJ

En la configuración de ejecución de IntelliJ:

```text
Active profiles: local
```

No pongas `docker` si la app corre fuera de Docker.

No pongas `schema-generation` salvo que quieras generar SQL.

### Paso 5: comprobar que arranca

Al arrancar correctamente debe ocurrir:

```text
1. Spring Boot crea el DataSource.
2. Flyway aplica migraciones pendientes.
3. Hibernate valida entidades contra la base de datos.
4. Redis queda disponible para caché.
5. RabbitMQ queda disponible para mensajería.
6. Spring Security queda preparado para validar tokens de Keycloak.
```

La aplicación local normalmente escuchará en el puerto que tenga configurado Spring Boot. Si no hay un `server.port` específico, por defecto será:

```text
http://localhost:8080
```

## 8. Modo Docker completo: infraestructura y app en Docker

Este modo sirve para probar el proyecto como se ejecutaría de forma más parecida a despliegue.

### Paso 1: levantar todo

Lo habitual es no usar el `compose.yaml` de este repositorio: `mto-platform` levanta la imagen ya
publicada en GHCR.

```powershell
cd ..\mto-platform
docker compose --profile configuration up -d
.\keycloak\apply-partials.sh
```

Para probar una **construcción local** de esta aplicación contra esa misma infraestructura, con el
stack de `mto-platform` ya en marcha:

```powershell
docker compose up -d --build
```

La app queda publicada en:

```text
http://localhost:8081
```

porque en `compose.yaml` está configurado:

```yaml
ports:
  - "${APP_PORT:-8081}:8080"
```

Esto significa:

```text
Puerto 8081 de tu PC → puerto 8080 del contenedor app
```

### Paso 2: perfil usado en Docker

El servicio `app` define:

```yaml
SPRING_PROFILES_ACTIVE: docker
```

Por tanto Spring carga:

```text
application.yaml
application-docker.yaml
```

### Paso 3: conexiones internas en Docker

Dentro de Docker, la app usa:

```text
PostgreSQL: jdbc:postgresql://postgres:5432/mto_configuration_db
Redis: redis:6379
RabbitMQ: rabbitmq:5672
Keycloak issuer: http://auth.mto.local:8082/realms/mto
```

### Paso 4: por qué existe `extra_hosts`

En `compose.yaml`, el servicio `app` tiene:

```yaml
extra_hosts:
  - "auth.mto.local:host-gateway"
```

Esto permite que el contenedor de la aplicación resuelva:

```text
auth.mto.local
```

hacia el host Docker.

Así la app dockerizada puede validar tokens contra el mismo issuer oficial:

```text
http://auth.mto.local:8082/realms/mto
```

Flujo:

```text
app container
  → auth.mto.local:8082
  → host Docker
  → puerto 8082 publicado
  → keycloak container:8080
```

### Paso 5: comprobar servicios dockerizados

URLs útiles:

```text
Aplicación: http://localhost:8081
Keycloak: http://auth.mto.local:8082
RabbitMQ Management: http://localhost:15672
PostgreSQL: localhost:5432
Redis: localhost:6379
```

Aunque PostgreSQL, Redis y RabbitMQ estén en Docker, se publican al host para poder depurar desde herramientas locales.

## 9. Modo generación de schema

Este modo no es para arrancar la aplicación normalmente.

Sirve para generar:

```text
target/generated-schema/db_create.sql
target/generated-schema/db_drop.sql
```

Comando:

```powershell
cd ..\mto-platform; docker compose up -d; cd ..\mto-configuration
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,schema-generation'
```

Después debes revisar:

```text
target/generated-schema/db_create.sql
```

y copiar el SQL bueno a:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

No uses `schema-generation` para probar Flyway, porque ese perfil desactiva Flyway.

## 10. Cuándo usar cada comando

### Desarrollo diario

Usa este modo:

```powershell
cd ..\mto-platform; docker compose up -d; cd ..\mto-configuration
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Ventajas:

- Arranque más rápido de la app.
- Depuración fácil desde IntelliJ.
- Hot reload más sencillo.
- Infraestructura estable en Docker.

### Prueba completa dockerizada

Usa este modo, con la infraestructura de `mto-platform` ya en marcha:

```powershell
docker compose up -d --build
```

Ventajas:

- Pruebas el `Dockerfile`.
- Pruebas variables de entorno de Docker.
- Pruebas la red interna de Docker Compose.
- Simulas mejor un despliegue.

### Reinicio limpio de base de datos en desarrollo

Usa este modo cuando cambies migraciones iniciales o quieras empezar de cero:

```powershell
docker compose down -v
```

Después levanta de nuevo:

```powershell
cd ../mto-platform && docker compose up -d && cd -
```

o:

```powershell
docker compose up -d --build
```

Atención:

```text
docker compose down -v borra los volúmenes locales.
```

Borra datos de:

```text
PostgreSQL
Redis
RabbitMQ
```

No lo uses en producción.

## 11. Cómo saber si todo está bien

### PostgreSQL

Debe estar creado con:

```text
Database: mto_configuration_db
User: mto_configuration_user
Password: mto_configuration_password
Schema: mto_configuration
```

Flyway debe crear o gestionar:

```text
mto_configuration.flyway_schema_history
```

### Redis

La app debe conectar sin errores a:

```text
localhost:6379 en local
redis:6379 en Docker
```

### RabbitMQ

La app debe conectar sin errores a:

```text
localhost:5672 en local
rabbitmq:5672 en Docker
```

Panel de administración:

```text
http://localhost:15672
```

Credenciales:

```text
guest / guest
```

### Keycloak

Debes entrar siempre por:

```text
http://auth.mto.local:8082
```

No uses:

```text
http://localhost:8082
```

El token JWT debe tener:

```text
iss = http://auth.mto.local:8082/realms/mto
```

Y la app debe validar contra:

```text
KEYCLOAK_ISSUER_URI=http://auth.mto.local:8082/realms/mto
```

## 12. Errores habituales

### La app local no conecta a PostgreSQL

Comprueba que arrancaste:

```powershell
cd ../mto-platform && docker compose up -d postgres && cd -
```

Comprueba que el perfil activo es:

```text
local
```

Si usas `docker`, intentará conectar a `postgres:5432`, que solo funciona dentro de Docker.

### La app dockerizada no conecta a PostgreSQL

Comprueba que el perfil activo es:

```text
docker
```

Comprueba que la URL es:

```text
jdbc:postgresql://host.docker.internal:5432/mto_configuration_db
```

No debe ser `localhost` dentro del contenedor: PostgreSQL lo publica `mto-platform` en el host.

### Error de Keycloak issuer

Si ves errores `401 Unauthorized`, `invalid issuer` o problemas de JWT, revisa:

- Que entras a Keycloak por `http://auth.mto.local:8082`.
- Que el token tiene `iss=http://auth.mto.local:8082/realms/mto`.
- Que la app usa `KEYCLOAK_ISSUER_URI=http://auth.mto.local:8082/realms/mto`.
- Que existe `127.0.0.1 auth.mto.local` en el archivo `hosts` de Windows.
- Que `extra_hosts` existe en el servicio `app` de `compose.yaml`.

### El realm `mto` no existe

Si `http://auth.mto.local:8082/realms/mto/.well-known/openid-configuration` da 404, la importación
del realm base no llegó a ejecutarse. Lo habitual es que el volumen venga de un arranque anterior:
Keycloak no importa sobre un realm que ya existe. Se arregla partiendo de cero, desde
`mto-platform`:

```powershell
cd ..\mto-platform
docker compose --profile all down -v
docker compose up -d
.\keycloak\apply-partials.sh
```

Si aun así falla, mira `docker compose logs keycloak`: un JSON mal formado aborta el arranque y lo
dice ahí.

Caso distinto: el realm existe pero falta el cliente `mto-configuration-api` o algún perfil. Eso no
es el realm base, sino que no se ejecutó `apply-partials.sh`. Ejecútalo; es reejecutable.

### Flyway no ejecuta migraciones

Revisa:

- Que no arrancaste con `schema-generation`.
- Que existe `src/main/resources/db/migration/V1__init_schema.sql`.
- Que `spring.flyway.enabled=true`.
- Que la base de datos no tiene ya la migración aplicada.

### RabbitMQ falla al arrancar

Comprueba:

```text
Usuario: guest
Password: guest
Host local: localhost
Host Docker: rabbitmq
Puerto: 5672
```

### Redis falla al arrancar

Comprueba:

```text
Host local: localhost
Host Docker: redis
Puerto: 6379
```

## 13. Comandos útiles

### Levantar solo infraestructura

```powershell
cd ..\mto-platform
docker compose up -d
.\keycloak\apply-partials.sh
```

### Arrancar app local

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

### Arrancar todo dockerizado

```powershell
docker compose up -d --build
```

### Parar la aplicación

```powershell
docker compose down
```

### Parar la infraestructura borrando volúmenes

```powershell
cd ..\mto-platform
docker compose --profile all down -v
```

Las bases y sus usuarios los crea `postgres/init/01-databases.sql`, que PostgreSQL ejecuta **solo**
en la primera inicialización del volumen: cambiar un nombre o una credencial de base exige
precisamente este `down -v`.

### Generar scripts de schema

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,schema-generation'
```

## 14. Flujo recomendado de trabajo

Para el día a día:

```text
1. Levantar la infraestructura de mto-platform.
2. Arrancar mto-configuration en local con perfil local.
3. Desarrollar y depurar desde IntelliJ.
4. Crear migraciones Flyway para cambios de base de datos.
5. Probar el arranque local.
6. Probar ocasionalmente todo dockerizado.
```

Comandos:

```powershell
cd ..\mto-platform; docker compose up -d; cd ..\mto-configuration
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Antes de considerar algo terminado, prueba también la imagen:

```powershell
docker compose up -d --build
```

Así verificas que:

- El `Dockerfile` construye.
- El perfil `docker` funciona.
- Flyway arranca desde cero.
- Hibernate valida contra la base creada por Flyway.
- Keycloak usa el issuer correcto.
- Redis y RabbitMQ responden dentro de Docker.
