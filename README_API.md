# API de configuración — cómo se usan los endpoints

Guía práctica de las rutas síncronas de infraestructura, las listas de valores y la fachada
asíncrona. El foco está en lo que **no** se deduce leyendo el OpenAPI: qué código devuelve cada
verbo, quién manda cuando el id viaja dos veces, y sobre todo **cómo se tratan las colecciones de
hijos en una modificación**, que es donde es fácil equivocarse y borrar datos.

Ruta base: `/api/v1/configuration` — en los ejemplos aparece como `$BASE`.

---

## 1. Un vistazo rápido

| Verbo y ruta | Código | Cuerpo de respuesta |
|---|---|---|
| `GET $BASE/{recurso}/{id}` | `200` | el DTO |
| `POST $BASE/{recurso}` | **`201`** | el DTO creado, con su `id` |
| `POST $BASE/{recurso}/bulk` | **`201`** | array de DTO creados |
| `PUT $BASE/{recurso}/{id}` | `200` | el DTO actualizado |
| `PUT $BASE/{recurso}/bulk` | `200` | array de DTO actualizados |
| `DELETE $BASE/{recurso}/{id}` | **`204`** | vacío |
| `GET $BASE/{recurso}/paged` | `200` | página de Spring Data |
| `POST $BASE/{recurso}/search` | `200` | página de Spring Data |
| `POST $BASE/{recurso}/filter` | `200` | página de Spring Data |

Recursos de infraestructura: `cantilevers`, `disconnectors`, `execution-packages`, `profiles`,
`section-insulators`, `stations`, `steady-arms`, `tracks`.

El borrado es **lógico**: marca la fila (`deleted = true`) y deja de aparecer en las consultas. No
hay endpoint para restaurarla.

---

## 2. Alta

```bash
curl -X POST "$BASE/tracks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "VIA 1",
        "enabled": true,
        "executionPackageId": 100
      }'
```

Responde **201** con el DTO completo, ya con `id`, `createUser`, `createDate` y `versionNumber`.

Las propiedades de auditoría que mandes en el cuerpo **se ignoran**. Es deliberado: las pone el
servidor, y aceptarlas del cliente permitiría falsear quién creó un registro. Puedes mandarlas o
no, da igual.

### Alta con hijos anidados

Los hijos van dentro del padre, **sin `id`** (todavía no existen):

```bash
curl -X POST "$BASE/profiles" \
  -H "Content-Type: application/json" \
  -d '{
        "profileId": "P-001",
        "kp": "10.500",
        "trackId": 3,
        "cantilevers": [
          { "cwHeight": "5.500", "stagger": "200" },
          { "cwHeight": "6.000", "stagger": "210" }
        ]
      }'
```

Se insertan las tres filas (perfil + dos ménsulas) en la misma transacción, y cada ménsula queda
con su clave ajena al perfil. La respuesta trae los `id` asignados.

### Alta en lote

```bash
curl -X POST "$BASE/tracks/bulk" \
  -H "Content-Type: application/json" \
  -d '[{ "name": "VIA 1", "enabled": true },
       { "name": "VIA 2", "enabled": true }]'
```

Responde **201**. Es **una sola transacción**: si un elemento falla la validación, no se guarda
ninguno. Los errores llegan con la ruta del campo indexada: `[1].name`.

---

## 3. Modificación

```bash
curl -X PUT "$BASE/tracks/42" \
  -H "Content-Type: application/json" \
  -d '{ "id": 999, "name": "VIA PRINCIPAL", "enabled": true }'
```

**El id de la ruta manda.** El controlador hace `dto.setId(42)` antes de delegar, así que ese `999`
del cuerpo se descarta. Es lo que impide modificar el recurso 999 llamando a la ruta del 42.

La auditoría tampoco se pisa: `createUser`, `createDate` y `versionNumber` conservan lo que hay en
base de datos aunque mandes otra cosa.

---

## 4. Colecciones de hijos en una modificación — **léelo antes de tocar nada**

Esta es la parte que hay que entender. Al modificar un padre, **la lista de hijos que mandas es el
estado final completo**, no una lista de cambios. El servidor la compara con lo que hay guardado y
decide qué hacer con cada hijo según traiga `id` o no:

| Lo que mandas | Lo que hace el servidor |
|---|---|
| hijo **con `id`** que ya existe | **UPDATE** de esa fila |
| hijo **sin `id`** | **INSERT** de fila nueva |
| hijo que existe y **no mandas** | **DELETE** de esa fila |
| hijo con un `id` que no es de este padre | se **ignora** |

### Ejemplo completo

Perfil 7 con tres ménsulas en base de datos:

```
id=1  cw_height=5.500
id=2  cw_height=6.000
id=3  cw_height=6.500
```

Quieres: cambiar la altura de la 1, dejar la 2, borrar la 3 y añadir una nueva.

