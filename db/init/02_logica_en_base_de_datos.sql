/*
Autor:       Brayan David Herrera Acero
Fecha:       2026-10-26
Descripcion: Logica en la base de datos de SoundPass (PostgreSQL): triggers que
             protegen las reglas de negocio y procedimientos/funciones
             almacenados que usa la aplicacion. Se ejecuta despues de
             01_esquema.sql (docker-entrypoint-initdb.d respeta el orden).
Cambios:     20261026- se creo el script con la tabla de auditoria, los triggers
             (transicion de entrada, precio historico, sin borrado fisico,
             asistente solo en entrada vendida, auditoria de estados) y los
             procedimientos y funciones (generar entradas, comprar, cancelar,
             check-in y resumen de ventas).

Convenciones:
  fn_trg_*  funciones de trigger.
  sp_*      procedimientos (CALL) con parametros INOUT para devolver datos.
  fn_*      funciones que devuelven filas.
  Errores de negocio: RAISE EXCEPTION con SQLSTATE propio, que la API traduce:
    SP404 no existe        -> HTTP 404
    SP409 estado invalido  -> HTTP 409 (TRANSICION_INVALIDA)
    SP410 sin entradas     -> HTTP 409 (ENTRADAS_AGOTADAS)
    SP411 clave en uso     -> HTTP 409 (DUPLICADO)
    SP422 dato invalido    -> HTTP 422 (VALIDACION)
*/

-- ---------------------------------------------------------------------------
-- AUDITORIA_ESTADO
-- Historial de cambios de estado de orden y entrada. id_usuario sale de la
-- variable de sesion app.usuario, que fijan los procedimientos de la API.
-- ---------------------------------------------------------------------------
CREATE TABLE auditoria_estado (
    id_auditoria     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tabla            VARCHAR(10)  NOT NULL,
    id_registro      INTEGER      NOT NULL,
    estado_anterior  VARCHAR(12),
    estado_nuevo     VARCHAR(12)  NOT NULL,
    id_usuario       INTEGER,
    fecha            TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario),
    CONSTRAINT ck_auditoria_tabla CHECK (tabla IN ('orden', 'entrada'))
);

CREATE INDEX ix_auditoria_registro ON auditoria_estado (tabla, id_registro, id_auditoria);

-- ---------------------------------------------------------------------------
-- TRIGGER: maquina de estados de ENTRADA (regla 5)
-- DISPONIBLE -> VENDIDA -> USADA, y VENDIDA -> CANCELADA. Nada mas.
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_trg_entrada_transicion() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.estado IS DISTINCT FROM NEW.estado AND NOT (
           (OLD.estado = 'DISPONIBLE' AND NEW.estado = 'VENDIDA')
        OR (OLD.estado = 'VENDIDA'    AND NEW.estado IN ('USADA', 'CANCELADA'))) THEN
        RAISE EXCEPTION 'Transicion de entrada no permitida: % -> %', OLD.estado, NEW.estado
            USING ERRCODE = 'SP409';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_entrada_transicion
    BEFORE UPDATE OF estado ON entrada
    FOR EACH ROW EXECUTE FUNCTION fn_trg_entrada_transicion();

-- ---------------------------------------------------------------------------
-- TRIGGER: precio historico inmutable (regla 4)
-- Una linea de compra no cambia ni de cantidad ni de precio una vez creada.
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_trg_detalle_inmutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id_orden IS DISTINCT FROM OLD.id_orden
       OR NEW.id_tipo_entrada IS DISTINCT FROM OLD.id_tipo_entrada
       OR NEW.cantidad IS DISTINCT FROM OLD.cantidad
       OR NEW.precio_unitario IS DISTINCT FROM OLD.precio_unitario THEN
        RAISE EXCEPTION 'Las lineas de compra no se pueden modificar (precio historico)'
            USING ERRCODE = 'SP409';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_detalle_inmutable
    BEFORE UPDATE ON detalle_compra
    FOR EACH ROW EXECUTE FUNCTION fn_trg_detalle_inmutable();

