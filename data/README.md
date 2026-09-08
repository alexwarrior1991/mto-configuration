# `data/` — Maestros generados desde los workbooks

Esta carpeta contiene los workbooks de origen y las dos herramientas que los
consolidan: una produce el **catálogo de LOVs** y otra los **datos de infraestructura**
(paquetes, estaciones, vías, perfiles y ménsulas).

```
data/
├── workbook/                   # Workbooks de Execution Package (fuente, tal cual los entrega ingeniería)
├── tools/
│   ├── workbook_common.py      # Primitivas compartidas por los dos generadores
│   ├── build_lov_master.py     # Generador del catálogo de LOVs
│   ├── build_profile_master.py # Generador de los datos de infraestructura
│   ├── aliases.yml             # Tablas de mapeo — se amplía aquí, no en los scripts
│   ├── topology.yml            # Lo que solo puede decir una persona (ver más abajo)
│   └── tests/                  # Pruebas de las reglas de mapeo (unittest, sin dependencias)
├── lov-master.xlsx             # Catálogo consolidado (generado)
└── profile-master.xlsx         # Infraestructura consolidada (generado)
```

Los dos maestros son lo único que lee la aplicación: los workbooks no los abre nunca.
El de LOVs va **primero**, porque los perfiles referencian sus códigos.

El resto de este documento describe el catálogo de LOVs. El maestro de perfiles tiene
su propia sección al final.

## Por qué hay dos pasos

Los workbooks de origen son heterogéneos: cabeceras en español y en inglés,
categorías que cambian de nombre entre ficheros, notas escritas en la columna
equivocada, celdas con varios valores separados por salto de línea, y hojas con
formato aplicado al millón de filas (`EP9A` declara 2.127.071 filas físicas).

Meter todo eso en el código de la aplicación significaría arrastrar un mapa de
excepciones por fichero y un riesgo de memoria real. En su lugar:

1. **El generador** consolida los workbooks y produce `lov-master.xlsx`, limpio y
   estable.
2. **La aplicación** solo sabe leer ese maestro: una hoja de 12 columnas.

## Regenerar el maestro

```bash
pip install openpyxl pyyaml
python3 data/tools/build_lov_master.py                 # lee data/workbook/
python3 data/tools/build_lov_master.py otra/carpeta -o salida.xlsx
```

Tarda alrededor de un minuto con los 11 workbooks actuales.

**El script termina con código de salida distinto de cero si encuentra algo que no
sabe mapear.** No es un fallo del script: significa que el catálogo saldría
incompleto. Hay que mirar la hoja `NO_RECONOCIDO`, añadir el alias que falte en
`tools/aliases.yml` y volver a ejecutar.

Esa salvaguarda no es teórica: al incorporar los workbooks `EP9B`, `EP14A`, `EP14B`
y `EP15` destapó 13 categorías nuevas (~107 filas), entre ellas los 57 códigos de
`POSTES SIMPLES` / `POSTES DOBLES` / `POSTES ESPECIALES`, que se habrían perdido sin
que nadie se enterase.

## De dónde sale cada dato

Las tres fuentes son complementarias y ninguna basta por sí sola:

| Fuente | Hoja | Qué aporta |
|---|---|---|
| **BOQ** | `Recuento Conjuntos` | Volumen, número de plano y descripción |
| **Legend** | `Legend` (solo en 5 de los 11) | Semántica, y las entidades que el BOQ ignora |
| **Track** | `HR Track *` (176 hojas) | Uso real: cuántas veces se usa cada código |

El caso que lo justifica: el BOQ no contiene **ni una sola** fila de `Sectioning`,
que es una de las 8 relaciones de `Profile`. Los códigos que la app necesita
(`S/A` con 1713 usos, `A/S` con 1222) solo aparecen en el `Legend`.

## La hoja `LOVS`

Una fila por valor de LOV. Columnas:

