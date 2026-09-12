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
«pendiente de decidir»: a la vista, sin romper la generación. Hoy quedan ahí 14 códigos repartidos
entre `AnchorageFoundation`, `Foundation`, `Portal`, `ReturnSupport` y `SupportType`, y ninguno es
una familia: son casos de uno en uno. **De esos 14, sólo uno le quita el valor a un perfil**
(`MP-ISusp` en `Portal`); los otros trece están en filas que no son perfil —el cosechador recorre
la columna entera, el generador de perfiles sólo lee filas de perfil—, así que decidirlos es
limpieza de catálogo, no dato perdido.

**Aceptar no es la única salida, ni siempre la correcta.** Un código que el origen escribe en la
columna de otro catálogo no se acepta, se corrige en el workbook: dar de alta `B7` en `SupportType`
—cuando `B7` es un semipórtico, código de `Portal` con 140 usos en su propia columna— crearía un
soporte que no existe y lo dejaría ahí para siempre.

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

**Una vía que atraviesa varias estaciones las declara todas, y sigue siendo UNA vía.** No se
parte: `TRACK 1` de EP4 es larga y pasa por ZIC, por BIN y por HAD, mientras que `TRACK 5 BIN`
solo está en BIN. Desde `V17` la relación es N:M, así que se declaran en plural:

```yaml
      - sheet: "HR Track 1"
        name: "TRACK 1"
        stations: [ZIC, BIN, HAD]      # la vía larga: pasa por las tres
      - sheet: "HR Track 5 BIN"
        name: "TRACK 5 BIN"
        station: BIN                   # el singular sigue valiendo para una sola
      - sheet: "HR Track 1 RIS-HER"
        name: "TRACK 1 RISHPON-HERZLIYA"
        station: null                  # entre estaciones: cuelga del paquete
```

`station:` (una) y `stations:` (varias) valen las dos y pueden convivir en el fichero: las vías
que ya estaban rellenas en singular no hay que reescribirlas.

Lo que **no** se declara es dónde empieza cada estación dentro de la vía. Se valoró cortarla por
rangos de fila y se descartó: parte la vía en tres, que es contar una vía como tres. Y por KP no
se puede, que sería lo natural: de las 176 vías medidas, **31 traen el KP no monótono** y
`EP9B / TRACK 1 LOD_S` llega a un KP de 1.110.546 (un dedazo por 110.546), así que un límite
declarado en KP metería perfiles en la estación equivocada sin avisar.

**Una hoja con dos tramos concatenados YA NO se parte en dos vías.** `HR Track 1` y `HR Track 2`
de EP9A traen dos tramos seguidos con la kilometración reiniciada, y hasta `V18` había que
cortarlos con `rows` porque cada tramo se numeró por su cuenta y el identificador de perfil se
repetía —47 veces en la vía 1, 46 en la vía 2—. Son **una sola vía**, y ahora se declaran como
tal:

```yaml
      - sheet: "HR Track 1"
        name: "TRACK 1"
        stations: [TZG, BEN, TFAM]
```

Lo que lo hace posible: la clave natural del perfil pasa a ser `(vía, profileId, kp)`. Medido
sobre el origen, `5-1.01` aparece dos veces pero en KP distintos (5421 y 5017), así que el KP es
lo que distingue un mástil del otro. Sigue chocando —y sale en `DESCARTADOS`— la fila que repite
identificador **y** KP: en EP9A hay una, `8-1.12` en el KP 8447, con distinto tipo de poste en
cada aparición, que es un error del origen.

Y el orden de los perfiles pasa a una columna propia, `ORDEN` (1..N en el orden de la hoja),
porque ordenar por KP mezclaría los dos tramos en vez de ponerlos uno detrás del otro.