-- ---------------------------------------------------------------------------
-- TRIGGER: sin borrado fisico
-- usuario, artista, escenario, evento y orden se desactivan o cambian de estado.
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_trg_sin_borrado_fisico() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'No se permite borrar fisicamente en %: use el borrado logico', TG_TABLE_NAME
        USING ERRCODE = 'SP409';
END;
$$;

CREATE TRIGGER trg_usuario_sin_borrado   BEFORE DELETE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_trg_sin_borrado_fisico();
CREATE TRIGGER trg_artista_sin_borrado   BEFORE DELETE ON artista
    FOR EACH ROW EXECUTE FUNCTION fn_trg_sin_borrado_fisico();
CREATE TRIGGER trg_escenario_sin_borrado BEFORE DELETE ON escenario
    FOR EACH ROW EXECUTE FUNCTION fn_trg_sin_borrado_fisico();
CREATE TRIGGER trg_evento_sin_borrado    BEFORE DELETE ON evento
    FOR EACH ROW EXECUTE FUNCTION fn_trg_sin_borrado_fisico();
CREATE TRIGGER trg_orden_sin_borrado     BEFORE DELETE ON orden
    FOR EACH ROW EXECUTE FUNCTION fn_trg_sin_borrado_fisico();

-- Una entrada solo se puede borrar mientras esta DISPONIBLE (al reducir un cupo).
CREATE FUNCTION fn_trg_entrada_borrado() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.estado <> 'DISPONIBLE' THEN
        RAISE EXCEPTION 'Solo se pueden borrar entradas DISPONIBLE (esta %)', OLD.estado
            USING ERRCODE = 'SP409';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_entrada_borrado
    BEFORE DELETE ON entrada
    FOR EACH ROW EXECUTE FUNCTION fn_trg_entrada_borrado();

-- ---------------------------------------------------------------------------
-- TRIGGER: el asistente solo se registra en una entrada VENDIDA
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_trg_asistente_entrada_vendida() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM entrada e
                    WHERE e.id_entrada = NEW.id_entrada AND e.estado = 'VENDIDA') THEN
        RAISE EXCEPTION 'Solo se registra asistente en una entrada VENDIDA'
            USING ERRCODE = 'SP409';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_asistente_entrada_vendida
    BEFORE INSERT ON asistente
    FOR EACH ROW EXECUTE FUNCTION fn_trg_asistente_entrada_vendida();

-- ---------------------------------------------------------------------------
-- TRIGGERS de auditoria: orden (alta y cambios de estado) y entrada (cambios)
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_trg_auditoria_orden() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_anterior VARCHAR(12);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.estado IS NOT DISTINCT FROM NEW.estado THEN
            RETURN NEW;
        END IF;
        v_anterior := OLD.estado;
    END IF;
    INSERT INTO auditoria_estado (tabla, id_registro, estado_anterior, estado_nuevo, id_usuario)
    VALUES ('orden', NEW.id_orden, v_anterior, NEW.estado,
            NULLIF(current_setting('app.usuario', true), '')::INTEGER);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_auditoria_orden
    AFTER INSERT OR UPDATE OF estado ON orden
    FOR EACH ROW EXECUTE FUNCTION fn_trg_auditoria_orden();

CREATE FUNCTION fn_trg_auditoria_entrada() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO auditoria_estado (tabla, id_registro, estado_anterior, estado_nuevo, id_usuario)
    VALUES ('entrada', NEW.id_entrada, OLD.estado, NEW.estado,
            NULLIF(current_setting('app.usuario', true), '')::INTEGER);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_auditoria_entrada
    AFTER UPDATE OF estado ON entrada
    FOR EACH ROW WHEN (OLD.estado IS DISTINCT FROM NEW.estado)
    EXECUTE FUNCTION fn_trg_auditoria_entrada();

