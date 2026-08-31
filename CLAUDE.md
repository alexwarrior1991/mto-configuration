# CLAUDE.md

## Proyecto
`mto-configuration`: backend Spring Boot para gestión de configuración y datos maestros de infraestructura ferroviaria (dominio `MTO`). Ver `README.md` para detalle funcional completo.

⚠️ `mto-stock` es un **proyecto hermano independiente** (otro repo, otro `pom.xml`), no un módulo de este. No buscar ni mezclar código de stock aquí.

## Stack
Java 25, Spring Boot 4.0.1, Maven **single-module** (sin `<modules>` en `pom.xml`). Detalle completo de dependencias en `README.md`.

## Estructura de paquetes (`src/main/java/com/alejandro/mtoconfiguration`)
| Paquete | Subpaquetes |
|---|---|
| `business` | `commons`, `infrastructure` |
| `controller` | `synchronous`, `asynchronous`, `commons` |
| `service` | `commons`, `infraestructure` (+ `asynchronous`, `jobs`), `lov`, `audit` |
| `repository` | `jpa` (+ `jobs`), `feign` |
| `entity` | `commons`, `infrastructure`, `configuration`, `lov`, `jobs` |
| `model` | `commons`, `synchronous` (+ `infrastructure/jobs`), `audit` |
| `mapper` | `commons`, `infraestructure`, `lov` |
| `validator` | `commons`, `infrastructure`, `lov` |
| `enums` | `infrastructure`, `lov`, `jobs` |
| `configuration` | `cache`, `security` |
| `core` | `audit`, `exception`, `messaging`, `outbox`, `rabbitmq`, `model` |
| `masterdata` | `messaging` |
| `constant`, `utils` | transversales, sin subpaquetes |

Tests en `src/test/java`, misma raíz de paquete.

## Documentación relacionada
- `README.md` — build, comandos, dependencias, estructura funcional
- `README_API.md` — uso de los endpoints (alta, modificación, colecciones de hijos, consultas, errores)
- `README_FLYWAY.md` — migraciones de BD
- `README_LOCAL_DOCKER.md` — entorno local con Docker
- `README_MESSAGING.md` — RabbitMQ / eventos
- `README_ASYNC_JOBS.md` — trabajos en segundo plano (202 Accepted + jobId), capa paralela a `/async`

## Reglas específicas
Reglas por capa (controllers, repositories, tests, etc.) viven en `.claude/rules/`, no aquí.
