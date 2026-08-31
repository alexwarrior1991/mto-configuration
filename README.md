# mto-configuration

Aplicación backend desarrollada con Spring Boot para gestionar configuración y datos maestros relacionados con infraestructura ferroviaria. El proyecto está organizado por capas y reutiliza componentes comunes para CRUD, búsqueda, validación, auditoría, caché y mensajería asíncrona.

## Estado actual de la aplicación

Actualmente la aplicación contiene una base funcional para exponer y gestionar entidades de configuración del dominio `MTO`:

- Gestión de entidades de infraestructura, como perfiles, vías, estaciones, ménsulas, brazos de atirantado, seccionadores, aisladores de sección y paquetes de ejecución.
- Gestión de tablas maestras o valores catalogados (`LOV`), como tipos de cimentación, anclajes, soportes, pórticos, estados de perfil y otros catálogos de apoyo.
- Capas genéricas para operaciones `CRUD`, lectura, guardado, borrado, cancelación, búsqueda y lectura masiva.
- DTOs separados de las entidades JPA para transportar información hacia/desde la API.
- Mappers con MapStruct para convertir entre entidades y modelos DTO.
- Repositorios JPA con soporte para Querydsl y búsquedas por criterios.
- Servicios síncronos y servicios asíncronos basados en `CompletableFuture` y `@Async`.
- Validadores reutilizables para reglas comunes y reglas específicas de infraestructura/LOV.
- Auditoría con Spring Data auditing y Hibernate Envers.
- Manejo centralizado de excepciones y respuestas de error.
- Integración con Redis para caché.
- Configuración de RabbitMQ para publicar/consumir eventos de datos maestros.
- Integración con Keycloak/OAuth2 mediante Spring Security, Resource Server y clientes Feign para tokens.

## Tecnologías principales

- Java 25.
- Spring Boot 4.0.1.
- Spring MVC para API REST.
- Spring Data JPA con PostgreSQL.
- Spring Security y OAuth2 Resource Server para validación JWT.
- Spring Cloud OpenFeign para llamadas HTTP declarativas.
- Hibernate Envers para auditoría de cambios.
- MapStruct para mapeo entre entidades y DTOs.
- Lombok para reducir código repetitivo.
- Querydsl para consultas dinámicas.
- Redis como sistema de caché.
- RabbitMQ como broker de mensajería.
- Maven como herramienta de construcción.

## Estructura del proyecto

```text
src/main/java/com/alejandro/mtoconfiguration
├── business
├── configuration
├── constant
├── controller
├── core
├── entity
├── enums
├── mapper
├── model
├── repository
├── service
├── utils
└── validator
```

### `business`

Contiene lógica de negocio y abstracciones comunes. Se separan componentes genéricos en `business/commons` y lógica específica del dominio en `business/infrastructure`.

### `configuration`

Agrupa configuración técnica de la aplicación. Actualmente destaca la configuración relacionada con caché.

### `constant`

Contiene constantes reutilizables de la aplicación.

### `controller`

Expone contratos y controladores REST. Incluye:

- `controller/commons`: interfaces reutilizables para controladores base, lectura, búsqueda, guardado, borrado, cancelación y respuestas estándar.
- `controller/synchronous`: controladores síncronos.
- `controller/synchronous/infraestructure`: endpoints del dominio de infraestructura.

### `core`

Incluye componentes transversales de la aplicación:

- `core/audit`: auditoría con Spring Data y Envers.
- `core/exception`: excepciones propias y manejador REST centralizado.
- `core/messaging`: modelo y utilidades para mensajes asíncronos.
- `core/rabbitmq`: configuración, propiedades y constantes de RabbitMQ.
- `core/model`: modelos comunes para respuestas técnicas, como errores.

### `entity`

Define las entidades persistentes JPA:

- `entity/commons`: entidades base, identificadores y soporte común para CRUD.
- `entity/configuration`: entidades de configuración.
- `entity/infrastructure`: entidades principales del dominio ferroviario.
- `entity/lov`: entidades tipo catálogo.
- `entity/lov/commons`: base común para entidades `LOV`.

### `enums`

Contiene enumeraciones de dominio, separadas por áreas como infraestructura y catálogos.

### `mapper`

Contiene mappers MapStruct para convertir entre entidades y DTOs:

- `mapper/commons`: contratos y configuración común.
- `mapper/infraestructure`: mappers de infraestructura.
- `mapper/lov`: mappers de catálogos.
- `mapper/lov/commons`: base común para mappers `LOV`.

### `model`

Contiene DTOs y modelos de entrada/salida:

- `model/commons`: DTOs base, paginación, búsqueda, errores y modelos comunes.
- `model/synchronous/infrastructure`: DTOs de infraestructura.
- `model/synchronous/infrastructure/filter`: filtros para búsquedas de infraestructura.
- `model/synchronous/lov`: DTOs de catálogos.

### `repository`

Agrupa acceso a datos e integraciones externas:

- `repository/jpa`: repositorios JPA para entidades persistentes.
- `repository/jpa/commons`: repositorios base y búsqueda por criterios.
- `repository/jpa/infrastructure`: repositorios de infraestructura.
- `repository/jpa/lov`: repositorios de catálogos.
- `repository/feign`: clientes Feign y configuración para integraciones HTTP.
- `repository/feign/token`: clientes y modelos relacionados con obtención de tokens.