| Columna | Significado |
|---|---|
| `ENTIDAD` | Entidad LOV destino (`Foundation`, `PoleType`, `Sectioning`…) |
| `CODIGO` | Código tal cual aparece. Máximo 40 caracteres |
| `DESCRIPCION_EN` / `DESCRIPCION_ES` | Descripción en cada idioma, si existe |
| `TIPO` | Solo `Foundation`, `Portal` y `AnchorageFoundation`. Ver hoja `TIPOS` |
| `N_PLANO` | Número de plano, vacío si el origen no traía uno numérico |
| `ENABLED` | **`SI`/`NO`. Es lo único que decide si la fila se carga en BBDD** |
| `ORIGEN` | `BOQ`, `LEGEND`, `BOQ+LEGEND` o `TRACK` |
| `CATEGORIAS_BOQ` | Categoría de origen en `Recuento Conjuntos` |
| `EPS` | Execution Packages en los que aparece |
| `USOS_EN_TRACKS` | Veces que se usa en hojas `HR Track` |
| `REVISAR` | `SI` cuando hace falta una decisión humana (fila resaltada) |

### Qué hay que revisar

Las filas con `ORIGEN=TRACK` salen con **`ENABLED=NO`**: son códigos que se usan en
las hojas de trazado pero que ningún catálogo curado recoge. Para aceptarlas, añade
el código a `track_accepted` en `tools/aliases.yml` y vuelve a generar — **no basta
con poner `ENABLED=SI` a mano**, porque la siguiente regeneración se lo lleva por
delante. Ver «Las dos tablas que deciden por ti» más abajo.

`USOS_EN_TRACKS` es la columna que permite decidir de un vistazo:

- `RW1` con 5262 usos es evidentemente un código real que faltaba en el BOQ.
- `ENT-1` con 1 uso es una errata de `EMT-1`.

También salen marcadas las filas cuyo `TIPO` no se ha podido deducir del código.

## Hojas auxiliares

| Hoja | Contenido |
|---|---|
| `LEEME` | La explicación de las columnas, dentro del propio Excel |
| `TIPOS` | Catálogos `FoundationType` / `PortalType` / `AnchorageFoundationType`. Se cargan **antes** que las LOVs que los referencian |
| `USO_TRACKS` | Qué columna de las hojas Track alimenta cada entidad |
| `DESCARTADOS` | Todo lo rechazado, con el motivo |
| `NO_RECONOCIDO` | Lo que el generador no supo mapear. **Si tiene filas, el catálogo está incompleto** |

## Las dos tablas que deciden por ti

Además de las tablas de alias de cabeceras y categorías, `aliases.yml` tiene dos secciones que
resuelven problemas distintos y conviene no confundir.

### `code_canonical` — varias grafías del mismo código

Una tabla por entidad, de la grafía que aparece en el origen a la forma canónica. Se aplica **antes
de agrupar**, así que los usos se suman en una única fila en lugar de repartirse.

Las diferencias de solo mayúsculas **no hacen falta aquí**: la clave del catálogo ya es
`(entidad, CÓDIGO en mayúsculas)`, de modo que `DISC/IO` y `Disc/IO` colapsan solas. Esto es para lo
que además cambia de caracteres.

El caso que lo justifica: la columna `Sectioning Feeding` escribe el mismo *feeder wire* como
`FW-25` (99 usos), `FW25` (14), `FW+25` (11) y `F-25` (2). Repartido así, ninguna de las tres
últimas llega al umbral en el que alguien se fija; junto, es un código de 126 usos que estaba sin
catalogar. Lo mismo con `LoadB/NZ` y `LoadB/NS`, que son el mismo equipo: la leyenda de `EP9B` llama
«N.Z. Disconnector» justo al código `Disc/NS`.

### `track_accepted` — códigos de trazado ya revisados

Un código que solo aparece en las hojas `HR Track` sale con `ENABLED=NO`: está en uso real pero
ningún catálogo curado lo recoge, y aceptarlo es una decisión humana.

**Esa decisión se declara aquí, no poniendo `ENABLED=SI` en el Excel generado.** El maestro se
regenera y se lleva por delante cualquier edición manual; en `aliases.yml` queda versionada y
revisable en el PR.

Lo que no está aceptado se queda con `ENABLED=NO` y `REVISAR=SI`, que es exactamente el estado
«pendiente de decidir»: a la vista, sin romper la generación. Hoy están ahí los siete códigos de
`DisconnectorFunction` con sufijo `-pr` / `-pp` (`Disc/IO-pr`, `Disc-pr`, …), a la espera de saber
qué significa el sufijo.

