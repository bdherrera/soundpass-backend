# soundpass-backend

API REST de SoundPass: lógica de negocio, autenticación y persistencia híbrida con PostgreSQL (datos transaccionales: eventos, entradas, órdenes) y MongoDB (reseñas, perfiles de artista y detalle de evento).

- Java 21, Spring Boot 3.4, Maven.
- PostgreSQL 18 con Spring Data JPA (`ddl-auto=validate`; el esquema oficial es `db/init/01_esquema.sql`).
- MongoDB 8.0 con Spring Data MongoDB (validadores `$jsonSchema` creados al arrancar).
- Spring Security con JWT y contraseñas BCrypt. Roles: `USUARIO` y `ADMIN`.
- Contrato de la API: [`docs/openapi.yaml`](docs/openapi.yaml). Decisiones y pendientes: [`docs/DECISIONES.md`](docs/DECISIONES.md).

## Levantar el backend con Docker

Necesitas Docker con Compose. Desde la raíz del repositorio:

```bash
cp .env.example .env      # opcional: sin él se usan valores de desarrollo
docker compose up --build
```

Esto construye la imagen de la aplicación (JDK 21 para compilar, JRE 21 liviano para ejecutar, usuario sin privilegios) y levanta `app` (puerto 8080), `postgres:18` y `mongo:8.0`. PostgreSQL ejecuta los scripts de `db/init` (`01_esquema.sql` y `02_logica_en_base_de_datos.sql`) la primera vez que se crea su volumen.

Comprobar que está arriba:

```bash
curl http://localhost:8080/actuator/health
```

Las rutas de la API cuelgan de `/api/v1` (por ejemplo `POST /api/v1/auth/registro`, `GET /api/v1/eventos`).

Para crear un ADMIN inicial define `ADMIN_CORREO` y `ADMIN_CONTRASENA` en `.env` antes de arrancar; si no se definen, no se crea ninguno. Para empezar de cero (borra los datos): `docker compose down -v`.

### Variables de entorno

Ninguna se escribe en el código ni en `application.yml`. Están listadas en [`.env.example`](.env.example):

| Variable | Uso |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexión a PostgreSQL (`DB_HOST` y `DB_PORT` los fija el compose). |
| `MONGO_URI` | Conexión a MongoDB. |
| `JWT_SECRET` | Clave para firmar los JWT, mínimo 32 caracteres. |
| `ADMIN_CORREO`, `ADMIN_CONTRASENA` | Opcionales: ADMIN inicial. |

Los valores por defecto del `docker-compose.yml` son solo para desarrollo. En un despliegue real define todos, sobre todo `JWT_SECRET` y las claves de las bases de datos.

## Lógica en la base de datos (PostgreSQL)

`db/init/02_logica_en_base_de_datos.sql` se ejecuta después del esquema al crear la base de datos y contiene:

- **Triggers:** las transiciones de estado de una entrada (DISPONIBLE → VENDIDA → USADA, y VENDIDA → CANCELADA), el
  precio histórico inmutable, el bloqueo del borrado físico, que el asistente solo se registre en una entrada VENDIDA
  y la auditoría de cambios de estado (`auditoria_estado`, con el usuario que los provocó).
- **Procedimientos y funciones** que usa la aplicación: `sp_generar_entradas`, `sp_cancelar_orden`,
  `fn_comprar_entradas` (compra idempotente sin sobreventa), `fn_checkin` y `fn_resumen_ventas_evento`.

