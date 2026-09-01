#!/usr/bin/env bash
#
# Junta en un unico fichero los fallos de la ultima ejecucion de tests.
#
# Maven ya deja un informe por clase en target/surefire-reports (tests unitarios) y
# target/failsafe-reports (los *IT), pero son decenas de ficheros y la mayoria son de clases
# que han pasado. Esto recoge solo los que tienen algo que contar.
#
#   ./mvnw clean verify -DtrimStackTrace=false   # o 'test' si solo quieres los unitarios
#   ./scripts/test-report.sh                     # -> test-failures.txt
#
# -DtrimStackTrace=false es importante: sin el, Maven recorta la traza y a veces se lleva por
# delante justo la linea que dice donde ha fallado de verdad.
#
# Uso: ./scripts/test-report.sh [fichero-de-salida]

set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

OUT="${1:-test-failures.txt}"
DIRS=(target/surefire-reports target/failsafe-reports)

encontrados=0
: > "$OUT"

{
    echo "Informe de fallos - $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Rama: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
    echo "Commit: $(git rev-parse --short HEAD 2>/dev/null || echo '?')"
    echo
} >> "$OUT"

# Aviso util: si no hay informes, los tests no llegaron a ejecutarse (tipicamente porque la
# compilacion fallo antes). En ese caso lo que hace falta es la salida de Maven, no esto.
hay_informes=0
for dir in "${DIRS[@]}"; do
    [ -d "$dir" ] && compgen -G "$dir/*.txt" > /dev/null && hay_informes=1
done

if [ "$hay_informes" -eq 0 ]; then
    {
        echo "No hay informes en target/. Los tests no llegaron a ejecutarse:"
        echo "probablemente fallo la compilacion. Manda la salida de Maven en su lugar."
    } >> "$OUT"
    cat "$OUT"
    exit 0
fi

# 1. Resumen: que clases han fallado y con cuantos.
{
    echo "== RESUMEN =="
} >> "$OUT"

for dir in "${DIRS[@]}"; do
    [ -d "$dir" ] || continue

    for f in "$dir"/*.txt; do
        [ -e "$f" ] || continue
        grep -q "<<< \(FAILURE\|ERROR\)!" "$f" || continue

        encontrados=$((encontrados + 1))
        grep -m1 "^Tests run:" "$f" >> "$OUT"
    done
done

if [ "$encontrados" -eq 0 ]; then
    echo "Sin fallos: todas las clases con informe han pasado." >> "$OUT"
    cat "$OUT"
    exit 0
fi

# 2. Detalle de cada clase que ha fallado, con su traza.
{
    echo
    echo "== DETALLE =="
    echo
} >> "$OUT"

for dir in "${DIRS[@]}"; do
    [ -d "$dir" ] || continue

    for f in "$dir"/*.txt; do
        [ -e "$f" ] || continue
        grep -q "<<< \(FAILURE\|ERROR\)!" "$f" || continue

        cat "$f" >> "$OUT"
        echo >> "$OUT"
    done
done

echo "Escrito $OUT ($encontrados clase(s) con fallos, $(wc -l < "$OUT") lineas)"