**Los tramos de una hoja partida** —`rows`, que sigue siendo solo para las hojas que llevan
**dos tramos concatenados**, como `EP9A / HR Track 1`— **tienen que cubrirla entera, y una sola
vez.** Si las filas
con perfil se quedan fuera de todo tramo, o caen en dos a la vez, el generador las saca en
`NO_RECONOCIDO` y termina con código distinto de cero. Sin esa comprobación, declarar
`[4, 120]` y `[200, 400]` en una hoja que llega a la 400 se tragaba las de en medio **en
silencio**: el maestro salía con menos perfiles y cuadraba consigo mismo, que es la peor
forma de perder datos, porque no hay nada que no cuadre.

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

**`SECTIONING`, `ANCHORAGE` y `SECTIONING_FEEDING` son las columnas multivalor.** Un perfil puede llevar varios
seccionamientos a la vez —`A/S P50(CS)` son dos, corriente en estaciones— y varios anclajes
—`FP+AnMC CP+AnMC` es uno con regulación de tensión y otro sin ella—. Y varios aparatos de seccionamiento —`Disc SECT-I` es un disconnector más un aislador de
sección—. El modelo es N:M en las tres desde `V14`, `V15` y `V16`. El generador **primero prueba la celda entera** como código y solo la parte si
**todas** sus partes son códigos válidos; si no, la vacía y la saca en
`NO_RECONOCIDO`. Sin esa condición, `A/S Diag` —que es `A/S-Diag` escrito con espacio— se
convertiría en `A/S` más un `Diag` inventado. En las demás columnas, dos códigos en una celda
siguen siendo una anomalía y se reportan.

**La partición la decide la leyenda de los workbooks, no el espacio.** El catálogo de
`Sectioning` se cosechó leyendo cada celda de las hojas Track como si fuera un código, así
que llegó a tener **45 códigos que en realidad eran varios juntos** (`P30(CS) A/S Diag`,
`S/A A/S Diag`…). La hoja `Legend` dice cuáles son los de verdad —diez: `A/S`, `A/S-Diag`,
`S/A`, `A`, `MP`, `MP/Tunnel`, `AnMP`, `AnMP/Tunnel`, `P50`, `P90`— más la familia de
agujas `P<valor>` y `P<valor>(CS)`.

`workbook_common.code_tokens` aplica esas reglas y **la usan los dos generadores**, que es
lo que impide que el catálogo y las referencias de los perfiles discrepen:

| Celda | Se parte en | Por qué |
|---|---|---|
| `A/S Diag` | `A/S-Diag` | *Diagonal Anchorage* es `A/S-Diag`; un `Diag` suelto no existe |
| `A/S Diag 2` | `A/S-Diag` | el número cuenta diagonales del poste, no es código |
| `A/S - S/A` | `A/S` · `S/A` | el guion suelto separa: *Overlap Anchorage* y *Overlap Semi-Axis* |
| `P50 (CS) S/A` | `P50(CS)` · `S/A` | el espacio antes del paréntesis es errata |
| `P50(CS)S/A` | `P50(CS)` · `S/A` | dos códigos pegados |
| `AnMP(T1)` | `AnMP` | el `(T1)` dice en qué vía está, y eso ya lo sabe la vía |
| `T-SIGN FOUND.` | `T-SIGN FOUND.` | ninguna de sus partes es código: **no** se parte |

La condición no cambia: se acepta la partición **solo si todas las partes son códigos** de
la misma entidad. `AnMP T A/S-Diag` lleva una `T` que nadie ha identificado, así que la
celda entera sale en `NO_RECONOCIDO` en vez de cargarse a medias.

**Códigos de `Anchorage` en la columna `SECTIONNING`.** `AnRW`, `AnFW` e `IO` los declara
la leyenda en `ANCHORAGE`, no en `SECTIONNING`. Están fuera del catálogo de seccionamiento
(`code_reassignment` con `to: null`), así que la celda que los trae sale nombrada con su
hoja y su fila: es un error del workbook, y ahí es donde hay que corregirlo.

### El tipo de ménsula cuando el origen no lo escribe

`cantileverType` es una relación **obligatoria**: es lo único que `CantileverValidator`
sigue exigiendo después de relajar los seis numéricos. Y hay **348 huecos** M1/M2/M3 en
los que la celda del tipo está vacía pero otras columnas del mismo hueco traen números.
Sin un tipo al que colgarlas, esas ménsulas no se pierden a medias: se pierden **enteras**.