-- ---------------------------------------------------------------------------
-- PROCEDIMIENTO: sp_generar_entradas
-- Crea n entradas DISPONIBLE para un tipo. p_generadas devuelve cuantas creo.
-- ---------------------------------------------------------------------------
CREATE PROCEDURE sp_generar_entradas(p_tipo INTEGER, p_cantidad INTEGER, INOUT p_generadas INTEGER DEFAULT NULL)
LANGUAGE plpgsql AS $$
BEGIN
    IF p_cantidad IS NULL OR p_cantidad < 1 OR p_cantidad > 100000 THEN
        RAISE EXCEPTION 'La cantidad de entradas debe estar entre 1 y 100000' USING ERRCODE = 'SP422';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM tipo_entrada t WHERE t.id_tipo_entrada = p_tipo) THEN
        RAISE EXCEPTION 'El tipo de entrada no existe' USING ERRCODE = 'SP404';
    END IF;
    INSERT INTO entrada (id_tipo_entrada)
    SELECT p_tipo FROM generate_series(1, p_cantidad);
    p_generadas := p_cantidad;
END;
$$;

-- ---------------------------------------------------------------------------
-- FUNCION: fn_comprar_entradas (reglas 2, 3 y 4)
-- Una sola transaccion: reserva la clave de idempotencia, toma las entradas con
-- FOR UPDATE SKIP LOCKED, copia el precio vigente y las marca VENDIDA. Si falta
-- alguna entrada, la excepcion revierte todo lo hecho.
--   p_lineas: [{"idTipoEntrada": 1, "cantidad": 2}, ...] sin tipos repetidos.
--   creada = FALSE cuando la clave ya existia: se devuelve la orden original.
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_comprar_entradas(p_usuario INTEGER, p_clave VARCHAR, p_lineas JSONB)
RETURNS TABLE (orden_id INTEGER, creada BOOLEAN)
LANGUAGE plpgsql AS $$
DECLARE
    v_orden  INTEGER;
    v_dueno  INTEGER;
    v_linea  RECORD;
    v_tipo   RECORD;
    v_ids    INTEGER[];
BEGIN
    IF p_lineas IS NULL OR jsonb_typeof(p_lineas) <> 'array' OR jsonb_array_length(p_lineas) = 0 THEN
        RAISE EXCEPTION 'La compra necesita al menos una linea' USING ERRCODE = 'SP422';
    END IF;
    IF (SELECT count(DISTINCT l ->> 'idTipoEntrada') FROM jsonb_array_elements(p_lineas) l)
           <> jsonb_array_length(p_lineas) THEN
        RAISE EXCEPTION 'Hay tipos de entrada repetidos en la compra' USING ERRCODE = 'SP422';
    END IF;

    PERFORM set_config('app.usuario', p_usuario::TEXT, true);

    SELECT o.id_orden, o.id_usuario INTO v_orden, v_dueno
      FROM orden o WHERE o.clave_idempotencia = p_clave;
    IF FOUND THEN
        IF v_dueno <> p_usuario THEN
            RAISE EXCEPTION 'La clave de idempotencia ya esta en uso' USING ERRCODE = 'SP411';
        END IF;
        RETURN QUERY SELECT v_orden, FALSE;
        RETURN;
    END IF;

    -- ON CONFLICT no aborta la transaccion y espera a una solicitud concurrente con la misma clave
    INSERT INTO orden (id_usuario, clave_idempotencia, estado)
    VALUES (p_usuario, p_clave, 'PAGADA')
    ON CONFLICT (clave_idempotencia) DO NOTHING
    RETURNING orden.id_orden INTO v_orden;

    IF v_orden IS NULL THEN
        SELECT o.id_orden, o.id_usuario INTO v_orden, v_dueno
          FROM orden o WHERE o.clave_idempotencia = p_clave;
        IF v_dueno <> p_usuario THEN
            RAISE EXCEPTION 'La clave de idempotencia ya esta en uso' USING ERRCODE = 'SP411';
        END IF;
        RETURN QUERY SELECT v_orden, FALSE;
        RETURN;
    END IF;

    -- por orden de tipo, para que dos compras no se bloqueen entre si en sentidos opuestos
    FOR v_linea IN
        SELECT (l ->> 'idTipoEntrada')::INTEGER AS tipo, (l ->> 'cantidad')::INTEGER AS cantidad
          FROM jsonb_array_elements(p_lineas) l
         ORDER BY 1
    LOOP
        IF v_linea.tipo IS NULL OR v_linea.cantidad IS NULL OR v_linea.cantidad < 1 THEN
            RAISE EXCEPTION 'Cada linea necesita un tipo de entrada y una cantidad mayor que 0'
                USING ERRCODE = 'SP422';
        END IF;

        SELECT t.id_tipo_entrada, t.nombre, t.precio INTO v_tipo
          FROM tipo_entrada t
          JOIN evento ev ON ev.id_evento = t.id_evento
         WHERE t.id_tipo_entrada = v_linea.tipo AND ev.estado = 'PUBLICADO';
        IF NOT FOUND THEN
            RAISE EXCEPTION 'El tipo de entrada % no existe', v_linea.tipo USING ERRCODE = 'SP404';
        END IF;

        v_ids := ARRAY(SELECT en.id_entrada
                         FROM entrada en
                        WHERE en.id_tipo_entrada = v_linea.tipo AND en.estado = 'DISPONIBLE'
                        ORDER BY en.id_entrada
                        LIMIT v_linea.cantidad
                          FOR UPDATE SKIP LOCKED);
        IF COALESCE(array_length(v_ids, 1), 0) < v_linea.cantidad THEN
            RAISE EXCEPTION 'No hay suficientes entradas disponibles de %', v_tipo.nombre
                USING ERRCODE = 'SP410';
        END IF;

        INSERT INTO detalle_compra (id_orden, id_tipo_entrada, cantidad, precio_unitario)
        VALUES (v_orden, v_tipo.id_tipo_entrada, v_linea.cantidad, v_tipo.precio);

        UPDATE entrada SET estado = 'VENDIDA', id_orden = v_orden
         WHERE id_entrada = ANY (v_ids);
    END LOOP;

    RETURN QUERY SELECT v_orden, TRUE;