> Nota de coherencia pendiente: `LoadB/IO-pr` y `LoadB/PP-pr` **sí** están habilitados, porque vienen
> del BOQ y no solo de las hojas Track. Es la misma familia con dos tratamientos distintos; se
> resolverá cuando se decida qué es el sufijo.

## Probar el generador

```bash
python3 -m unittest discover -s data/tools/tests -v
```

Sin dependencias: usa `unittest` de la biblioteca estándar. Las pruebas de mecanismo
(canonicalización, aceptación, descartes, decisión de `ENABLED`) corren contra un `aliases.yml`
construido en el propio test, así que ampliar el catálogo no las rompe. La última clase contrasta el
`lov-master.xlsx` ya generado y se salta sola si no está o si falta `openpyxl`.

CI las ejecuta en su propio paso, antes de `./mvnw verify`: el generador queda fuera de Maven y sin
ese paso no las correría nadie.

## Qué queda fuera a propósito

- **Catálogo de materiales** (`WIRES AND CONDUCTORS`, `PROTECTIONS`, `OCR`,
  `RAIL CAT.`…): son conjuntos de material, más cercanos a `mto-stock` que a un LOV
  de configuración. Están listados en `aliases.yml` bajo `material_categories` para
  que no ensucien `NO_RECONOCIDO`.
- **`ProfileStatus`**: ya se cubre con el enum `enums/infrastructure/ProfileStatusValue`.
- **`Soil Found`, `Terrain Geometry`, `Survey`**: son columnas reales de las hojas
  Track pero hoy no tienen entidad LOV en el proyecto.

---

# El maestro de perfiles

`profile-master.xlsx` lleva los datos de infraestructura: paquetes de ejecución,
estaciones, vías, perfiles y ménsulas con sus brazos. Lo genera
`tools/build_profile_master.py` y es lo que importa la aplicación.

## Lo que hace falta declarar a mano: `topology.yml`

Hay conocimiento que **no está en los ficheros** y que solo puede aportar una persona:

- **Los metadatos de cada paquete**: nombre, fechas, longitud y empresa. Los workbooks
  no los traen; la hoja `Revision Data` solo tiene el histórico de revisiones.
- **Qué vía es cada hoja.** La celda A1 da un título legible que expande las
  abreviaturas del nombre de hoja (`HR Track 1 HER` → `TRACK 1 HERZLIYA`, `RIS` →
  `RISHPON`, `TSA` → `TLV SAVIDOR`), pero **miente**: `EP14B / HR Track 6` dice
  «TRACK 5» y `EP14B / HR Track 41` dice «TRACK 4», que además chocan con las hojas
  que sí se llaman así. Sirve de semilla, no de fuente de verdad.
- **Si una vía cuelga de una estación o del paquete.** No se deduce de ninguna parte, y
  `TRACK.STATION_ID` es anulable a propósito: `station: null` es una respuesta válida.
- **Dónde se parte una hoja que lleva dos tramos.** `EP9A / HR Track 1` y `HR Track 2`
  tienen el KP reiniciado a mitad y 47 y 46 códigos de perfil repetidos. Sin el corte,
  los dos tramos caerían en la misma vía y chocarían por `(vía, profileId)`.

```yaml
execution_packages:
  EP9A:
    file: EP9A.xlsm
    name: "EP-09A"
    initial_package: false
    length: 21000
    start_date: 2017-08-13
    end_date: 2020-12-31
    company_identification_number: "B12345678"
    stations: [TEL AVIV SOUTH, LOD]
    tracks:
      - sheet: "HR Track 1TLV S."
        name: "TRACK 1 TEL AVIV SOUTH"
        station: TEL AVIV SOUTH
      - sheet: "HR Track 1"              # esta hoja lleva DOS tramos
        name: "TRACK 1 (5+421 a 8+948)"
        station: null                    # cuelga del paquete, no de una estación
        rows: [5, 135]
      - sheet: "HR Track 1"
        name: "TRACK 1 (0+270 a 10+758)"
        station: null
        rows: [137, 785]
      - sheet: "HR Track X AAA"
        skip: "hoja vacía"
```

