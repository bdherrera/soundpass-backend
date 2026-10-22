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

Esto construye la imagen de la aplicación (JDK 21 para compilar, JRE 21 liviano para ejecutar, usuario sin privilegios) y levanta `app` (puerto 8080), `postgres:18` y `mongo:8.0`. PostgreSQL ejecuta `db/init/01_esquema.sql` la primera vez que se crea su volumen.

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

Para validar el contrato: `docker run --rm -v "$PWD":/spec -w /spec redocly/cli lint docs/openapi.yaml`.

## Estructura

```
src/main/java/co/soundpass/
  config/      seguridad y prefijo /api/v1
  dominio/     entidades JPA y vista v_orden_total
  repositorio/ repositorios Spring Data (JPA)
  mongo/       documentos MongoDB y creación de validadores e índices
  servicio/    lógica de negocio (compra, órdenes, check-in, reseñas...)
  web/         controladores REST y DTO
  seguridad/   JWT
  error/       estructura única de error
db/init/       esquema oficial de PostgreSQL
docs/          contrato OpenAPI y decisiones
```