### `service`

Contiene servicios de aplicación:

- `service/commons`: servicios base reutilizables, incluyendo operaciones CRUD y variantes asíncronas.
- `service/infraestructure`: servicios específicos de infraestructura.
- `service/infraestructure/asynchronous`: servicios asíncronos del dominio.
- `service/audit`: servicios relacionados con auditoría.

### `utils`

Funciones auxiliares reutilizables para validaciones, utilidades de infraestructura, ventanas de listas y lógica común.

### `validator`

Contiene reglas de validación:

- `validator/commons`: infraestructura común de validación, catálogo de errores y anotaciones.
- `validator/commons/annotation`: anotaciones personalizadas.
- `validator/commons/validator`: implementaciones de validadores comunes.
- `validator/infrastructure`: validaciones específicas de infraestructura.
- `validator/lov`: validaciones específicas de catálogos.
- `validator/lov/commons`: validaciones base para entidades `LOV`.

## Configuración principal

La configuración principal se encuentra en `src/main/resources/application.yaml`.

### Aplicación

```yaml
spring:
  application:
    name: mto-configuration
```

### Redis

La aplicación usa Redis como sistema de caché:

```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: localhost
      port: 6379
```

También existe una configuración propia bajo `cache.redis` con tiempos de vida diferenciados para elementos normales, listas, páginas, búsquedas y catálogos.

### RabbitMQ

RabbitMQ está habilitado mediante `app.rabbitmq.enabled=true`. La configuración define:

- Exchange principal `mto.master-data.exchange` de tipo `topic`.
- Colas para eventos, caché, auditoría y eliminados.
- Dead letter queues habilitadas.
- Bindings con patrones como `mto.master-data.#` y `mto.master-data.*.deleted`.

### Persistencia

La aplicación usa Spring Data JPA y PostgreSQL. En `application.yaml` se define configuración de Hibernate como `default_batch_fetch_size`, mientras que la dependencia del driver PostgreSQL está declarada en `pom.xml`.

### Seguridad

El proyecto incluye Spring Security y OAuth2 Resource Server para validar tokens JWT. También contiene clientes Feign orientados a obtener tokens desde Keycloak.

## Componentes comunes destacados

### Servicios base

La capa `service/commons` centraliza operaciones reutilizables. Por ejemplo, `BaseAsyncService` encapsula llamadas asíncronas para:

- Buscar por identificador.
- Crear y actualizar DTOs.
- Listar todos los elementos.
- Ejecutar búsquedas paginadas.
- Obtener y procesar varias entidades de forma asíncrona, registrando errores por ID sin detener todo el procesamiento.

### Controladores base

La capa `controller/commons` define contratos reutilizables para construir controladores consistentes. Esto permite que distintas entidades compartan patrones de endpoints y respuestas.

### Repositorios base

La capa `repository/jpa/commons` define repositorios genéricos para entidades CRUD y LOV, además de soporte para búsquedas dinámicas mediante criterios.

### Validación

La validación está separada en componentes comunes y validadores específicos. Esto permite reutilizar reglas transversales y mantener reglas de dominio en paquetes concretos.

## Comandos básicos

Compilar el proyecto:

```bash
./mvnw clean compile
```

Ejecutar tests:

```bash
./mvnw test              # solo los unitarios
./mvnw verify            # unitarios + los *IT, que necesitan Docker (Testcontainers)
```

### Informe de fallos

Maven deja un informe por clase en `target/surefire-reports` (unitarios) y `target/failsafe-reports`
(los `*IT`), pero son decenas de ficheros y la mayoría corresponden a clases que han pasado. Para
juntar en uno solo los que han fallado:

```bash
./mvnw clean verify -DtrimStackTrace=false
./scripts/test-report.sh                      # escribe test-failures.txt
```

`-DtrimStackTrace=false` importa: sin él Maven recorta la traza y a veces se lleva justo la línea
que dice dónde falló de verdad.

Si no hay informes, los tests no llegaron a ejecutarse —normalmente porque falló la compilación— y
el script lo dice: en ese caso lo que hace falta es la salida de Maven.

Arrancar la aplicación:

```bash
./mvnw spring-boot:run
```

En Windows también se puede usar:

```bat
mvnw.cmd spring-boot:run
```

## Servicios externos necesarios

Para ejecutar la aplicación localmente, según la configuración actual, se espera disponer de:

- PostgreSQL para persistencia.
- Redis en `localhost:6379`.
- RabbitMQ en `localhost:5672` con usuario `guest` y contraseña `guest`.
- Keycloak u otro proveedor compatible con OAuth2/JWT si se prueban flujos protegidos o clientes Feign de token.

## Notas actuales

- El proyecto está en una fase de construcción de base backend y arquitectura común.
- La estructura favorece la reutilización mediante clases e interfaces genéricas para CRUD, búsqueda, validación, mapeo y controladores.
- Algunas rutas usan el nombre `infraestructure`; conviene mantenerlo mientras exista en el código para no romper paquetes/imports.
- Este documento describe el estado actual observado del proyecto y puede ampliarse cuando se definan endpoints finales, perfiles de entorno, despliegue o ejemplos de uso de la API.