```bash
curl -X PUT "$BASE/profiles/7" \
  -H "Content-Type: application/json" \
  -d '{
        "profileId": "P-001",
        "kp": "10.500",
        "trackId": 3,
        "cantilevers": [
          { "id": 1, "cwHeight": "9.999", "stagger": "200" },
          { "id": 2, "cwHeight": "6.000", "stagger": "210" },
          {           "cwHeight": "7.777", "stagger": "220" }
        ]
      }'
```

Resultado:

```
id=1     UPDATE  cw_height=9.999    <- venía con id: se actualiza
id=2     UPDATE  cw_height=6.000    <- venía con id: se actualiza (sin cambios reales)
id=57    INSERT  cw_height=7.777    <- venía sin id: fila nueva
                                       la 3 no venía: BORRADA
```

### Los dos errores que cuestan datos

**No mandar la colección con la intención de "no tocarla".** Una lista vacía o ausente significa
*«el padre no tiene ninguno»*, y **borra todos los hijos**:

```jsonc
// PUT $BASE/profiles/7
{ "profileId": "P-001", "kp": "10.500", "cantilevers": [] }
// -> borra las tres ménsulas
```

Si sólo quieres cambiar el `kp` del perfil, **devuelve las ménsulas tal como las leíste**, con sus
`id`.

**Perder los `id` al reenviar.** Si mandas los mismos hijos pero sin su `id`, el servidor entiende
que los existentes se han quitado y que llegan otros nuevos: borra tres filas e inserta tres. Los
datos acaban pareciendo correctos pero cambian de `id`, pierden su histórico de auditoría y
rompen cualquier referencia que apuntara a ellos.

**Regla práctica:** lee el recurso, modifica sobre lo leído, devuélvelo entero. Ni construyas el
cuerpo desde cero ni le quites campos.

### Dónde aplica

| Padre | Colecciones |
|---|---|
| `execution-packages` | `tracks`, `stations` |
| `stations` | `tracks`, `disconnectors`, `sectionInsulators` |
| `tracks` | `profiles` |
| `profiles` | `cantilevers` |

Y **anida**: en un `PUT $BASE/execution-packages/{id}` puedes traer vías, y dentro de cada vía sus
perfiles, y dentro de cada perfil sus ménsulas. Cada nivel se reconcilia con la misma regla.

### En qué orden te llegan los hijos

El orden lo decide el servidor, no la posición en la que los mandaste:

- **Perfiles de una vía** → por punto kilométrico (`kp`), y el `id` desempata. Es el orden físico a
  lo largo de la vía, y el mismo que devuelven `/profiles/track/{id}/keyset` y `/range`.
- **Resto de colecciones** → por `id`, que es simplemente un orden estable.

Mandar los hijos en otro orden no cambia nada: no hay forma de reordenarlos desde la API. Si
necesitas mover un perfil dentro de la vía, lo que se cambia es su `kp`.

La relación 1:1 (`profiles.disconnector`, `cantilevers.steadyArm`) va aparte: mandar el objeto lo
crea o actualiza, mandar `null` lo desvincula.

---

## 5. Listas de valores

Las LOV van por código, no por id. Al referenciarlas desde otra entidad basta el `code`:

```jsonc
{ "profileId": "P-001", "poleType": { "code": "PT1" } }
```

El servidor resuelve el código contra el catálogo. Lo que mandes en `description` u otros campos de
la LOV **se ignora**: manda el catálogo, no la petición.

Endpoints propios de cada LOV (`anchorages`, `pole-types`, `profile-statuses`, `sectionings`,
`portals`, `foundations`, `cantilever-types`, `steady-arm-types`, …):

```bash
GET    $BASE/pole-types            # 200, catálogo completo
GET    $BASE/pole-types/{id}       # 200
GET    $BASE/pole-types/code/PT1   # 200, por código
POST   $BASE/pole-types            # 201
POST   $BASE/pole-types/bulk       # 201
PUT    $BASE/pole-types/{id}       # 200
PUT    $BASE/pole-types/bulk       # 200
DELETE $BASE/pole-types/{id}       # 204
```

Un id o código inexistente responde **404**.

Escribir en una LOV exige el rol **`LOV_MANAGE`** además del permiso de escritura habitual. Es
deliberado: un perfil de edición diaria (`mto-editor`) mantiene infraestructura sin poder tocar el
catálogo del que depende todo lo demás.

---

## 6. Consultas

### Paginada

```bash
GET "$BASE/profiles/paged?page=0&size=20&sort=kp,desc"
```

Sin parámetros: página 0 de **20 elementos**.

### Filtro funcional (QueryDSL)

Campos concretos, combinados con AND. Los vacíos no filtran.

```bash
curl -X POST "$BASE/profiles/filter?page=0&size=20" \
  -H "Content-Type: application/json" \
  -d '{ "trackId": 3, "profileStatusCode": "OK", "searchText": "torre" }'
```

`searchText` busca a la vez en varias columnas, incluidas las de tablas asociadas (en perfiles:
identificador, nombre de vía y nombre de estación).

### Búsqueda por criteria

Más genérica: mapa de filtros libre más paginación y orden.