**Toda hoja `HR Track` tiene que estar declarada**, con `name` o con `skip`. Una hoja
sin declarar hace terminar el generador con código distinto de cero.

**Una vía tampoco puede colgar de una estación que su paquete no declara.** Si `station:`
nombra algo que no está en la lista `stations:` de ese EP, el generador lo saca en
`NO_RECONOCIDO` y termina con código distinto de cero. Sin esa comprobación el error
aparecía mucho más tarde: el importador rechazaba la vía y se quedaba sin cargar, con el
trabajo ya a medias. La comparación ignora mayúsculas y espacios sobrantes; `station: null`
no se comprueba, porque es una respuesta válida.

**`UNIQUE SOLUTION` es un valor de cimentación, no un marcador.** Significa que esa
cimentación necesita una solución **específica, que viene aparte**, porque ninguna
convencional sirve. Estuvo en `code_rejections` por una suposición equivocada, y eso dejaba
468 perfiles sin `foundation`. Las grafías del origen (`Unique Solution`, `U.S.`,
`U.SOLUTION`, `U. SOLUTION`…) se canonicalizan a `UNIQUE SOLUTION`, y su `FoundationType` es
**`USX`**, un tipo propio: precisamente por no encajar en ninguna de las seis familias
estructurales (`CX`, `CXR_L`, `PLX`, `WX`, `WDX`, `WTX`). En la misma pasada se funden dos
erratas de `T-SIGN FOUND.` (`T-SING FOUND.`, `T-SIGN FOUND,`), 27 filas más.

`UNIQUE SOLUTION` vale igual en las tres columnas donde aparece: `FOUNDATION` (tipo `USX`),
`ANCHORAGE_FOUNDATION` (tipo `AnUS`, porque el anclaje también puede necesitar solución a
medida) y `POLE_TYPE`, que no lleva tipo.

**La familia `M<n> Ø<d>`** (`M3 Ø36`, `M5 Ø42`…) son cimentaciones de poste con pernos
métricos. Tipo propio **`MX`**, por la misma razón que `USX`: ninguna de las familias
estructurales describe un anclaje así. Ocho códigos, ~150 filas.

**`NON DEFINED`, `N.D.` y `U.S. N.D.` no son códigos: son «aquí no hay dato».** Siguen fuera
del catálogo, y además el generador de perfiles los convierte en **hueco**. Antes salían en
`NO_RECONOCIDO` como si faltara una lista de valores por declarar, que es justo lo
contrario de lo que pasa. La lista es la misma, `code_rejections`, y ahora la usan los dos
generadores con el mismo significado: esto no es un código.

**`SECTIONING` es la única columna multivalor.** Un perfil puede llevar varios
seccionamientos a la vez —`A/S P50(CS)` son dos, corriente en estaciones— y desde `V14` el
modelo es N:M. El generador **primero prueba la celda entera** como código y solo la parte si
**todas** sus partes son códigos válidos; si no, la deja intacta y la saca en
`NO_RECONOCIDO`. Sin esa condición, `A/S Diag` —que es `A/S-Diag` escrito con espacio— se
convertiría en `A/S` más un `Diag` inventado. En las demás columnas, dos códigos en una celda
siguen siendo una anomalía y se reportan.

Dos erratas mecánicas se corrigen antes de nada, en los dos generadores: el **espacio antes
de un paréntesis** (`P50 (CS)` es `P50(CS)`, 50 códigos de cuatro catálogos) y el **código
repetido en la celda** (`S/A S/A` es `S/A`). La primera importa el doble ahora: partiendo la
cadena cruda, ese espacio rompía `P50(CS)` por la mitad.

**Una celda con varios valores no es un código.** El catálogo se cosecha leyendo cada celda
de las hojas Track como un código, así que `S/A A/S-Diag` entraba como si fuera uno más —y
al habilitar en bloque los `ENABLED=NO` se legitimaron 123 de ellos. Ahora se descartan: la
regla no es «tiene un espacio» (`T-SIGN FOUND.`, `UNIQUE SOLUTION` y `M3 Ø36` son legítimos),
sino que **todas** sus partes sean a su vez códigos de la misma entidad.

