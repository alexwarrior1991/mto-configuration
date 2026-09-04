# Realm de Keycloak para `mto-configuration`

Estos ficheros son la definición del realm que espera la aplicación. Se versionan para que la
configuración del servidor de identidad se revise en pull request como cualquier otro cambio, y
para que los entornos no diverjan por lo que alguien pinchó un día en la consola.

El realm base lo crea y lo posee
[`mto-platform`](https://github.com/alexwarrior1991/mto-platform), y cada servicio aporta desde su
propio repositorio lo suyo. Aquí quedan dos ficheros:

| Fichero | Qué aporta |
|---|---|
| `mto-configuration-partial-import.json` | Los clientes `mto-configuration-api` y `mto-configuration-svc`, los permisos y los perfiles `mto-viewer`/`mto-editor`/`mto-admin`/`mto-auditor`. Sin usuarios y sin secretos: vale para cualquier entorno. |
| `mto-configuration-dev.json` | Los cinco usuarios de desarrollo y el secreto fijo de la cuenta de servicio. Aparte a propósito, para poder aplicar lo anterior en un entorno desplegado sin arrastrarlos. |

Que los roles vivan aquí y no en el repositorio de plataforma es deliberado: así un permiso se
cambia en el mismo commit que el código que lo comprueba (`SecurityRoles`).

Los aplica `mto-platform/keycloak/apply-partials.sh`, junto a los de los otros dos servicios y **en
orden**: primero las parciales que crean los clientes, después `mto-ops-cross-service.json`, que los
nombra. Ese guion es la única forma soportada de obtener el realm completo.

Antes esto estaba repartido en cinco ficheros de tres repositorios y no lo ensamblaba nadie: los
dos `mto-realm-local.json` habían divergido hasta ser realms distintos con el mismo nombre, así que
ganaba el stack que arrancases primero y el otro servicio se quedaba sin su cliente.

## Qué hay dentro

### Clientes

| Cliente | Tipo | Para qué |
|---|---|---|
| `mto-configuration-api` | Confidencial, sin flujos | No inicia ninguna autenticación. Existe para declarar los permisos como roles de cliente y para ser la **audiencia** de los tokens. |
| `mto-frontend` | Público, PKCE S256 | La aplicación de navegador. Lleva un *audience mapper* por cada API del dominio a la que llama: `mto-configuration-api`, `mto-stock-api` y `mto-gateway-api`. |
| `mto-configuration-svc` | Confidencial, cuenta de servicio | `client_credentials` para las llamadas salientes a otros servicios. |

### Permisos y perfiles

Los **permisos** son roles de cliente de `mto-configuration-api` y son lo que comprueba el código.
Los **perfiles** son roles de realm compuestos que los agrupan, y son lo que se asigna a las
personas. Keycloak expande los compuestos al emitir el token, así que un usuario con un perfil
llega con sus permisos dentro de `resource_access`.

La ventaja de separarlos: cambiar lo que puede hacer un perfil se hace aquí, sin desplegar.

| Perfil | Agrupa |
|---|---|
| `mto-viewer` | `config-read` |
| `mto-editor` | `config-read`, `config-write`, `config-import` |
| `mto-admin` | los de editor + `config-delete`, `lov-manage`, `config-audit` |
| `mto-auditor` | `config-read`, `config-audit` |

`mto-ops` no está en esta lista: es el perfil de quien explota la plataforma entera y lo define
`mto-platform/keycloak/mto-ops-cross-service.json`, que agrupa el Actuator de los tres servicios.
Ver más abajo.

Los roles de realm **nunca** deben llamarse igual que un permiso. El converter emite los de realm
solo como `ROLE_REALM_*` precisamente para que no se confundan, pero conviene no tentar a la suerte.

## Cómo cargarlo

### Lo normal: el guion de `mto-platform`

```bash
cd ../mto-platform
cp .env.example .env    # define al menos KC_BOOTSTRAP_ADMIN_PASSWORD
docker compose up -d
./keycloak/apply-partials.sh                 # con los usuarios de desarrollo
./keycloak/apply-partials.sh --no-dev-users  # solo clientes, permisos y perfiles
```

El contenedor importa el realm base al arrancar; el guion aplica encima las parciales de los tres
servicios, en orden y con `ifResourceExists: OVERWRITE`, de modo que se puede reejecutar.

Keycloak queda en `http://auth.mto.local:8082` (consola: `admin` / lo que pusieras en
`KC_BOOTSTRAP_ADMIN_PASSWORD`) con el realm `mto` completo y, salvo `--no-dev-users`, cinco usuarios
de desarrollo de este servicio, todos con contraseña `local`:

| Usuario | Perfil |
|---|---|
| `config.lector` | `mto-viewer` |
| `config.editor` | `mto-editor` |
| `config.responsable` | `mto-admin` |
| `config.auditor` | `mto-auditor` |
| `config.ops` | `mto-ops` |

Esos usuarios existen **solo** en `mto-configuration-dev.json`;
`mto-configuration-partial-import.json` no trae ninguno a propósito.

La importación del realm **base** solo actúa si el realm no existe todavía: Keycloak no la repite
sobre uno ya creado, así que un cambio en él exige partir de cero con
`docker compose --profile all down -v` en `mto-platform`. Las parciales no tienen esa limitación:
`apply-partials.sh` las aplica con `OVERWRITE` cuantas veces haga falta.

Para pedir un token con `curl` sin montar el flujo del navegador hace falta que `mto-frontend`
tenga `directAccessGrantsEnabled`, que fuera de local debe estar cerrado:

```bash
curl -s -X POST http://auth.mto.local:8082/realms/mto/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mto-frontend \
  -d username=config.editor -d password=local
```

### A mano, sobre un realm que ya existe

Desde la consola: **Realm settings → Partial import**, subiendo
`mto-configuration-partial-import.json`. La estrategia de conflicto por defecto es *Fail*: para
reaplicarlo sobre un realm que ya tiene parte de esto, úsese *Overwrite*.

## Después de importar

Cuatro cosas que `mto-configuration-partial-import.json` no puede traer. En local, las dos primeras
vienen ya resueltas por `mto-configuration-dev.json`:

1. **Leer los secretos generados.** Clients → `mto-configuration-svc` → Credentials. Ese valor va
   al gestor de secretos del entorno como `KEYCLOAK_SERVICE_CLIENT_SECRET`, nunca a un fichero del
   repositorio. En local no hace falta: `mto-configuration-dev.json` fija ese secreto con el mismo
   valor que trae `.env.example`.

2. **Ajustar el destino de la cuenta de servicio.** El *audience mapper* de
   `mto-configuration-svc` apunta a `mto-stock-api` como ejemplo. Debe nombrar al servicio al que
   se vaya a llamar de verdad — al **destino**, no a sí mismo. Si se llama a varios, un mapper por
   cada uno.

3. **Ajustar las URLs del frontal.** `redirectUris` y `webOrigins` vienen con
   `http://localhost:4200`. En un entorno desplegado deben ser los dominios reales, enumerados y
   sin comodín final.

4. **Crear los usuarios y asignarles su perfil.** `mto-configuration-partial-import.json` no trae
   ninguno a propósito; los de desarrollo están solo en `mto-configuration-dev.json`.

## El perfil `mto-ops` y los tres servicios

`mto-ops` es el perfil de quien explota la plataforma, y explotar la plataforma es explotar los tres
servicios. Pero los permisos son roles de **cliente**, y el converter solo lee los del cliente
propio de cada aplicación:

```java
authorities.addAll(extractClientRoles(jwt, securityProperties.clientId()));
```

`ops-metrics` existe en `mto-configuration-api`, en `mto-stock-api` y en `mto-gateway-api` como tres
permisos distintos que se llaman igual. Un `mto-ops` que solo llevara los de esta aplicación
recibiría un 403 en el Actuator de las otras dos.

Por eso `mto-ops` no se define aquí, sino en `mto-platform/keycloak/mto-ops-cross-service.json`, que
se aplica **cuando los tres clientes ya están en el realm**: un compuesto solo puede nombrar roles
de clientes que existan, y si no existen Keycloak aborta la importación entera con *App doesn't
exist in role definitions*.

Una importación parcial **reescribe el rol entero**, así que ese fichero es la definición completa
del perfil: lo que no esté allí, no lo tiene nadie. `mto-platform/scripts/check_realm_consistency.py`
recorre todos los ficheros en el orden en que se aplican y falla si un compuesto nombra un cliente o
un rol que todavía no existe, si `mto-ops` se define en más de un sitio, o si un servicio declara un
rol `ops-*` que el perfil no cubre.

Los *audience mapper* no tienen ese problema: su destino se resuelve al emitir el token, no al
importar, así que sí pueden nombrar un cliente que aún no existe. Por eso los de `mto-stock-api` y
`mto-gateway-api` viven directamente en `mto-frontend`.

### Y con el gateway, tres

`mto-gateway` entra por la misma puerta que `mto-stock` y que esta aplicación: aporta su cliente
`mto-gateway-api` y sus roles `ops-metrics` / `ops-write` con
`keycloak/mto-gateway-partial-import.json`, **en su propio repositorio**. Lo suyo que vive fuera de
él son dos cosas, las dos en `mto-platform`:

- el *audience mapper* de `mto-frontend`, que ya emite `mto-gateway-api` para que activar
  `KEYCLOAK_AUDIENCE_VALIDATION_ENABLED` en el gateway sea cambiar una variable y no tocar el realm
  con prisa;
- `mto-gateway-api` dentro del compuesto de `mto-ops-cross-service.json`, porque explotar la
  plataforma es explotar **las tres** cosas. Sin esa entrada, quien tiene el perfil `mto-ops` lee
  `/actuator/prometheus` de las dos aplicaciones pero no el del gateway, que es justo donde se ven
  las métricas de todo el tráfico de entrada.

El gateway no declara ningún perfil de realm propio: no tiene permisos de negocio que agrupar. Su
autorización se acaba en su propio Actuator — quién puede leer o escribir qué lo siguen decidiendo
`mto-configuration` y `mto-stock`.

Orden de aplicación: primero las importaciones parciales de los tres servicios (crean los
clientes), después `mto-ops-cross-service.json` (los nombra). Al revés, Keycloak responde *App
doesn't exist in role definitions*. Lo fija `mto-platform/keycloak/apply-partials.sh`.

## Un realm por entorno

`mto-dev`, `mto-pre`, `mto-pro`. Se separa por entorno y no por aplicación: los usuarios son los
mismos para `mto-configuration` y para `mto-stock`, y un realm compartido evita duplicar
identidades. El aislamiento entre servicios lo dan los roles de cliente, no los realms.

Para cambiar el nombre, el campo `realm` de `mto-platform/keycloak/mto-realm.json` y de
`mto-realm-local.json`. Los dos deben declarar lo mismo salvo los ajustes propios de local; que no
se separen lo vigila `mto-platform/scripts/check_realm_consistency.py`, porque un cambio hecho en
uno solo deja el entorno local probando algo distinto de lo que se despliega.

El realm `master` se reserva para administrar Keycloak. Alojar ahí la aplicación pondría a
cualquiera de sus usuarios a un rol de distancia de administrar todo el servidor de identidad.

## El error más fácil de cometer

Si falta el *audience mapper*, la API rechaza **todos** los tokens con un 401 sin más explicación.
Keycloak añade la audiencia por su cuenta solo cuando el usuario tiene roles en ese cliente, de
modo que el fallo aparece justo con quien no los tiene —una cuenta de servicio, un usuario
recién creado— y parece intermitente. El mapper explícito es lo que la garantiza siempre.

## Comprobar que quedó bien

`KeycloakAuthorizationIT` levanta un Keycloak con un realm de la misma forma que este
(`src/test/resources/keycloak/mto-test-realm.json`, con usuarios de prueba añadidos) y verifica
contra él los permisos por verbo, la expansión de los compuestos y la audiencia:

```bash
mvn verify -Dit.test=KeycloakAuthorizationIT -Dtest=NONE \
  -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```
