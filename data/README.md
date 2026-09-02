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

## Un aviso sobre `workbook/`

Son unos 60 MB de binarios versionados en git. Si la carpeta sigue creciendo,
conviene valorar Git LFS.
