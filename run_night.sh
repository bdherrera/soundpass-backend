#!/usr/bin/env bash
# Orquestador nocturno del backend de SoundPass.
# Se ejecuta en el equipo anfitrión (no dentro del contenedor del agente). Para cada tarea:
#   1. reinicia PostgreSQL y MongoDB desechables,
#   2. ejecuta Claude Code dentro del contenedor del agente,
#   3. verifica por su cuenta (pruebas, docker build, docker compose config),
#   4. si pasa, hace commit; si falla, reintenta una vez; si vuelve a fallar, descarta los cambios.
# El agente nunca hace commit ni push: eso lo hace este script con tus credenciales de git.
#
# Variables opcionales: HORAS_MAX (8), PUSH (1 = subir la rama al terminar), TAREAS (tareas),
# RAMA_BASE (main: la rama de la que parte la rama nocturna).

set -u
cd "$(dirname "$0")" || exit 1

COMPOSE="docker compose -f docker-compose.agente.yml"
HORAS_MAX="${HORAS_MAX:-8}"
PUSH="${PUSH:-1}"
RAMA_BASE="${RAMA_BASE:-main}"
DIR_TAREAS="${TAREAS:-tareas}"
FECHA="$(date +%Y%m%d)"
RAMA="agente/noche-${FECHA}"
LOGS="logs/${FECHA}"
INFORME="${LOGS}/informe.md"
LIMITE=$(( $(date +%s) + HORAS_MAX * 3600 ))

abortar() { echo "ERROR: $*" >&2; exit 1; }

# ---------------------------------------------------------------- comprobaciones previas
command -v docker >/dev/null 2>&1 || abortar "docker no está disponible"
command -v git >/dev/null 2>&1 || abortar "git no está disponible"
git rev-parse --verify HEAD >/dev/null 2>&1 || abortar "el repositorio no tiene ningún commit; haz el primer commit con el kit (ver LEEME.md)"
[ -f .env.agente ] || abortar "falta .env.agente (copia .env.agente.example y pon tu token)"
[ -f .env ] && abortar "hay un .env en la carpeta; muévelo fuera del repositorio esta noche (el agente podría leerlo)"
git diff --quiet && git diff --cached --quiet \
  || abortar "hay cambios sin confirmar; haz commit del kit antes de empezar"
ls "${DIR_TAREAS}"/*.md >/dev/null 2>&1 || abortar "no hay tareas en ${DIR_TAREAS}/"

mkdir -p "$LOGS"
git rev-parse --verify "$RAMA_BASE" >/dev/null 2>&1 || abortar "no existe la rama base $RAMA_BASE (usa RAMA_BASE=...)"
git checkout -B "$RAMA" "$RAMA_BASE" >/dev/null 2>&1 || abortar "no pude crear la rama $RAMA desde $RAMA_BASE"

{
  echo "# Informe nocturno ${FECHA}"
  echo
  echo "| Tarea | Modelo | Resultado | Intentos | Costo estimado (USD) |"
  echo "|---|---|---|---|---|"
} > "$INFORME"

echo "Construyendo la imagen del agente..."
$COMPOSE build agente > "$LOGS/build-agente.log" 2>&1 || abortar "falló la construcción de la imagen (ver $LOGS/build-agente.log)"

# ---------------------------------------------------------------- funciones
leer() { # leer <archivo> <clave> <por_defecto>
  local v
  v="$(sed -n "s/^$2:[[:space:]]*//p" "$1" | head -1)"
  echo "${v:-$3}"
}

levantar_dbs() {
  $COMPOSE rm -sfv postgres mongo >/dev/null 2>&1
  $COMPOSE up -d --wait postgres mongo >> "$LOGS/dbs.log" 2>&1
}

ejecutar_agente() { # ejecutar_agente <id> <intento> <modelo> <presupuesto> <turnos> <prompt>
  local salida="$LOGS/$1.intento$2.json"
  $COMPOSE run --rm -T agente claude -p "$6" \
    --model "$3" --max-budget-usd "$4" --max-turns "$5" \
    --dangerously-skip-permissions --output-format json \
    > "$salida" 2> "$salida.err"
  echo "$salida"
}