END;
$$;

-- ---------------------------------------------------------------------------
-- PROCEDIMIENTO: sp_cancelar_orden
-- Cancela una orden del usuario: las entradas VENDIDA pasan a CANCELADA; la orden
-- queda REEMBOLSADA si estaba PAGADA y CANCELADA si estaba PENDIENTE.
-- p_estado devuelve el estado final de la orden.
-- ---------------------------------------------------------------------------
CREATE PROCEDURE sp_cancelar_orden(p_orden INTEGER, p_usuario INTEGER, INOUT p_estado VARCHAR DEFAULT NULL)
LANGUAGE plpgsql AS $$
DECLARE
    v_estado VARCHAR(12);
BEGIN
    PERFORM set_config('app.usuario', p_usuario::TEXT, true);

    -- bloquea la orden: dos cancelaciones simultaneas se serializan
    SELECT o.estado INTO v_estado
      FROM orden o WHERE o.id_orden = p_orden AND o.id_usuario = p_usuario
       FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'La orden no existe' USING ERRCODE = 'SP404';
    END IF;
    IF v_estado NOT IN ('PAGADA', 'PENDIENTE') THEN
        RAISE EXCEPTION 'La orden ya esta %', v_estado USING ERRCODE = 'SP409';
    END IF;

    PERFORM 1 FROM entrada e WHERE e.id_orden = p_orden ORDER BY e.id_entrada FOR UPDATE;
    IF EXISTS (SELECT 1 FROM entrada e WHERE e.id_orden = p_orden AND e.estado = 'USADA') THEN
        RAISE EXCEPTION 'No se puede cancelar una orden con entradas ya usadas' USING ERRCODE = 'SP409';
    END IF;

    UPDATE entrada SET estado = 'CANCELADA' WHERE id_orden = p_orden AND estado = 'VENDIDA';
    UPDATE orden
       SET estado = CASE WHEN v_estado = 'PAGADA' THEN 'REEMBOLSADA' ELSE 'CANCELADA' END
     WHERE id_orden = p_orden
    RETURNING estado INTO p_estado;
