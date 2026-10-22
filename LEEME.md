# Kit del agente nocturno (backend SoundPass)

Contenido: `CLAUDE.md` (reglas del proyecto para el agente), `tareas/` (T00 a T12), `run_night.sh`,
`Dockerfile.agente`, `docker-compose.agente.yml`, `.env.agente.example`, `db/init/01_esquema.sql`.

## Antes de dormir

1. Clona el repositorio vacío y copia dentro todo el contenido de esta carpeta (incluido `.gitignore`).
2. `cp .env.agente.example .env.agente` y pon el token (`claude setup-token`) o la clave de API. Solo una.
3. Mueve fuera del repositorio cualquier `.env` con credenciales reales de las VMs.
4. Primer commit y subida de la rama principal (el script necesita un commit y un árbol limpio):
   `git add -A && git commit -m "Kit del agente y esquema de base de datos" && git push -u origin main`
5. Apaga las VMs de VMware y cierra lo que no uses (el equipo tiene poca RAM).
6. Evita que el equipo se suspenda durante la noche.
7. Prueba con una sola tarea: crea una carpeta `prueba/` con solo `T00-contrato-api.md` y ejecuta
   `TAREAS=prueba PUSH=0 bash run_night.sh`. Revisa `logs/` y `git log`.
8. Lanzamiento real: `bash run_night.sh` (o prográmalo, por ejemplo con cron:
   `30 23 21 10 * cd /ruta/al/backend && bash run_night.sh >> logs/cron.log 2>&1`).

## Por la mañana

- `logs/AAAAMMDD/informe.md`: resultado, intentos y costo estimado por tarea.
- `git log` de la rama `agente/noche-AAAAMMDD`: un commit por tarea que pasó la verificación.
- `docs/DECISIONES.md`: decisiones que tomó el agente ante ambigüedades.
- Tareas marcadas FALLÓ: sus cambios se descartaron; los registros están en `logs/`.

## Notas

- Las tareas se ejecutan en orden y cada una depende de las anteriores; si una falla, las siguientes
  pueden fallar también.
- Variables: `HORAS_MAX` (por defecto 8), `PUSH` (1 sube la rama al terminar), `TAREAS` (carpeta de tareas).
- Cada tarea tiene tope de presupuesto y de turnos en su cabecera; ajústalos ahí.
