# `data/` — Catálogo maestro de LOVs

Esta carpeta contiene los datos maestros (LOVs) del dominio de infraestructura y la
herramienta que los consolida.

```
data/
├── workbook/               # Workbooks de Execution Package (fuente, tal cual los entrega ingeniería)
├── tools/
│   ├── build_lov_master.py # Generador
│   └── aliases.yml         # Tablas de mapeo — se amplía aquí, no en el script
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
las hojas de trazado pero que ningún catálogo curado recoge. Para aceptarlas basta
poner `ENABLED=SI`.

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
