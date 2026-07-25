# Guía detallada para arrancar `mto-configuration` en local y con Docker

Esta guía explica cómo arrancar el proyecto `mto-configuration` en dos modos de trabajo:

```text
Modo local:
  La aplicación Spring Boot se ejecuta desde IntelliJ o Maven.
  PostgreSQL, Redis, RabbitMQ y Keycloak se ejecutan en Docker.

Modo Docker completo:
  La aplicación Spring Boot también se ejecuta dentro de Docker Compose.
  PostgreSQL, Redis, RabbitMQ y Keycloak se ejecutan en Docker.
```

## 1. Arquitectura local del proyecto

El stack definido en `docker-compose.yaml` contiene estos servicios:

```text
postgres  → PostgreSQL 17
redis     → Redis 7.4
rabbitmq  → RabbitMQ 4 con panel de administración
keycloak  → Keycloak 26.1
app       → mto-configuration-api
```

Puertos publicados en tu máquina:

```text
PostgreSQL:           localhost:5432
Redis:                localhost:6379
RabbitMQ AMQP:        localhost:5672
RabbitMQ Management:  http://localhost:15672
Keycloak:             http://auth.mto.local:8082
Aplicación:           http://localhost:8081 cuando corre en Docker
```

## 2. Requisitos previos

Antes de arrancar el proyecto necesitas:

- Docker Desktop funcionando.
- Java compatible con el proyecto.
- Maven Wrapper disponible mediante `mvnw.cmd`.
- El archivo `hosts` de Windows configurado para Keycloak.
- El proyecto abierto desde la raíz `mto-configuration`.

La raíz del proyecto debe contener:

```text
Dockerfile
docker-compose.yaml
mvnw.cmd
pom.xml
src/main/resources/application.yaml
src/main/resources/application-local.yaml
src/main/resources/application-docker.yaml
src/main/resources/application-schema-generation.yaml
```

## 3. Configuración del archivo `hosts` para Keycloak

Para evitar problemas de `issuer` entre navegador, aplicación local y aplicación dockerizada, Keycloak usa un hostname único:

```text
auth.mto.local
```

En Windows debes editar como administrador este archivo:

```text
C:\Windows\System32\drivers\etc\hosts
```

Añade esta línea:

```text
127.0.0.1 auth.mto.local
```

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

Desde la raíz del proyecto ejecuta:

```powershell
docker compose up -d postgres redis rabbitmq keycloak
```

Esto levanta solo:

```text
postgres
redis
rabbitmq
keycloak
```

No levanta `app`, porque la aplicación la vas a ejecutar desde IntelliJ o Maven.

### Paso 2: comprobar servicios

Comprueba estas URLs:

```text
Keycloak: http://auth.mto.local:8082
RabbitMQ Management: http://localhost:15672
```

Credenciales por defecto:

```text
Keycloak admin: admin / admin
RabbitMQ: guest / guest
PostgreSQL: mto_configuration_user / mto_configuration_password
```

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

Desde la raíz del proyecto ejecuta:

```powershell
docker compose up -d --build
```

Esto levanta:

```text
postgres
redis
rabbitmq
keycloak
app
```

La app queda publicada en:

```text
http://localhost:8081
```

porque en `docker-compose.yaml` está configurado:

```yaml
ports:
  - "8081:8080"
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

En `docker-compose.yaml`, el servicio `app` tiene:

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
docker compose up -d postgres redis rabbitmq keycloak
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
docker compose up -d postgres redis rabbitmq keycloak
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Ventajas:

- Arranque más rápido de la app.
- Depuración fácil desde IntelliJ.
- Hot reload más sencillo.
- Infraestructura estable en Docker.

### Prueba completa dockerizada

Usa este modo:

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
docker compose up -d postgres redis rabbitmq keycloak
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
docker compose up -d postgres
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
jdbc:postgresql://postgres:5432/mto_configuration_db
```

No debe ser `localhost` dentro del contenedor.

### Error de Keycloak issuer

Si ves errores `401 Unauthorized`, `invalid issuer` o problemas de JWT, revisa:

- Que entras a Keycloak por `http://auth.mto.local:8082`.
- Que el token tiene `iss=http://auth.mto.local:8082/realms/mto`.
- Que la app usa `KEYCLOAK_ISSUER_URI=http://auth.mto.local:8082/realms/mto`.
- Que existe `127.0.0.1 auth.mto.local` en el archivo `hosts` de Windows.
- Que `extra_hosts` existe en el servicio `app` de Docker Compose.

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
docker compose up -d postgres redis rabbitmq keycloak
```

### Arrancar app local

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

### Arrancar todo dockerizado

```powershell
docker compose up -d --build
```

### Parar contenedores sin borrar datos

```powershell
docker compose down
```

### Parar contenedores borrando volúmenes

```powershell
docker compose down -v
```

### Generar scripts de schema

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local,schema-generation'
```

## 14. Flujo recomendado de trabajo

Para el día a día:

```text
1. Levantar infraestructura Docker.
2. Arrancar mto-configuration en local con perfil local.
3. Desarrollar y depurar desde IntelliJ.
4. Crear migraciones Flyway para cambios de base de datos.
5. Probar el arranque local.
6. Probar ocasionalmente todo dockerizado.
```

Comandos:

```powershell
docker compose up -d postgres redis rabbitmq keycloak
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Antes de considerar algo terminado, prueba también:

```powershell
docker compose down -v
docker compose up -d --build
```

Así verificas que:

- El `Dockerfile` construye.
- El perfil `docker` funciona.
- Flyway arranca desde cero.
- Hibernate valida contra la base creada por Flyway.
- Keycloak usa el issuer correcto.
- Redis y RabbitMQ responden dentro de Docker.