**Todo código de lista de valores tiene que existir HABILITADO en el catálogo.** El
generador cruza cada columna de código (`SECTIONING`, `ANCHORAGE`, `ANCHORAGE_FOUNDATION`,
`FOUNDATION`, `POLE_TYPE`, `PORTAL`, `RETURN_SUPPORT`, `SECTIONING_FEEDING`,
`CANTILEVER_TYPE`, `STEADY_ARM_TYPE`) contra `lov-master.xlsx` y saca a `NO_RECONOCIDO` los
que no resuelven, terminando con código distinto de cero. Antes de aplicar el cruce
canonicaliza con la misma tabla `code_canonical` que usa el generador de LOV: tenerla en un
solo sitio y aplicarla solo en uno era un fallo que dejaba `FW25` sin convertir en `FW-25`.

Un código con `ENABLED=NO` cuenta como ausente: no llega a la base de datos, así que
referenciarlo desde un perfil está igual de roto que inventárselo.

Por qué importa tanto: `MasterDataService` resuelve un código desconocido a `null` **sin
quejarse**, y `ProfileValidator` no consulta ningún catálogo. Sin esta comprobación el perfil
se guardaba con la clave ajena vacía y el informe lo contaba como cargado. Medido sobre el
maestro real eran **5.814 asignaciones** que se habrían perdido en silencio. El importador
lleva además su propio candado (`InfrastructureUpsertService.requireResolvableCodes`), que
rechaza la fila nombrando todos los códigos que fallan de una vez.

**El `company_identification_number` se busca, no se da de alta.** Es el NIF de la
empresa, no su id: el importador lo traduce contra `business_entity`, y esa tabla viene de
un maestro externo — este repositorio no tiene migración que la siembre, ni servicio, ni
endpoint que la escriba. Un NIF que no esté ahí **no se puede resolver**, así que
comprueba antes que existe:

```sql
SELECT id, identification_number, name FROM business_entity;
```

El importador distingue los dos fallos y los dice con ese nombre: si el campo está vacío,
que hay que rellenarlo aquí; si el NIF no corresponde a ninguna empresa, **cuál es el NIF**
que no encontró. Antes devolvía `null` en silencio y el paquete moría más abajo con
«companyId es un campo obligatorio», que nombra un campo inexistente en este fichero y no
distingue el hueco del NIF mal escrito — con once paquetes apuntando a la misma empresa,
un NIF mal tecleado tumbaba los once sin decir cuál era.

Para arrancar:

```bash
python3 data/tools/build_profile_master.py --seed-topology
```

Recorre las 177 hojas y escribe un `topology.yml` inicial con el nombre tomado de A1 y
todo marcado `# REVISAR`. Se corrige a mano y queda versionado, diffable en el PR.

## Generar

```bash
pip install openpyxl pyyaml
python3 data/tools/build_profile_master.py
```

Como el de LOVs, **termina con código distinto de cero si encuentra algo que no sabe
mapear**: una hoja sin declarar o una cabecera desconocida van a `NO_RECONOCIDO`.

## Las hojas

| Hoja | Contenido |
|---|---|
| `LEEME` | La explicación de las columnas, dentro del propio Excel |
| `EPS` / `STATIONS` / `TRACKS` | Lo declarado en `topology.yml`, ya resuelto |
| `PROFILES` | Una fila por perfil: identificador, KP, las 8 LOV y los 4 campos técnicos |
| `CANTILEVERS` | Una fila por ménsula, con su `SLOT` (1..3) y el brazo ya partido en tipo y longitud |
| `DISCONNECTORS` / `SECTION_INSULATORS` | Cabeceras y **ninguna fila**: la costura para cuando lleguen esos datos |
| `NO_MAPEADO` | Columnas reales del origen que hoy no tienen campo en el dominio |
| `DESCARTADOS` | Todo lo rechazado, con motivo y celda de origen |
| `NO_RECONOCIDO` | Lo que no se supo mapear. **Si tiene filas, el maestro está incompleto** |

`ENABLED` (`SI`/`NO`) decide si la fila se carga; `REVISAR` resalta lo que necesita ojo
humano.

## Las tres trampas del origen

1. **Las columnas se resuelven por nombre, nunca por índice.** Las 177 hojas escriben
   las mismas ~36 columnas lógicas de 66 formas distintas, y `EP6 / HR Track 1 HER` no
   tiene columna `Sectionning`: todo lo posterior queda desplazado una posición. Leer
   por índice corrompería esa hoja **en silencio**.
