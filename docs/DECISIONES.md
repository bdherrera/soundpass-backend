# Decisiones

Formato: fecha, tarea, decisión y motivo.

- 2026-10-21 · T00 · JSON en camelCase en español (`idEvento`, `fechaInicio`, `claveIdempotencia`) en lugar de snake_case. Es la convención de Java/Jackson y evita configurar naming strategies.
- 2026-10-21 · T00 · Rutas bajo `/api/v1`; los recursos anidados usan el evento como prefijo (`/eventos/{id}/tipos-entrada`, `/eventos/{id}/resenas`).
- 2026-10-21 · T00 · La compra recibe `claveIdempotencia` en el cuerpo (no en cabecera) y devuelve 201 al crear y 200 al repetir la clave.
- 2026-10-21 · T00 · No hay DELETE de `tipo_entrada` ni de evento: el evento se "borra" con `PATCH /eventos/{id}/estado` a CANCELADO, y el tipo de entrada solo se edita. Motivo: hay FKs desde `entrada` y `detalle_compra`, y no se hace borrado físico.
- 2026-10-21 · T00 · Los recursos de otro usuario (orden, entrada) responden 404 y no 403, para no revelar su existencia. 403 se reserva para rol insuficiente y para reseñas sin entrada USADA.
- 2026-10-21 · T00 · Cancelar una orden: PAGADA pasa a REEMBOLSADA y PENDIENTE a CANCELADA.
- 2026-10-21 · T00 · El asistente lo registra el dueño de la entrada (`PUT /entradas/{id}/asistente`); el check-in lo hace un ADMIN con `POST /checkins` y el código UUID.
- 2026-10-21 · T00 · Perfil de artista y detalle de evento: GET responde 404 si no hay documento en MongoDB; el catálogo no depende de ellos.
- 2026-10-21 · T00 · El token de login incluye el objeto `usuario` (sin hash) y `expiraEn`.
- 2026-10-22 · T01 · Los tests de integración se llaman `*Test` (no `*IT`) para que los ejecute Surefire en `mvn verify` sin configurar Failsafe.
- 2026-10-22 · T01 · El health check del contenedor usa `wget` (viene en la imagen Alpine del JRE) contra `/actuator/health`.
- 2026-10-22 · T02 · Los ids compuestos (`EventoArtistaId`, `DetalleCompraId`) son `record` embebibles; `Orden` mapea sus líneas con `@OneToMany(mappedBy)` y la vista `v_orden_total` es una entidad de solo lectura (`OrdenTotal`).
- 2026-10-22 · T02 · La prueba de PK repetida en `detalle_compra` inserta la segunda fila con JDBC: con JPA, `save` sobre una clave existente haría merge (UPDATE) y no fallaría.
- 2026-10-22 · T03 · El JWT (HS256, 60 min) no se revalida contra la base en cada petición: un usuario desactivado conserva su token hasta que expira. Motivo: evitar una consulta por petición.
- 2026-10-22 · T03 · Correos en minúsculas y sin espacios al registrar y al iniciar sesión. Contraseña de 8 a 72 caracteres (límite de BCrypt).
- 2026-10-22 · T03 · ADMIN inicial opcional con `ADMIN_CORREO` y `ADMIN_CONTRASENA`; solo se crea si ambos tienen valor y el correo no existe.
- 2026-10-22 · T03 · Todos los controladores cuelgan de `/api/v1` mediante `WebConfig`. Las operaciones de ADMIN se protegen con `@PreAuthorize` en cada método; lecturas GET de artistas, escenarios y eventos son públicas.
- 2026-10-22 · T04 · Borrar un artista o escenario inexistente da 404; borrarlo dos veces es idempotente (204). Los desactivados responden 404 en GET y PUT.
- 2026-10-22 · T05 · Transiciones de evento: BORRADOR→PUBLICADO/CANCELADO, PUBLICADO→CANCELADO/FINALIZADO, CANCELADO→BORRADOR (reactivar; puede dar 409 por solapamiento), FINALIZADO sin salida. Cancelar un evento no cancela las entradas ya vendidas (eso es responsabilidad de los reembolsos, T08).
- 2026-10-22 · T05 · Un evento que no está PUBLICADO responde 404 a quien no es ADMIN (detalle, tipos de entrada y disponibilidad).
- 2026-10-22 · T06 · Se añade el código `CUPO_INSUFICIENTE` (409) y se limita `cupo` a 100000 por solicitud para no generar millones de filas por error; contrato actualizado.
- 2026-10-22 · T06 · `cupo` es el total de entradas del tipo. Al bajarlo se quitan (DELETE físico) entradas DISPONIBLE sin vender, nunca las vendidas; al subirlo se generan las que faltan. No se valida la suma de cupos contra la capacidad del escenario.
- 2026-10-22 · Pruebas · Las pruebas de API (`PruebaApiBase`) no son transaccionales y vacían las tablas con TRUNCATE al terminar; un error HTTP dentro de una transacción compartida deja la transacción de PostgreSQL inservible.
- 2026-10-22 · T07 · No hay pasarela de pago: la orden nace PAGADA al completarse la compra. Una clave de idempotencia repetida devuelve la orden original (200) aunque el cuerpo sea distinto; si es de otro usuario responde 409 DUPLICADO.
- 2026-10-22 · T07 · La clave se reserva con `INSERT ... ON CONFLICT DO NOTHING RETURNING` (no aborta la transacción y espera a la solicitud concurrente con la misma clave). Si la compra falla, se revierte todo y la clave queda libre.
- 2026-10-22 · T07 · Con `SKIP LOCKED` una compra simultánea puede ver menos entradas de las que hay (las otras están bloqueadas por compras en curso) y recibir 409 aunque quedaran disponibles; es el comportamiento pedido. Solo se venden eventos PUBLICADOS; un tipo de otro estado responde 404.
- 2026-10-22 · T08 · Cancelar una orden deja las entradas en CANCELADA y no las devuelve al inventario (la regla 5 no admite CANCELADA -> DISPONIBLE). Las entradas de un cupo cancelado no se revenden.
- 2026-10-22 · T08 · La orden y sus entradas se bloquean con `FOR UPDATE` al cancelar, para que dos cancelaciones (o una cancelación y un check-in) no se pisen.
- 2026-10-22 · T09 · El check-in exige que la entrada tenga asistente registrado, porque `fecha_checkin` vive en `asistente`. Sin asistente responde 409 TRANSICION_INVALIDA.
- 2026-10-22 · T10 · Las colecciones de MongoDB, sus validadores y sus índices los crea `MongoEsquema` al arrancar (idempotente con `collMod`). Si MongoDB no responde, la aplicación arranca igual y el esquema se prepara en la primera escritura. Reseñas, perfiles y detalles responden 503 mientras MongoDB esté caído.
- 2026-10-22 · T10 · La reseña guarda una copia del nombre del usuario (`usuario.nombre`); no se actualiza si el nombre cambia (hoy no existe endpoint para cambiarlo).
- 2026-10-22 · T11 · `management.health.mongo.enabled=false`: el health check del contenedor no depende de MongoDB (es opcional). La prueba `MongoCaidoTest` arranca la aplicación con una URI de MongoDB inalcanzable y comprueba que catálogo y compra funcionan.
- 2026-10-22 · T11 · Una ruta de imagen es relativa si no empieza por `/` ni `\`, no contiene `:` (sin esquema ni unidad) ni `..`. La regla vive en `RutaRelativa` y se aplica en Bean Validation y en el validador de MongoDB.
- 2026-10-22 · T12 · Las reglas de ADMIN se evalúan también por URL en `SeguridadConfig` (además de `@PreAuthorize`) para que un USUARIO reciba 403 antes de que se valide el cuerpo. `ContratoApiTest` comprueba rutas y niveles de acceso contra `docs/openapi.yaml`.
- 2026-10-22 · T12 · Se añadió el 503 al contrato para las operaciones que dependen de MongoDB.

## Pendiente o dudoso (ordenado por importancia)

1. **Pasarela de pago inexistente.** La orden nace PAGADA; el estado PENDIENTE solo existe en el esquema. Hay que decidir cómo se integra el pago real antes de producción.
2. **Sesiones.** El JWT no se revalida contra la base: un usuario desactivado o cuyo rol cambie conserva el token hasta que expira (60 min). No hay logout, refresco ni revocación.
3. **Entradas canceladas no se revenden.** Cancelar una orden deja las entradas en CANCELADA; la regla 5 no admite volver a DISPONIBLE. Si el negocio quiere reponer inventario, hay que cambiar la regla y la restricción del esquema. Cancelar un evento no reembolsa ni cancela las órdenes ya vendidas.
4. **Protección contra abuso.** No hay límite de intentos de login, ni verificación de correo, ni reglas de complejidad de contraseña más allá de la longitud (8 a 72).
5. **Compras con mucha contención.** `SKIP LOCKED` puede devolver 409 a un comprador aunque queden entradas (otras compras las tienen bloqueadas un instante); el cliente debe poder reintentar. La clave de idempotencia devuelve la orden original aunque el cuerpo de la repetición sea distinto.
6. **Asistentes.** Solo el dueño de la entrada registra al asistente y no hay endpoint para corregirlo; el check-in exige asistente registrado porque `fecha_checkin` está en esa tabla.
7. **Cupos.** No se valida que la suma de cupos de un evento no supere la capacidad del escenario, y reducir el cupo borra físicamente entradas DISPONIBLE. Mover las fechas de un evento con entradas vendidas no tiene restricciones ni avisa a los compradores.
8. **Pruebas destructivas.** Las pruebas de API vacían las tablas de PostgreSQL y las colecciones de MongoDB; solo deben correr contra bases desechables.
9. **Filtros de fecha** (`desde`, `hasta`): el signo `+` de una zona horaria debe ir codificado en la URL (`%2B`); `Z` no necesita codificarse.
10. **Entorno de construcción.** Este equipo tiene Java 8 y no tiene Maven; todo se compiló y probó con la imagen `maven:3.9-eclipse-temurin-21` contra PostgreSQL 18 y MongoDB 8.0 en Docker. Los archivos del kit del agente nocturno (`run_night.sh`, `Dockerfile.agente`, `docker-compose.agente.yml`, `LEEME.md`, `tareas/`) se dejaron en el repositorio tal cual llegaron y no forman parte de la aplicación.