No todas son ménsulas, y ahí está la distinción que importa:

| Lo que trae el hueco | Qué es | Qué se hace |
|---|---|---|
| `stagger`, `catenaryHeight`, `cwElevation`, `windDeflection` o el brazo | una ménsula descrita a la que le falta el **nombre** | entra, con tipo supuesto y `REVISAR=SI` |
| solo `cwHeight` y/o `armAngle` | `cwHeight` es un valor de proyecto repetido a lo largo del tramo y `armAngle` es calculado: aparecen igual **donde no hay ménsula** | se descarta el hueco entero |

Son **217 y 131**. La lista de campos que sirven de prueba está en
`cantilever_type_fallback.evidence`, en `aliases.yml`, y que `cwHeight` y `armAngle`
queden fuera **no es un olvido**: son justamente los dos que aparecen sin ménsula.

El tipo supuesto sale de la columna `Supports` de la misma fila cuando ésta lo nombra:

- **`Supports = OCR SUPPORT`** → tipo **`OCR`** (*Overhead Conductor Rail*, catenaria
  rígida). Son las 58 de `EP6 / HR Track 1 TSA-THA` y `HR Track 2 TSA-THA`. **No hace
  falta un código nuevo**: `OCR` ya está en el catálogo, con 23 usos en `EP9B`, que sí lo
  escribe en la columna del tipo. Darle otro nombre partiría en dos una sola cosa.
- **El resto** → **`UNKNOWN`**, 159, casi todas de `EP14A`.

`UNKNOWN` es el único código que **no sale de ningún workbook**: lo declara
`synthetic_codes` en `aliases.yml` y el generador lo emite con **`ORIGEN=MTO`**, que lo
distingue a simple vista de `BOQ`, `LEGEND` y `TRACK`. Es deliberado que se vea: un código
que el origen no nombra tiene que poder auditarse. Las ménsulas que lo llevan salen todas
con `REVISAR=SI`.

Las 217 quedan anotadas en `DESCARTADOS` con el motivo `tipo de mensula supuesto: …`, su
hoja, su fila y el tipo que se les puso. Rellenar el tipo en el workbook las saca de esa
lista sin tocar nada aquí.

### El brazo: la longitud no es parte del tipo

La columna `Arm Type` escribe el tipo y su longitud juntos, `PHQ-1150`. El catálogo
`SteadyArmType` solo tiene los **ocho tipos base** —`BC`, `BCE`, `BTC`, `PH`, `PH-C`,
`PH-Q`, `PHC`, `PHQ`— y la longitud tiene su propia columna, `steady_arm.length`.

`workbook_common.split_steady_arm` aplica la regla «sufijo numérico = longitud», que
necesita la lista de tipos porque `PH-C` y `PH-Q` llevan guion **sin** que sea una
longitud. También limpia `PH- 1450` (espacio suelto), `PH950` (sin guion), `BTC_1651`
(guion bajo) y `PHC-1500E` (letra al final, que se ignora diciéndolo).

La usan **los dos generadores**, por lo mismo que `code_tokens`: mientras solo la aplicaba
el maestro de perfiles, el catálogo se quedaba con **62 filas que no son tipos de brazo**
(`PHQ-1150` con 1.160 usos, `PH-1150` con 720…). Nadie las referenciaba —el maestro ya
escribe el tipo base y manda el número a su columna— pero estaban marcadas
`REVISAR=SI`, es decir «pendiente de decidir»: habilitar una habría guardado la misma
medida **dos veces**, en `steady_arm.length` y dentro del código.

Con esto `SteadyArmType` pasa de **71 códigos a 9**, todos habilitados y ninguno pendiente.

Los dos valores que el catálogo no reconocía se resolvieron **de formas distintas a
propósito**, porque no eran lo mismo:

