# Realm de Keycloak para `mto-configuration`

Estos ficheros son la definición del realm que espera la aplicación. Se versionan para que la
configuración del servidor de identidad se revise en pull request como cualquier otro cambio, y
para que los entornos no diverjan por lo que alguien pinchó un día en la consola.

| Fichero | Cuándo |
|---|---|
| `mto-realm.json` | Definición de referencia, la que se lleva a un entorno desplegado. Sin usuarios y sin secretos. |
| `mto-realm-local.json` | La misma configuración para el stack de `docker compose` de este repositorio. Añade usuarios de desarrollo y fija el secreto de la cuenta de servicio. |
| `mto-ops-cross-service.json` | Importación parcial que extiende el perfil `mto-ops` a las dos aplicaciones. Se aplica **después** de que `mto-stock` haya creado su cliente. |

`mto-realm.json` no contiene ningún secreto: los de los clientes confidenciales los genera Keycloak
al importar. `mto-realm-local.json` sí trae uno, pero es el del stack local y sirve exactamente
para eso; nada de lo que hay en él debe acercarse a un entorno desplegado.

`mto-stock` vive en este mismo realm y añade lo suyo con su propio fichero de importación parcial
(`mto-stock/keycloak/mto-stock-partial-import.json`).

## Qué hay dentro

### Clientes

| Cliente | Tipo | Para qué |
|---|---|---|
| `mto-configuration-api` | Confidencial, sin flujos | No inicia ninguna autenticación. Existe para declarar los permisos como roles de cliente y para ser la **audiencia** de los tokens. |
| `mto-frontend` | Público, PKCE S256 | La aplicación de navegador. Lleva un *audience mapper* por cada API del dominio a la que llama: `mto-configuration-api` y `mto-stock-api`. |
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
| `mto-ops` | `config-read`, `ops-metrics`, `ops-write` |

Los roles de realm **nunca** deben llamarse igual que un permiso. El converter emite los de realm
solo como `ROLE_REALM_*` precisamente para que no se confundan, pero conviene no tentar a la suerte.

## Cómo cargarlo

### Automático: el stack local de este repositorio

No hay que hacer nada. El servicio `keycloak` de `docker-compose.yaml` monta
`mto-realm-local.json` en `/opt/keycloak/data/import` y arranca con `--import-realm`:

```bash
cp .env.example .env    # define al menos KC_BOOTSTRAP_ADMIN_PASSWORD
docker compose up -d postgres redis rabbitmq keycloak
```

Keycloak queda en `http://auth.mto.local:8082` (consola: `admin` / lo que pusieras en
`KC_BOOTSTRAP_ADMIN_PASSWORD`) con el realm `mto` importado y cinco usuarios de desarrollo, todos
con contraseña `local`:

| Usuario | Perfil |
|---|---|
| `config.lector` | `mto-viewer` |
| `config.editor` | `mto-editor` |
| `config.responsable` | `mto-admin` |
| `config.auditor` | `mto-auditor` |
| `config.ops` | `mto-ops` |

Esos usuarios existen **solo** en `mto-realm-local.json`; `mto-realm.json` no trae ninguno a
propósito.

La importación solo actúa si el realm no existe todavía: Keycloak no la repite sobre uno ya creado.
Para recoger un cambio del JSON hay que partir de cero con `docker compose down -v`.

En local, `mto-frontend` lleva además `directAccessGrantsEnabled`, de modo que se puede pedir un
token con `curl` sin montar el flujo del navegador:

```bash
curl -s -X POST http://auth.mto.local:8082/realms/mto/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mto-frontend \
  -d username=config.editor -d password=local
```

En `mto-realm.json` ese grant está cerrado, que es como debe estar fuera de local.

### Automático: un Keycloak nuevo, fuera de compose

```bash
kc.sh start --import-realm     # con el fichero en /opt/keycloak/data/import/
```

### Sobre un realm que ya existe

Desde la consola: **Realm settings → Partial import**, con la estrategia de conflicto en *Skip* si
solo se quieren añadir las piezas que falten.

## Después de importar