verificar() { # verificar <id> <comando>
  local log="$LOGS/$1.verificar.log"
  : > "$log"
  levantar_dbs || { echo "no levantaron las bases de datos" >> "$log"; return 1; }
  $COMPOSE run --rm -T agente bash -lc "$2" >> "$log" 2>&1 || return 1
  if [ -f docker-compose.yml ]; then
    local envf=""
    [ -f .env.example ] && envf="--env-file .env.example"
    docker compose $envf -f docker-compose.yml config -q >> "$log" 2>&1 || return 1
  fi
  if [ -f Dockerfile ]; then
    docker build -q -t soundpass-backend:noche . >> "$log" 2>&1 || return 1
  fi
  return 0
}

costo() { grep -oE '"total_cost_usd":[0-9.]+' "$1" 2>/dev/null | head -1 | cut -d: -f2; }

descartar() { git reset --hard HEAD >/dev/null 2>&1; git clean -fd >/dev/null 2>&1; }

# ---------------------------------------------------------------- bucle principal
for f in "${DIR_TAREAS}"/*.md; do
  ID="$(basename "$f" .md)"
  MODELO="$(leer "$f" modelo claude-sonnet-5-5)"
  PRESUPUESTO="$(leer "$f" presupuesto 4)"
  TURNOS="$(leer "$f" turnos 100)"
  VERIFICAR="$(leer "$f" verificar 'mvn -B -ntp verify')"

  if [ "$(date +%s)" -ge "$LIMITE" ]; then
    echo "| $ID | $MODELO | OMITIDA (se acabó el tiempo) | 0 | - |" >> "$INFORME"
    continue
  fi

  echo "[$(date +%H:%M)] $ID ($MODELO)"
  PROMPT="Eres el agente de desarrollo del backend de SoundPass. Lee primero CLAUDE.md y luego implementa SOLO la tarea descrita en ${f}. No hagas git commit ni git push, no modifiques nada dentro de ${DIR_TAREAS}/, y si algo es ambiguo elige la opción más simple y regístrala en docs/DECISIONES.md. Al terminar, ejecuta tú mismo el comando de verificación de la tarea (${VERIFICAR}) y corrige hasta que pase."

  levantar_dbs
  SALIDA="$(ejecutar_agente "$ID" 1 "$MODELO" "$PRESUPUESTO" "$TURNOS" "$PROMPT")"
  COSTO="$(costo "$SALIDA")"
  INTENTOS=1

  if verificar "$ID" "$VERIFICAR"; then
    RESULTADO="OK"
  else
    INTENTOS=2
    COLA="$(tail -n 60 "$LOGS/$ID.verificar.log")"
    PROMPT2="La verificación automática de la tarea ${ID} falló. Últimas líneas del registro:

${COLA}

Corrige el problema sin ampliar el alcance de la tarea y vuelve a ejecutar la verificación (${VERIFICAR})."
    levantar_dbs
    SALIDA2="$(ejecutar_agente "$ID" 2 "$MODELO" "$PRESUPUESTO" "$TURNOS" "$PROMPT2")"
    C2="$(costo "$SALIDA2")"
    COSTO="$(echo "${COSTO:-0} ${C2:-0}" | awk '{printf "%.2f", $1 + $2}')"
    if verificar "$ID" "$VERIFICAR"; then
      RESULTADO="OK (tras reintento)"
    else
      RESULTADO="FALLÓ (cambios descartados)"
      descartar
    fi
  fi

  case "$RESULTADO" in
    OK*)
      git add -A
      git commit -q -m "${ID}: implementación del agente" || RESULTADO="${RESULTADO} - sin cambios para confirmar"
      ;;
  esac

  echo "| $ID | $MODELO | $RESULTADO | $INTENTOS | ${COSTO:-n/d} |" >> "$INFORME"
done

$COMPOSE down >/dev/null 2>&1

{
  echo
  echo "Rama: \`$RAMA\`. Registros por tarea en \`$LOGS/\`. Decisiones del agente en \`docs/DECISIONES.md\`."
} >> "$INFORME"

if [ "$PUSH" = "1" ]; then
  git push -u origin "$RAMA" >> "$LOGS/push.log" 2>&1 \
    && echo "Rama subida: $RAMA" \
    || echo "No se pudo subir la rama (ver $LOGS/push.log); queda en local."
fi

echo "Terminado. Informe: $INFORME"