- **`BS` es un tipo real** y se da de alta. Solo aparece en una hoja (`EP9B / HR Track 2
  LOD_S`) y en ningún BOQ, así que va en `track_accepted`: los códigos que solo viven en
  las hojas de trazado necesitan que una persona los acepte.
- **`BHC` era `PHC` mal tecleado** y se corrige con un alias en `steady_arm_type_aliases`,
  **no** con un código nuevo: dar de alta una errata la convierte en un tipo para siempre.

El alias va en su propia sección y no en `code_canonical` porque hay que aplicarlo **antes**
de separar el tipo de la longitud: `BHC-1150` no llega entero a ningún sitio donde una tabla
de grafías pudiera verlo, y sin esto se perdía el brazo **y** su longitud, que sí era buena.

### `ANCHORAGE`: lo que la leyenda escribe al lado del código

El bloque `ANCHORAGE` de la leyenda declara **doce anclajes** —`CP+AnMC`, `FP+AnMC`,
`CP/Tunnel`, `FP/Tunnel`, `CP/TX-P/1100`, `CP/TX-T`, `CP/TX-W/1100`, `AnFW`, `AnRW`,
`AnFW/Tunnel`, `AnRW/Tunnel` e `IO`— **y algo que no es un anclaje**: una
`SEMI TENSION LENGTH xxx m.` que el origen teclea pegada al código. El dominio no tiene
dónde guardar esa longitud, así que se conserva el anclaje y el número se descarta.

Eso, más la palabra `Portal` —que tiene columna y catálogo propios— y las anotaciones de
vía, es lo que declara `code_noise_tokens` en `aliases.yml`: trozos que esa columna
escribe **al lado** del código sin formar parte de él. Va **por entidad** a propósito: un
número suelto no es un código en `ANCHORAGE`, pero eso no vale como regla general.

| Celda | Queda | Por qué |
|---|---|---|
| `CP+AnMC 265,00` | `CP+AnMC` | el número es la longitud de semitensión de la leyenda |
| `AnRW Portal` | `AnRW` | `Portal` es otro catálogo, con su propia columna |
| `AnMP (Track 02)` | `AnMP` | la vía ya la sabe la vía (igual que el `(T1)`) |
| `AnRW AnRW` | `AnRW` | el mismo anclaje dos veces es uno: el modelo es un conjunto |
| `FP+AnMCAnRW` | `FP+AnMC` · `AnRW` | dos pegados; los separa `code_canonical`, porque solos no hay forma de saber dónde parten |
| `TRACK 5` | — | no queda código: la celda sale en `NO_RECONOCIDO` |

Dos decisiones que **no** son ruido, y por eso están declaradas como códigos atómicos:

- **`AnRW2` es un anclaje distinto de `AnRW`**, no «dos `AnRW`». El número forma parte del
  código. Importa porque `Profile.anchorages` es un `@ManyToMany`, es decir un conjunto:
  si el `2` fuera una cantidad no habría dónde guardarlo y 108 perfiles perderían la mitad
  del dato sin que se notara. `AnRW1`, en cambio, **es `AnRW`**: uno de algo es ese algo, y
  el catálogo no puede llevar las dos grafías de lo mismo. Y el `2` **delante** (`2AnRW`)
  sí cuenta anclajes en vez de nombrar otro, así que colapsa a uno.
- **`CP/TX-P`, `CP/TX-T` y `CP/TX-W`, cada uno con o sin longitud, son nueve anclajes
  distintos.** La leyenda solo dibuja tres de los nueve, pero los datos traen las nueve
  combinaciones. Ahí el número va pegado con barra y sí es parte del código: por eso el
  filtro de ruido solo tira números **sueltos**.

Con esto el catálogo `Anchorage` pasa de **45 códigos a 28, todos atómicos y todos
habilitados**: las 17 que salen eran celdas con dos anclajes (`CP+AnMC IO`), erratas de
tecleo (`PF+AnMC` por `FP+AnMC`) o anotaciones (`TRACK 5`, `(Track 02)`) que habían
entrado porque el catálogo se cosecha leyendo cada celda de las hojas Track como si fuera
un código.