END;
$$;

-- ---------------------------------------------------------------------------
-- FUNCION: fn_checkin
-- Marca la entrada USADA y guarda fecha_checkin en su asistente. Exige entrada
-- VENDIDA, evento PUBLICADO y asistente registrado.
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_checkin(p_codigo UUID, p_usuario INTEGER)
RETURNS TABLE (entrada_id INTEGER, fecha TIMESTAMPTZ)
LANGUAGE plpgsql AS $$
DECLARE
    v_id      INTEGER;
    v_estado  VARCHAR(12);
    v_evento  VARCHAR(12);
    v_fecha   TIMESTAMPTZ := clock_timestamp();
BEGIN
    PERFORM set_config('app.usuario', p_usuario::TEXT, true);

    -- bloquea la entrada: dos check-in del mismo codigo se serializan
    SELECT e.id_entrada, e.estado, ev.estado INTO v_id, v_estado, v_evento
      FROM entrada e
      JOIN tipo_entrada t ON t.id_tipo_entrada = e.id_tipo_entrada
      JOIN evento ev ON ev.id_evento = t.id_evento
     WHERE e.codigo = p_codigo
       FOR UPDATE OF e;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe una entrada con ese codigo' USING ERRCODE = 'SP404';
    END IF;
    IF v_estado <> 'VENDIDA' THEN
        RAISE EXCEPTION 'La entrada no admite check-in: esta %', v_estado USING ERRCODE = 'SP409';
    END IF;
    IF v_evento <> 'PUBLICADO' THEN
        RAISE EXCEPTION 'El evento no esta publicado' USING ERRCODE = 'SP409';
    END IF;

    UPDATE asistente SET fecha_checkin = v_fecha WHERE id_entrada = v_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'La entrada no tiene asistente registrado' USING ERRCODE = 'SP409';
    END IF;
    UPDATE entrada SET estado = 'USADA' WHERE id_entrada = v_id;

    RETURN QUERY SELECT v_id, v_fecha;
END;
$$;

-- ---------------------------------------------------------------------------
-- FUNCION: fn_resumen_ventas_evento
-- Reporte por tipo de entrada. ingresos suma las lineas de ordenes PAGADA
-- (las reembolsadas o canceladas no cuentan).
-- ---------------------------------------------------------------------------
CREATE FUNCTION fn_resumen_ventas_evento(p_evento INTEGER)
RETURNS TABLE (tipo_id INTEGER, tipo_nombre VARCHAR, tipo_cupo INTEGER, disponibles BIGINT,
               vendidas BIGINT, usadas BIGINT, canceladas BIGINT, ingresos NUMERIC)
LANGUAGE plpgsql STABLE AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM evento ev WHERE ev.id_evento = p_evento) THEN
        RAISE EXCEPTION 'El evento no existe' USING ERRCODE = 'SP404';
    END IF;

    RETURN QUERY
    SELECT t.id_tipo_entrada, t.nombre, t.cupo,
           count(e.id_entrada) FILTER (WHERE e.estado = 'DISPONIBLE'),
           count(e.id_entrada) FILTER (WHERE e.estado = 'VENDIDA'),
           count(e.id_entrada) FILTER (WHERE e.estado = 'USADA'),
           count(e.id_entrada) FILTER (WHERE e.estado = 'CANCELADA'),
           COALESCE((SELECT sum(d.subtotal)
                       FROM detalle_compra d
                       JOIN orden o ON o.id_orden = d.id_orden
                      WHERE d.id_tipo_entrada = t.id_tipo_entrada AND o.estado = 'PAGADA'), 0)
      FROM tipo_entrada t
      LEFT JOIN entrada e ON e.id_tipo_entrada = t.id_tipo_entrada
     WHERE t.id_evento = p_evento
     GROUP BY t.id_tipo_entrada, t.nombre, t.cupo
     ORDER BY t.id_tipo_entrada;
END;
$$;
