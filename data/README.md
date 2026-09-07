# `data/` — Catálogo maestro de LOVs

Esta carpeta contiene los datos maestros (LOVs) del dominio de infraestructura y la
herramienta que los consolida.

```
data/
├── workbook/               # Workbooks de Execution Package (fuente, tal cual los entrega ingeniería)
├── tools/
│   ├── build_lov_master.py # Generador
│   ├── aliases.yml         # Tablas de mapeo — se amplía aquí, no en el script
│   └── tests/              # Pruebas de las reglas de mapeo (unittest, sin dependencias)
└── lov-master.xlsx         # Catálogo consolidado (generado). Es lo que importa la aplicación.
```

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