Dos reglas de `drop_concatenations` salieron de aquí y valen para **todas** las entidades:
canonicaliza cada trozo antes de comprobarlo —igual que hace `Catalog.add`, porque la
tabla de grafías se aplica a la celda entera y no casa cuando lleva dos códigos dentro—, y
distingue «no queda nada» de «no hay nada que partir». Sin lo segundo, una celda que era
solo una anotación se quedaba dentro del catálogo con `ENABLED=NO`, como si fuera un
código pendiente de decidir.

**El separador del maestro es la barra `|`, no el espacio**, y no puede ser el espacio: hay
códigos del catálogo que **llevan** espacios (`A/S Diag S/A`, `T-SIGN FOUND.`,
`CP+AnMC 265,00`), así que una celda separada por espacios es ambigua y el importador no
puede deshacerla. Es la misma barra que ya usa `ESTACIONES` en `TRACKS`. La barra solo se
escribe cuando el generador ha comprobado que **cada** parte está habilitada en el catálogo,
de modo que el importador puede partir por ella sin volver a decidir nada. Con el espacio,
`P30(CS) A/S Diag` —que es **un** código del catálogo— se partía en tres y se caían 142
perfiles al importar.

**Un código que no resuelve sale VACÍO, no tal cual.** Escribirlo daba un maestro que no se
puede cargar: el importador comprueba cada código contra su catálogo y tumbaba el perfil
entero por el valor de una relación opcional. Vaciarlo no lo esconde —queda en
`NO_RECONOCIDO` con su EP, su hoja y su fila, la fila sale `REVISAR=SI` y el generador sigue
terminando con código distinto de cero—, pero deja que el resto del perfil entre mientras se
decide qué hacer con ese código.

Dos erratas mecánicas se corrigen antes de nada, en los dos generadores: el **espacio antes
de un paréntesis** (`P50 (CS)` es `P50(CS)`, 50 códigos de cuatro catálogos) y el **código
repetido en la celda** (`S/A S/A` es `S/A`). La primera importa el doble ahora: partiendo la
cadena cruda, ese espacio rompía `P50(CS)` por la mitad.

**Una celda con varios valores no es un código.** El catálogo se cosecha leyendo cada celda
de las hojas Track como un código, así que `S/A A/S-Diag` entraba como si fuera uno más —y
al habilitar en bloque los `ENABLED=NO` se legitimaron 123 de ellos. Ahora se descartan: la
regla no es «tiene un espacio» (`T-SIGN FOUND.`, `UNIQUE SOLUTION` y `M3 Ø36` son legítimos),
sino que **todas** sus partes sean a su vez códigos de la misma entidad.

**El sufijo `-pr` es «montado en pórtico».** `Disc/IO-pr` es el mismo disconnector que
`Disc/IO`, pero sobre un pórtico en vez de sobre un poste. Lo confirma el propio catálogo:
`LoadB/PP` es *«Connections of Load Breaker (without feeder)»* y `LoadB/PP-pr` es
*«… in OCS portal (without feeder)»*. Las siete variantes están habilitadas y suman 182
perfiles. **`-pp` era una errata de `-pr`** —4 filas, todas de EP9A— y se canonicaliza.

**El maestro escribe la grafía del CATÁLOGO, no la del origen.** El importador resuelve con
`LovRepository.findByCode`, una consulta derivada y por tanto **sensible a mayúsculas**: si
el workbook trae `DISC/IO-pr` y el catálogo dice `Disc/IO-pr`, escribir lo del workbook
haría que el generador diera el maestro por bueno y el importador rechazara la fila. Los dos
candados tienen que decir lo mismo, así que el cruce busca sin distinguir mayúsculas pero
**escribe lo que dice el catálogo**.