2. **El vano vive en la fila intermedia.** Las filas alternan perfil / fila intermedia,
   y el `Span` está en la segunda: 11.475 de los 11.490 valores. Es el vano **hasta el
   perfil siguiente**, y por eso `Profile.span` significa eso.
3. **`'0'` es el marcador de hueco**, en todas las columnas, incluida la del
   identificador del perfil. Tratarlo como un valor metería ~320 perfiles fantasma y
   miles de medidas de cero inventadas.

## El brazo viene con la longitud dentro

La columna `Arm Type` trae tipo y longitud juntos (`PH-1150`, `BTC-1651`) pero el
catálogo `SteadyArmType` solo tiene el tipo base. La regla es «sufijo numérico =
longitud», y necesita la lista `steady_arm_types` de `aliases.yml` porque `PH-C` y
`PH-Q` son tipos que **también** llevan guion.

De las 14.592 ménsulas: 4.085 traen tipo y longitud, 5.691 solo el tipo y 4.816 no
traen brazo. **Que falte la longitud no es un error**: no se conoce, y por eso
`steady_arm.length` es opcional.

## Qué se importa y qué no

De las ~36 columnas del origen, 16 tienen campo en el dominio. Las demás —`Survey`,
`Other KP`, `Theorical/Current Cant`, `Track Layout`, `Anchorage KP`, `Track KP`,
`Supports`, `Soil Found`, `Terrain Geometry`, `Depth Pole/Anchor F.` y `Approved by`—
se conservan en `NO_MAPEADO` con su EP, vía, perfil y fila de origen. No se importan,
pero tampoco se pierden: ampliar el modelo más adelante es un cambio de esquema y un
mapper, no volver a analizar 60 MB de Excel.

## Probar

```bash
python3 -m unittest discover -s data/tools/tests -v
```

Las pruebas de mecanismo construyen las hojas en memoria, sin abrir ningún workbook.
Las dos últimas clases contrastan los maestros ya generados y se saltan solas si no
están.

---

## El tamaño de `workbook/`

Son ~60 MB de binarios en el árbol y **29,1 de los 32 MB del `.git`**, todos en un
único commit (`af82953`). **Decisión tomada: no se toca de momento.** Esta sección
existe para que se pueda reconsiderar con datos en lugar de por intuición.

El malentendido a evitar: **activar Git LFS «solo hacia adelante» no recupera nada**.
Los blobs ya están en el historial y LFS solo intercepta lo que se añade después.
Peor aún, no es ni siquiera un estado estable: en cuanto se añade la regla
`data/workbook/** filter=lfs`, git marca los 11 workbooks como modificados de forma
permanente, así que hay que convertirlos a punteros. Y al hacerlo el clon **empeora**,
porque los blobs viejos siguen ahí y encima se descargan los objetos LFS.

| Escenario | Tamaño de un clon nuevo |
|---|---|
| Hoy, sin tocar nada | 32 MB |
| Sin LFS, tras 1 re-subida de los 11 workbooks | ~61 MB |
| Sin LFS, tras 2 re-subidas | ~90 MB |
| LFS convertido, sin reescribir historia | ~92 MB, y ya no crece |
| LFS + reescritura de historia | ~63 MB, y ya no crece |

Conclusión: LFS a medias solo compensa a partir de la **tercera** re-subida, y aun así
queda peor que hacerlo bien. **Si llega ese momento, la opción correcta es la completa**:

```bash
git lfs install
git lfs migrate import --include="data/workbook/**" --everything
git push --force-with-lease origin master
```

Coste de esa vía, que hay que coordinar con el equipo: cambia el SHA de los commits
afectados, obliga a todo el mundo a re-clonar, y rompe la base de cualquier PR abierto.
Cuanto antes se haga, más barato sale: hoy solo hay un commit implicado.

Alternativa a considerar en ese punto: los workbooks son **entrada** del generador y la
aplicación no los abre nunca —solo necesita `lov-master.xlsx`, que son 124 KB—, así que
podrían vivir fuera del repo con su ubicación documentada aquí.
