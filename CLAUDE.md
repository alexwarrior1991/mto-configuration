# CLAUDE.md

## Proyecto
`mto-configuration`: backend Spring Boot para gestión de configuración y datos maestros de infraestructura ferroviaria (dominio `MTO`). Ver `README.md` para detalle funcional completo.

⚠️ `mto-stock` es un **proyecto hermano independiente** (otro repo, otro `pom.xml`), no un módulo de este. No buscar ni mezclar código de stock aquí.

⚠️ La infraestructura local (PostgreSQL, Redis, RabbitMQ, Keycloak, colector OTLP) la levanta `mto-platform`, otro repo hermano. `compose.yaml` de aquí trae **solo la aplicación**. Un único broker compartido: mientras cada repo levantaba el suyo, los eventos de datos maestros no llegaban a `mto-stock` y no fallaba nada.

## Stack
Java 25, Spring Boot 4.0.1, Maven **single-module** (sin `<modules>` en `pom.xml`). Detalle completo de dependencias en `README.md`.

## Estructura de paquetes (`src/main/java/com/alejandro/mtoconfiguration`)
| Paquete | Subpaquetes |
|---|---|
| `business` | `commons`, `infrastructure` |
| `controller` | `synchronous`, `asynchronous`, `commons` |
| `service` | `commons`, `infraestructure` (+ `asynchronous`, `jobs`, `imports`), `lov` (+ `imports`), `audit` |
| `repository` | `jpa` (+ `jobs`), `feign` |
| `entity` | `commons`, `infrastructure`, `configuration`, `lov`, `jobs` |
| `model` | `commons`, `synchronous` (+ `infrastructure/jobs`, `infrastructure/imports`, `lov/imports`), `audit` |
| `mapper` | `commons`, `infraestructure`, `lov` |
| `validator` | `commons`, `infrastructure` |
| `enums` | `infrastructure`, `lov`, `jobs` |
| `configuration` | `cache`, `security`, `web` |
| `core` | `audit`, `constraints`, `excel`, `exception`, `messaging`, `outbox`, `rabbitmq`, `model` |
| `masterdata` | `messaging` |
| `constant`, `utils` | transversales, sin subpaquetes |

Tests en `src/test/java`, misma raíz de paquete.

## Documentación relacionada
- `README.md` — build, comandos, dependencias, estructura funcional
- `README_API.md` — uso de los endpoints (alta, modificación, colecciones de hijos, consultas, errores)
- `README_FLYWAY.md` — migraciones de BD
- `README_LOCAL_DOCKER.md` — entorno local con Docker (la infraestructura vive en `mto-platform`)
- `README_MESSAGING.md` — RabbitMQ / eventos
- `keycloak/README.md` — qué aporta este repo al realm (`mto-configuration-partial-import.json` y `mto-configuration-dev.json`); el realm base y el orden de ensamblado son de `mto-platform`
- `README_ASYNC_JOBS.md` — trabajos en segundo plano (202 Accepted + jobId), capa paralela a `/async`
- `data/README.md` — los dos maestros generados desde los workbooks (`lov-master.xlsx` y `profile-master.xlsx`), sus generadores en Python y `topology.yml`, que es donde se declara lo que no está en los ficheros

## Reglas específicas
Las reglas por capa viven en `.claude/rules/`, una capa por fichero, y no aquí. Claude Code carga cada una al tocar los ficheros de su capa (campo `paths:` de su cabecera), y cada regla dice qué test o qué mecanismo la hace cumplir.

| Fichero | Capa |
|---|---|
| `controllers.md` | rutas, códigos de respuesta, errores (`ProblemDetail`) y permisos por ruta |
| `services.md` | servicios, validadores y los eventos de cada escritura |
| `mappers.md` | MapStruct: ids de padre, catálogos y colecciones de hijos |
| `persistence.md` | entidades, repositorios, Flyway, auditoría y los `*IT` con PostgreSQL |
| `cache.md` | Redis: claves, qué se puede cachear y la invalidación |
| `messaging.md` | el outbox y el contrato de los eventos de datos maestros |
| `tests.md` | fases, estilo y tests guardianes |