**Todo código de lista de valores tiene que existir HABILITADO en el catálogo.** El
generador cruza cada columna de código (`SECTIONING`, `ANCHORAGE`, `ANCHORAGE_FOUNDATION`,
`FOUNDATION`, `POLE_TYPE`, `PORTAL`, `RETURN_SUPPORT`, `SECTIONING_FEEDING`,
`SUPPORT_TYPE`, `CANTILEVER_TYPE`, `STEADY_ARM_TYPE`) contra `lov-master.xlsx` y saca a `NO_RECONOCIDO` los
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
empresa, no su id: el importador lo traduce contra `business_entity`.

La empresa de los once paquetes **ya viene puesta**: `V19` siembra *Syneox*, NIF
`B10744258`, con su tipo `RAILWAY_COMPANY`, de modo que una base recién migrada puede
importar el maestro sin que nadie inserte nada a mano. Antes no era así, y había que
meter la fila en cada entorno antes de cada carga.

Para **cualquier otro** NIF sigue sin haber servicio ni endpoint que escriba esa tabla, así
que hay que darlo de alta por fuera. Un NIF que no esté **no se puede resolver**, así que
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

De las 14.461 ménsulas del maestro: 4.087 traen tipo y longitud, 5.691 solo el tipo y
4.683 no traen brazo. **Que falte la longitud no es un error**: no se conoce, y por eso
`steady_arm.length` es opcional.

## Qué se importa y qué no

De las ~36 columnas del origen, 17 tienen campo en el dominio. Las demás —`Survey`,
`Other KP`, `Theorical/Current Cant`, `Track Layout`, `Anchorage KP`, `Track KP`,
`Soil Found`, `Terrain Geometry`, `Depth Pole/Anchor F.` y `Approved by`— se conservan en
`NO_MAPEADO` con su EP, vía, perfil y fila de origen. No se importan, pero tampoco se
pierden: ampliar el modelo más adelante es un cambio de esquema y un mapper, no volver a
analizar 60 MB de Excel.

**`Supports` salió de esa lista en `V20`.** Dice qué pieza sujeta la catenaria en el poste
—`S1`, `S2`, `S1/B7`, `OCR SUPPORT`— y su catálogo, `SupportType`, existía desde `V1` y se
rellenaba desde los workbooks, pero **nadie apuntaba a él**: la columna se recogía en
`NO_MAPEADO` y se quedaba ahí. Lo incoherente no era que faltase el campo, sino que el dato
**ya decidía lo que se carga sin quedar guardado**: el generador lee
`Supports = OCR SUPPORT` para deducir que la ménsula de ese hueco es de catenaria rígida.
Se usaba la pista y se tiraba la fuente.

Es `@ManyToOne` y no una N:M como el seccionamiento o el anclaje, y no por comodidad: en
las 2.038 celdas medidas **no hay ninguna con dos códigos**. Donde el origen escribe varios
valores en una celda, el modelo lleva tabla de unión; aquí no los escribe. El maestro trae
**2.033 perfiles con tipo de soporte**, de los cuales **2.032 son cargables** —el que falta va
`ENABLED=NO` por otro motivo de su fila, no por el soporte—.

Al pasar la columna por el catálogo salieron 13 grafías que nadie había revisado, porque hasta
`V20` esa columna no se comprobaba contra nada. **Nueve describían un soporte** y se aceptan en
`track_accepted` (`SF-2PR` 27 celdas, `Beam Support` 10, `MW/CW-ISusp` 7, `S1-CLAMP` 6, `S3T` 4,
`S2-R` 4, `MP-ISusp` 4, `MW-ISusp` 3, `S1(Diag)` 1). **Las otras cuatro no son soportes**: `SECT-I`,
`FS1` y `FS/PP2` son aparatos de `Sectioning Feeding`, y `B7` es un semipórtico de `Portal`; las
cuatro son celdas escritas en la columna equivocada y se arreglan en el workbook. El soporte que va
sobre ese semipórtico ya tiene código propio, `S1/B7`, con 119 usos.

Tres de las nueve llegaban con la mayúscula bailando (`MP-Isusp`, `MW-Isusp`, `MW/CW-Isusp`). Van a
`code_canonical` junto a las variantes que ya estaban: si no, el catálogo guardaría la grafía que
llegase primero y `findByCode` distingue mayúsculas.

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
