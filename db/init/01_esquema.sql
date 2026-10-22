/*
Autor:       Brayan David Herrera Acero
Fecha:       2026-10-21
Descripcion: Esquema relacional (PostgreSQL) de SoundPass - Plataforma de
             Conciertos y Eventos Musicales. 10 entidades del modelo ER.
Cambios:     20261021- se ajustó el script para crear el esquema inicial con
             restricción de exclusión (doble agendamiento), máquina de estados
             por CHECK, precio histórico y subtotal generado.
             20261021- se ajustó el script para usar clave primaria compuesta
             (id_orden, id_tipo_entrada) en detalle_compra y clave primaria
             compartida (id_entrada) en asistente.
*/

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- USUARIO
-- ---------------------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(100) NOT NULL,
    correo           VARCHAR(150) NOT NULL,
    contrasena_hash  VARCHAR(255) NOT NULL,
    rol              VARCHAR(10)  NOT NULL DEFAULT 'USUARIO',
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_registro   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_correo UNIQUE (correo),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('USUARIO', 'ADMIN'))
);

-- ---------------------------------------------------------------------------
-- ARTISTA
-- ---------------------------------------------------------------------------
CREATE TABLE artista (
    id_artista       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_artistico VARCHAR(100) NOT NULL,
    pais             VARCHAR(60),
    genero           VARCHAR(60),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------------
-- ESCENARIO
-- ---------------------------------------------------------------------------
CREATE TABLE escenario (
    id_escenario     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(100) NOT NULL,
    ciudad           VARCHAR(80)  NOT NULL,
    direccion        VARCHAR(200),
    capacidad        INTEGER      NOT NULL,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_escenario_capacidad CHECK (capacidad > 0)
);

-- ---------------------------------------------------------------------------
-- EVENTO
-- La restricción de exclusión impide que un escenario tenga dos eventos
-- solapados en el tiempo; los eventos cancelados no cuentan.
-- ---------------------------------------------------------------------------
CREATE TABLE evento (
    id_evento        INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_escenario     INTEGER      NOT NULL,
    nombre           VARCHAR(150) NOT NULL,
    fecha_inicio     TIMESTAMPTZ  NOT NULL,
    fecha_fin        TIMESTAMPTZ  NOT NULL,
    estado           VARCHAR(12)  NOT NULL DEFAULT 'BORRADOR',
    CONSTRAINT fk_evento_escenario FOREIGN KEY (id_escenario)
        REFERENCES escenario (id_escenario),
    CONSTRAINT ck_evento_fechas CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT ck_evento_estado CHECK (estado IN
        ('BORRADOR', 'PUBLICADO', 'CANCELADO', 'FINALIZADO')),
    CONSTRAINT ex_evento_sin_cruce EXCLUDE USING gist (
        id_escenario WITH =,
        tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADO')
);

-- ---------------------------------------------------------------------------
-- EVENTO_ARTISTA (tabla puente N:M con atributos)
-- ---------------------------------------------------------------------------
CREATE TABLE evento_artista (
    id_evento           INTEGER NOT NULL,
    id_artista          INTEGER NOT NULL,
    orden_presentacion  INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT pk_evento_artista PRIMARY KEY (id_evento, id_artista),
    CONSTRAINT fk_evar_evento FOREIGN KEY (id_evento)
        REFERENCES evento (id_evento) ON DELETE CASCADE,
    CONSTRAINT fk_evar_artista FOREIGN KEY (id_artista)
        REFERENCES artista (id_artista),
    CONSTRAINT ck_evar_orden CHECK (orden_presentacion > 0)
);

-- ---------------------------------------------------------------------------
-- TIPO_ENTRADA
-- ---------------------------------------------------------------------------
CREATE TABLE tipo_entrada (
    id_tipo_entrada  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_evento        INTEGER       NOT NULL,
    nombre           VARCHAR(60)   NOT NULL,
    precio           NUMERIC(12,2) NOT NULL,
    cupo             INTEGER       NOT NULL,
    CONSTRAINT fk_tipoent_evento FOREIGN KEY (id_evento)
        REFERENCES evento (id_evento),
    CONSTRAINT uq_tipoent_evento_nombre UNIQUE (id_evento, nombre),
    CONSTRAINT ck_tipoent_precio CHECK (precio >= 0),
    CONSTRAINT ck_tipoent_cupo CHECK (cupo > 0)
);

-- ---------------------------------------------------------------------------
-- ORDEN
-- clave_idempotencia evita órdenes duplicadas por doble envío.
-- El total no se almacena: se calcula en la vista v_orden_total.
-- ---------------------------------------------------------------------------
CREATE TABLE orden (
    id_orden            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario          INTEGER      NOT NULL,
    fecha               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    estado              VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
    clave_idempotencia  VARCHAR(64)  NOT NULL,
    CONSTRAINT fk_orden_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario),
    CONSTRAINT uq_orden_idempotencia UNIQUE (clave_idempotencia),
    CONSTRAINT ck_orden_estado CHECK (estado IN
        ('PENDIENTE', 'PAGADA', 'CANCELADA', 'REEMBOLSADA'))
);

-- ---------------------------------------------------------------------------
-- DETALLE_COMPRA (entidad débil: PK compuesta natural con la orden)
-- Una orden no repite el mismo tipo de entrada en dos líneas.
-- precio_unitario guarda el precio al momento de la compra.
-- ---------------------------------------------------------------------------
CREATE TABLE detalle_compra (
    id_orden         INTEGER       NOT NULL,
    id_tipo_entrada  INTEGER       NOT NULL,
    cantidad         INTEGER       NOT NULL,
    precio_unitario  NUMERIC(12,2) NOT NULL,
    subtotal         NUMERIC(14,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
    CONSTRAINT pk_detalle_compra PRIMARY KEY (id_orden, id_tipo_entrada),
    CONSTRAINT fk_detcomp_orden FOREIGN KEY (id_orden)
        REFERENCES orden (id_orden) ON DELETE CASCADE,
    CONSTRAINT fk_detcomp_tipoent FOREIGN KEY (id_tipo_entrada)
        REFERENCES tipo_entrada (id_tipo_entrada),
    CONSTRAINT ck_detcomp_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detcomp_precio CHECK (precio_unitario >= 0)
);

-- ---------------------------------------------------------------------------
-- ENTRADA (una fila por unidad de inventario, con máquina de estados)
-- DISPONIBLE no tiene orden; cualquier otro estado sí.
-- ---------------------------------------------------------------------------
CREATE TABLE entrada (
    id_entrada       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_tipo_entrada  INTEGER     NOT NULL,
    id_orden         INTEGER,
    codigo           UUID        NOT NULL DEFAULT gen_random_uuid(),
    estado           VARCHAR(12) NOT NULL DEFAULT 'DISPONIBLE',
    CONSTRAINT fk_entrada_tipoent FOREIGN KEY (id_tipo_entrada)
        REFERENCES tipo_entrada (id_tipo_entrada),
    CONSTRAINT fk_entrada_orden FOREIGN KEY (id_orden)
        REFERENCES orden (id_orden),
    CONSTRAINT uq_entrada_codigo UNIQUE (codigo),
    CONSTRAINT ck_entrada_estado CHECK (estado IN
        ('DISPONIBLE', 'VENDIDA', 'USADA', 'CANCELADA')),
    CONSTRAINT ck_entrada_orden CHECK (
        (estado = 'DISPONIBLE' AND id_orden IS NULL) OR
        (estado <> 'DISPONIBLE' AND id_orden IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------------
-- ASISTENTE (una persona por entrada: la PK es a la vez FK hacia entrada)
-- ---------------------------------------------------------------------------
CREATE TABLE asistente (
    id_entrada       INTEGER      NOT NULL,
    nombre           VARCHAR(100) NOT NULL,
    documento        VARCHAR(30)  NOT NULL,
    correo           VARCHAR(150),
    fecha_checkin    TIMESTAMPTZ,
    CONSTRAINT pk_asistente PRIMARY KEY (id_entrada),
    CONSTRAINT fk_asistente_entrada FOREIGN KEY (id_entrada)
        REFERENCES entrada (id_entrada)
);

-- ---------------------------------------------------------------------------
-- Vista: total de cada orden (atributo derivado)
-- ---------------------------------------------------------------------------
CREATE VIEW v_orden_total AS
SELECT o.id_orden,
       o.id_usuario,
       o.estado,
       COALESCE(SUM(d.subtotal), 0) AS total
  FROM orden o
  LEFT JOIN detalle_compra d ON d.id_orden = o.id_orden
 GROUP BY o.id_orden, o.id_usuario, o.estado;

-- ---------------------------------------------------------------------------
-- Índices (las PK, UNIQUE y la exclusión ya crean los suyos)
-- ---------------------------------------------------------------------------
CREATE INDEX ix_evento_escenario      ON evento (id_escenario);
CREATE INDEX ix_evento_estado_inicio  ON evento (estado, fecha_inicio);
CREATE INDEX ix_evar_artista          ON evento_artista (id_artista);
CREATE INDEX ix_orden_usuario         ON orden (id_usuario);
CREATE INDEX ix_detcomp_tipoent       ON detalle_compra (id_tipo_entrada);
CREATE INDEX ix_entrada_orden         ON entrada (id_orden);
CREATE INDEX ix_entrada_disponibles   ON entrada (id_tipo_entrada, id_entrada)
    WHERE estado = 'DISPONIBLE';