Cuatro cosas que `mto-realm.json` no puede traer. En el stack local, las dos primeras vienen ya
resueltas por `mto-realm-local.json`:

1. **Leer los secretos generados.** Clients → `mto-configuration-svc` → Credentials. Ese valor va
   al gestor de secretos del entorno como `KEYCLOAK_SERVICE_CLIENT_SECRET`, nunca a un fichero del
   repositorio. En local no hace falta: `mto-realm-local.json` fija ese secreto con el mismo valor
   que trae `.env.example`.

2. **Ajustar el destino de la cuenta de servicio.** El *audience mapper* de
   `mto-configuration-svc` apunta a `mto-stock-api` como ejemplo. Debe nombrar al servicio al que
   se vaya a llamar de verdad — al **destino**, no a sí mismo. Si se llama a varios, un mapper por
   cada uno.

3. **Ajustar las URLs del frontal.** `redirectUris` y `webOrigins` vienen con
   `http://localhost:4200`. En un entorno desplegado deben ser los dominios reales, enumerados y
   sin comodín final.

4. **Crear los usuarios y asignarles su perfil.** `mto-realm.json` no trae ninguno a propósito;
   los de desarrollo están solo en `mto-realm-local.json`.

## El perfil `mto-ops` y las dos aplicaciones

`mto-ops` es el perfil de quien explota la plataforma, y explotar la plataforma es explotar las dos
aplicaciones. Pero los permisos son roles de **cliente**, y el converter solo lee los del cliente
propio de cada aplicación:

```java
authorities.addAll(extractClientRoles(jwt, securityProperties.clientId()));
```

`ops-metrics` existe en `mto-configuration-api` y en `mto-stock-api` como dos permisos distintos que
se llaman igual. Por eso `mto-ops` tal y como lo define `mto-realm.json` abre el Actuator de esta
aplicación y no el de `mto-stock`.

Lo que lo arregla no puede ir dentro de `mto-realm.json`: un compuesto solo puede nombrar roles de
clientes que existan en el realm que se está importando, y `mto-realm.json` es justo el fichero que
**crea** el realm, cuando `mto-stock-api` todavía no está. Keycloak aborta la importación entera con
*App doesn't exist in role definitions* y el realm se queda sin crear. En `mto-realm-local.json` el
cliente no llega a existir nunca. `RealmDefinitionsTest` comprueba que ningún compuesto de los dos
ficheros nombre un cliente ausente, precisamente para que nadie lo intente.

De ahí `mto-ops-cross-service.json`, que se aplica como importación parcial cuando las dos
aplicaciones ya están en el realm:

```bash
curl -X POST "$KC_URL/admin/realms/mto/partialImport" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @keycloak/mto-ops-cross-service.json
```

Una importación parcial **reescribe el rol entero**, así que el fichero repite los permisos que
`mto-realm.json` ya da a `mto-ops` sobre `mto-configuration-api`. Olvidarlos dejaría al perfil de
explotación sin el Actuator de esta aplicación — es la razón por la que `mto-stock` no metió
`mto-ops` en su propia importación parcial, y `RealmDefinitionsTest` lo vigila.

Los *audience mapper* no tienen ese problema: su destino se resuelve al emitir el token, no al
importar, así que sí pueden nombrar un cliente que aún no existe. Por eso el de `mto-stock-api` vive
directamente en `mto-frontend`.

## Un realm por entorno

`mto-dev`, `mto-pre`, `mto-pro`. Se separa por entorno y no por aplicación: los usuarios son los
mismos para `mto-configuration` y para `mto-stock`, y un realm compartido evita duplicar
identidades. El aislamiento entre servicios lo dan los roles de cliente, no los realms.

Para cambiar el nombre, el campo `realm` de la primera línea del JSON — en los dos ficheros.

Permisos y perfiles deben ser idénticos en ambos: `mto-realm-local.json` es `mto-realm.json` más
usuarios, el secreto local y el grant de acceso directo. Un cambio de permisos que se haga solo en
uno de los dos deja el stack local probando algo distinto de lo que se despliega.

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
