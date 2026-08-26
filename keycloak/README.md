# Realm de Keycloak para `mto-configuration`

`mto-realm.json` es la definición del realm que espera la aplicación. Se versiona para que la
configuración del servidor de identidad se revise en pull request como cualquier otro cambio, y
para que los entornos no diverjan por lo que alguien pinchó un día en la consola.

No contiene ningún secreto: los de los clientes confidenciales los genera Keycloak al importar.

## Qué hay dentro

### Clientes

| Cliente | Tipo | Para qué |
|---|---|---|
| `mto-configuration-api` | Confidencial, sin flujos | No inicia ninguna autenticación. Existe para declarar los permisos como roles de cliente y para ser la **audiencia** de los tokens. |
| `mto-frontend` | Público, PKCE S256 | La aplicación de navegador. Lleva el *audience mapper* que nombra a la API. |
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

## Importar

En un Keycloak nuevo:

```bash
kc.sh start --import-realm     # con el fichero en /opt/keycloak/data/import/
```

En uno que ya está en marcha, desde la consola: **Realm settings → Partial import**, con la
estrategia de conflicto en *Skip* si solo se quieren añadir las piezas que falten.

## Después de importar

Cuatro cosas que el fichero no puede traer:

1. **Leer los secretos generados.** Clients → `mto-configuration-svc` → Credentials. Ese valor va
   al gestor de secretos del entorno como `KEYCLOAK_SERVICE_CLIENT_SECRET`, nunca a un fichero del
   repositorio.

2. **Ajustar el destino de la cuenta de servicio.** El *audience mapper* de
   `mto-configuration-svc` apunta a `mto-stock-api` como ejemplo. Debe nombrar al servicio al que
   se vaya a llamar de verdad — al **destino**, no a sí mismo. Si se llama a varios, un mapper por
   cada uno.

3. **Ajustar las URLs del frontal.** `redirectUris` y `webOrigins` vienen con
   `http://localhost:4200`. En un entorno desplegado deben ser los dominios reales, enumerados y
   sin comodín final.

4. **Crear los usuarios y asignarles su perfil.** El realm no trae ninguno a propósito.

## Un realm por entorno

`mto-dev`, `mto-pre`, `mto-pro`. Se separa por entorno y no por aplicación: los usuarios son los
mismos para `mto-configuration` y para `mto-stock`, y un realm compartido evita duplicar
identidades. El aislamiento entre servicios lo dan los roles de cliente, no los realms.

Para cambiar el nombre, el campo `realm` de la primera línea del JSON.

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