Docker solo ejecuta los scripts de `db/init` cuando el volumen está vacío. Si ya tienes una base creada solo con el
esquema, recréala (`docker compose down -v`) o aplica el script a mano:

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < db/init/02_logica_en_base_de_datos.sql
```

## Patrones de MongoDB: cola de reserva y logs

- **Cola de reserva (patrón Computed).** Un ADMIN la habilita por evento con
  `PUT /api/v1/eventos/{id}/cola` (`{"habilitada": true, "ventana": 100, "ttlMinutos": 10}`). Los usuarios entran con
  `POST /api/v1/eventos/{id}/cola/turnos`, consultan su posición con `GET .../turnos/mio` y, cuando su turno está
  ADMITIDO, compran; al comprar o vencer el turno pasa el siguiente. Las posiciones y los totales salen de contadores
  precalculados en un único documento, no de contar turnos. Sin cola habilitada, la compra funciona como siempre.
- **Logs (patrón Bucket).** Los logs de la aplicación y una línea por petición se guardan en `logs_aplicacion`: un
  documento por hora con hasta 100 entradas, que se borra solo a los 30 días (`LOGS_RETENCION_DIAS`). Un ADMIN los
  consulta con `GET /api/v1/logs?nivel=ERROR&limite=50`.
- Si MongoDB no responde, la compra y el catálogo siguen funcionando; la cola, los logs, las reseñas y los perfiles
  responden 503.
- `GET /api/v1/eventos/{id}/ventas` (ADMIN) devuelve el reporte de ventas por tipo de entrada, calculado en PostgreSQL.

## Correr las pruebas

Las pruebas son de integración contra PostgreSQL y MongoDB reales (no H2 ni mocks). **Borran los datos de las tablas al terminar cada prueba (`TRUNCATE`): úsalas solo contra bases de datos desechables, nunca contra datos que te importen.**

Sin Java ni Maven instalados, con las bases de datos del compose y Maven en un contenedor:

```bash
docker compose up -d --wait postgres mongo
docker run --rm --network soundpass_default -v "$PWD":/work -w /work \
  -e DB_HOST=postgres -e DB_PORT=5432 -e DB_NAME=soundpass -e DB_USER=soundpass -e DB_PASSWORD=soundpass_dev \
  -e "MONGO_URI=mongodb://soundpass:soundpass_dev@mongo:27017/soundpass?authSource=admin" \
  -e JWT_SECRET="$(openssl rand -hex 32)" \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp verify
```

Con Java 21 y Maven locales, exporta las mismas variables apuntando a tus bases de datos y ejecuta `mvn -B -ntp verify`. Las pruebas cubren, entre otras cosas:

- Restricción de exclusión de eventos (solapamiento, adyacencia, cancelados) y su traducción a 409.
- Compra: concurrencia (nunca se vende más que el cupo), idempotencia, reversión completa y precio histórico.
- Reembolsos, check-in, reseñas (solo con entrada USADA), validadores de MongoDB y funcionamiento sin MongoDB.
- `ContratoApiTest`: las rutas y los niveles de acceso implementados coinciden con `docs/openapi.yaml`.
- `ArquitecturaTest`: las capas MVC y las fachadas respetan sus dependencias.

Para validar el contrato: `docker run --rm -v "$PWD":/spec -w /spec redocly/cli lint docs/openapi.yaml`.

## Estructura

El proyecto sigue **MVC con el patrón Facade**. Una petición recorre: `controlador -> fachada -> servicio -> repositorio -> modelo`. Los controladores solo conocen fachadas y DTO; `ArquitecturaTest` verifica estas reglas.

```
src/main/java/co/soundpass/
  controlador/ (C) controladores REST: HTTP, validación y autorización; solo usan fachadas
  fachada/         Facade: un punto de entrada por área (Catalogo, Evento, Compra, Entrada, Contenido, Autenticacion)
  servicio/        interfaces de los servicios (contrato de la lógica de negocio)
  servicio/impl/   implementaciones de los servicios (transacciones); nadie depende de ellas, solo de las interfaces
  repositorio/     repositorios Spring Data (JPA)
  modelo/      (M) entidades JPA, documentos MongoDB, enums y la vista v_orden_total
  vista/       (V) DTO: la representación JSON que ve el cliente
  config/          seguridad, creación de validadores e índices de MongoDB, prefijo /api/v1
  seguridad/       JWT
  error/           estructura única de error
db/init/           01 esquema oficial de PostgreSQL; 02 triggers, procedimientos y funciones
docs/              contrato OpenAPI y decisiones
```