```bash
curl -X POST "$BASE/profiles/search" \
  -H "Content-Type: application/json" \
  -d '{
        "filters": { "name": "via", "enabled": true },
        "pageable": { "page": 0, "size": 50,
                      "sortBy": ["name"], "sortDirection": ["asc"] }
      }'
```

`sortBy` va contra una **lista blanca** por recurso. Una columna no permitida no da error: se
ignora y se aplica el orden por defecto.

Cuidado con los filtros booleanos, que no se comportan igual en todos los recursos:

- `disconnectors` → `onLoad: true` filtra; **`onLoad: false` no filtra nada** (devuelve todo).
- `section-insulators` → `enabled: false` sí devuelve los deshabilitados.

### Ventanas de perfiles por vía

Para recorrer una vía entera sin paginar por offset:

```bash
GET "$BASE/profiles/track/3/keyset?pageSize=50"                    # primera ventana
GET "$BASE/profiles/track/3/keyset?lastKp=12.5&lastId=99&pageSize=50"  # siguiente
GET "$BASE/profiles/track/3/range?startKp=1.5&endKp=9.75"          # tramo por KP
```

El cursor son **los dos valores juntos** (`lastKp` y `lastId`). Mandar sólo uno se trata como
primera ventana; hacen falta los dos porque dos perfiles pueden compartir KP.

---

## 7. Fachada asíncrona

Mismos recursos y mismos cuerpos bajo `/api/v1/configuration/async`:

```bash
POST "$BASE/async/profiles"        # 201
PUT  "$BASE/async/profiles/{id}"   # 200
GET  "$BASE/async/profiles/paged"
```

La petición se atiende en un hilo del pool, pero **se responde en la misma conexión**: el cliente
espera igual. Los errores salen con el mismo cuerpo que en la vía síncrona.

Si lo que quieres es no esperar, lo que necesitas son los trabajos del apartado siguiente.

---

## 8. Trabajos en segundo plano

Para exportaciones y cargas masivas grandes. Devuelven **202** al instante con un `jobId`.

```bash
# lanzar
curl -X POST "$BASE/profiles/jobs/export?trackId=42&mapperType=basic"
# 202 + Location: $BASE/profiles/jobs/{jobId}

curl -X POST "$BASE/profiles/jobs/bulk-create" \
  -H "Content-Type: application/json" -d '[ ... ]'
# 202

# consultar estado
curl "$BASE/profiles/jobs/{jobId}"        # 200: PENDING | RUNNING | COMPLETED | FAILED

# descargar el resultado
curl "$BASE/profiles/jobs/{jobId}/file"   # 200 con el CSV adjunto
```

Códigos de la descarga, que distinguen casos que un 404 mezclaría:

| Código | Significado |
|---|---|
| `409` | el trabajo existe pero aún no ha terminado: reintenta más tarde |
| `410` | terminó, pero el fichero ya se purgó: hay que relanzar la exportación |
| `404` | no existe ese trabajo, o es de un tipo que no produce fichero |
| `429` | no hay hueco de concurrencia; incluye `Retry-After` |

Detalle completo en [`README_ASYNC_JOBS.md`](README_ASYNC_JOBS.md).

---

## 9. Errores

Todos con el mismo formato (`application/problem+json`, RFC 9457):

```json
{
  "type": "https://api.mto-configuration/errors/val-000",
  "title": "Error de validación",
  "status": 400,
  "detail": "La petición tiene 2 errores de validación",
  "instance": "/api/v1/configuration/profiles",
  "code": "VAL-000",
  "timestamp": "2026-08-31T10:15:00Z",
  "traceId": "a4f7fe56a69a450a",
  "retryable": false,
  "errors": [
    { "field": "profileId",               "code": "VAL-001", "message": "El campo es obligatorio" },
    { "field": "cantilevers[1].cwHeight", "code": "VAL-004", "message": "Debe ser mayor que cero" }
  ]
}
```

Cada entrada de `errors` trae `field`, `code` y `message`. El `code` es estable y viene del
catálogo: úsalo para reaccionar en el cliente en lugar de parsear el texto, que puede cambiar.

`retryable` dice si reintentar la misma petición puede funcionar. En un `409` o un `429` sí; en un
error de validación no, hasta que cambies el cuerpo.

| HTTP | `code` | Cuándo |
|---|---|---|
| `400` | `VAL-000` | validación fallida, o cuerpo mal formado |
| `401` / `403` | — | sin token, o sin el rol necesario |
| `404` | `NOT-001` | el recurso no existe |
| `409` | `CON-001` | conflicto de concurrencia (`versionNumber` desactualizado) |
| `429` | — | sin hueco para el trabajo |
| `500` | `TEC-999` | error inesperado; el `traceId` es lo que hay que dar en la incidencia |

La ruta del campo en `errors[].field` es **navegable desde la raíz del cuerpo**: `cantilevers[1].cwHeight`
en un objeto, `[0].name` en un endpoint de lote.

Para conflictos de concurrencia, devuelve el `versionNumber` que leíste. Si otro ha guardado
mientras tanto, recibes un `409` en vez de pisar su cambio.
