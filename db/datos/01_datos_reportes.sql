/*
Autor:       Brayan David Herrera Acero
Fecha:       2026-11-04
Descripcion: Datos de prueba para generar los reportes de db/reportes/reportes.sql.
             Crea usuarios, artistas, escenarios, 13 eventos (6 finalizados, 5 publicados,
             1 borrador y 1 cancelado), tipos de entrada, compras, cancelaciones, asistentes
             y check-ins. Todo pasa por la logica de la base (sp_generar_entradas,
             fn_comprar_entradas, sp_cancelar_orden y fn_checkin), asi que respeta los
             triggers y deja la auditoria al dia.
Uso:         Despues de 01_esquema.sql y 02_logica_en_base_de_datos.sql, una sola vez:
               psql -U <usuario> -d <base> -v ON_ERROR_STOP=1 -f db/datos/01_datos_reportes.sql
             Es idempotente: si los datos ya estan cargados no hace nada.
Aviso:       Son datos ficticios (correos @datos.soundpass.test, contrasena no usable).
             No lo ejecutes en una base con datos reales que quieras conservar limpios.
Cambios:     20261104- version inicial.
*/

DO $datos$
DECLARE
    v_hash      TEXT := '$2a$10$' || repeat('x', 53);   -- no es un hash valido: esos usuarios no inician sesion
    v_nombres   TEXT[] := ARRAY['Camila','Andrés','Valentina','Santiago','Isabella','Mateo','Sofía','Daniel',
                                'Mariana','Sebastián','Laura','Felipe','Daniela','Juan','Paula','Nicolás',
                                'Natalia','Carlos','Luisa','Esteban'];
    v_apellidos TEXT[] := ARRAY['Gómez','Rodríguez','Martínez','López','García','Pérez','Sánchez','Ramírez',
                                'Torres','Díaz','Vargas','Castro','Rojas','Moreno','Jiménez','Herrera',
                                'Ortiz','Mendoza','Cárdenas','Pardo'];
    v_def       JSONB := $j$[
      {"n":"Aurora Nocturna en vivo","e":1,"d":-75,"s":"FINALIZADO","a":[1],"t":[["General",120000,200],["VIP",350000,50]],"v":0.90,"x":true},
      {"n":"Noche de Rock: Voltaje","e":1,"d":-50,"s":"FINALIZADO","a":[3],"t":[["General",150000,300],["Platea",280000,80]],"v":0.97,"x":true},
      {"n":"Gran Noche de Salsa","e":2,"d":-60,"s":"FINALIZADO","a":[2,1],"t":[["General",90000,150],["Preferencial",200000,40]],"v":0.60,"x":true},
      {"n":"Festival Indie del Pacífico","e":4,"d":-40,"s":"FINALIZADO","a":[4,7],"t":[["General",110000,250],["VIP",300000,60]],"v":0.75,"x":true},
      {"n":"Cumbia en la Plaza","e":5,"d":-25,"s":"FINALIZADO","a":[5],"t":[["General",70000,120],["Palco",180000,30]],"v":0.45,"x":true},
      {"n":"Electro Sunset","e":3,"d":-15,"s":"FINALIZADO","a":[6],"t":[["General",130000,400],["VIP",380000,80],["Backstage",600000,20]],"v":0.80,"x":true},
      {"n":"Pop Latino Tour","e":1,"d":20,"s":"PUBLICADO","a":[7,1],"t":[["General",140000,300],["VIP",400000,60]],"v":0.55,"x":false},
      {"n":"Flamenco Fusión","e":2,"d":35,"s":"PUBLICADO","a":[8],"t":[["General",100000,150],["Preferencial",220000,40]],"v":0.30,"x":false},
      {"n":"Reggaetón Fest","e":3,"d":50,"s":"PUBLICADO","a":[9,10],"t":[["General",120000,400],["VIP",350000,80]],"v":0.20,"x":false},
      {"n":"Hip Hop Underground","e":4,"d":65,"s":"PUBLICADO","a":[10],"t":[["General",80000,200],["VIP",220000,40]],"v":0.10,"x":false},
      {"n":"Concierto de Fin de Año","e":1,"d":90,"s":"PUBLICADO","a":[1,2,7],"t":[["General",160000,500],["VIP",450000,100],["Platea",300000,150]],"v":0.05,"x":false},
      {"n":"Gala Sinfónica","e":2,"d":120,"s":"BORRADOR","a":[8],"t":[["General",95000,200]],"v":0,"x":false},
      {"n":"Rock al Parque","e":3,"d":30,"s":"CANCELADO","a":[3],"t":[["General",90000,250]],"v":0,"x":false}
    ]$j$;
    v_art       INTEGER[];
    v_esc       INTEGER[];
    v_usuarios  INTEGER[] := '{}';
    v_admin     INTEGER;
    v_user      INTEGER;
    v_ev        INTEGER;
    v_tipos     INTEGER[];
    v_tipo      INTEGER;
    v_tipo2     INTEGER;
    v_lineas    JSONB;
    v_orden_id  INTEGER;
    v_total     INTEGER;
    v_meta      INTEGER;
    v_vendidas  INTEGER;
    v_cant      INTEGER;
    v_intentos  INTEGER;
    v_posicion  INTEGER;
    v_gen       INTEGER;
    v_estado    VARCHAR;
    e           JSONB;
    t           JSONB;
    r           RECORD;
    i           INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM usuario WHERE correo LIKE '%@datos.soundpass.test') THEN
        RAISE NOTICE 'Los datos de prueba ya estan cargados; no se hace nada.';
        RETURN;
    END IF;
    PERFORM setseed(0.42);   -- mismos datos en cada carga

    -- Usuarios: un ADMIN (quien registra los check-in) y 40 clientes
    INSERT INTO usuario (nombre, apellido, correo, contrasena_hash, rol)
    VALUES ('Admin', 'Datos', 'admin@datos.soundpass.test', v_hash, 'ADMIN')
    RETURNING id_usuario INTO v_admin;
    FOR i IN 1..40 LOOP
        INSERT INTO usuario (nombre, apellido, correo, contrasena_hash)
        VALUES (v_nombres[1 + (i * 7) % 20], v_apellidos[1 + (i * 11) % 20],
                'cliente' || i || '@datos.soundpass.test', v_hash)
        RETURNING id_usuario INTO v_user;
        v_usuarios := v_usuarios || v_user;
    END LOOP;

    -- Artistas y escenarios (el indice del arreglo es el que usan los eventos de v_def)
    WITH ins AS (
        INSERT INTO artista (nombre_artistico, pais, genero) VALUES
            ('Aurora Nocturna', 'Colombia', 'Pop'),
            ('Los Ritmos del Sur', 'Colombia', 'Salsa'),
            ('Voltaje', 'Argentina', 'Rock'),
            ('Marea Alta', 'Chile', 'Indie'),
            ('Cumbia Real', 'Colombia', 'Cumbia'),
            ('DJ Eclipse', 'México', 'Electrónica'),
            ('La Banda del Sol', 'México', 'Pop'),
            ('Rosa Negra', 'España', 'Flamenco'),
            ('Trópico Beat', 'Brasil', 'Reggaetón'),
            ('Acordes Urbanos', 'Colombia', 'Hip Hop')
        RETURNING id_artista)
    SELECT array_agg(id_artista ORDER BY id_artista) INTO v_art FROM ins;

    WITH ins AS (
        INSERT INTO escenario (nombre, ciudad, direccion, capacidad) VALUES
            ('Movistar Arena', 'Bogotá', 'Calle 63 #59A-06', 14000),
            ('Teatro Metropolitano', 'Medellín', 'Calle 41 #57-30', 1700),
            ('Coliseo del Pacífico', 'Cali', 'Carrera 5 #34-20', 6000),
            ('Teatro Adolfo Mejía', 'Cartagena', 'Plaza de la Merced', 1200),
            ('Plaza Mayor', 'Medellín', 'Calle 41 #55-80', 4000)
        RETURNING id_escenario)
    SELECT array_agg(id_escenario ORDER BY id_escenario) INTO v_esc FROM ins;

    -- Eventos, tipos de entrada, compras, cancelaciones, asistentes y check-ins
    FOR e IN SELECT value FROM jsonb_array_elements(v_def) LOOP
        INSERT INTO evento (id_escenario, nombre, fecha_inicio, fecha_fin, estado)
        VALUES (v_esc[(e ->> 'e')::INTEGER], e ->> 'n',
                date_trunc('day', now()) + (e ->> 'd')::INTEGER * INTERVAL '1 day' + INTERVAL '20 hours',
                date_trunc('day', now()) + (e ->> 'd')::INTEGER * INTERVAL '1 day' + INTERVAL '23 hours',
                CASE WHEN e ->> 's' = 'BORRADOR' THEN 'BORRADOR' ELSE 'PUBLICADO' END)
        RETURNING id_evento INTO v_ev;

        v_posicion := 1;
        FOR i IN SELECT value::INTEGER FROM jsonb_array_elements_text(e -> 'a') LOOP
            INSERT INTO evento_artista (id_evento, id_artista, orden_presentacion)
            VALUES (v_ev, v_art[i], v_posicion);
            v_posicion := v_posicion + 1;
        END LOOP;

        v_total := 0;
        v_tipos := '{}';
        FOR t IN SELECT value FROM jsonb_array_elements(e -> 't') LOOP
            INSERT INTO tipo_entrada (id_evento, nombre, precio, cupo)
            VALUES (v_ev, t ->> 0, (t ->> 1)::NUMERIC, (t ->> 2)::INTEGER)
            RETURNING id_tipo_entrada INTO v_tipo;
            v_tipos := v_tipos || v_tipo;
            v_total := v_total + (t ->> 2)::INTEGER;
            IF e ->> 's' <> 'BORRADOR' THEN
                CALL sp_generar_entradas(v_tipo, (t ->> 2)::INTEGER, v_gen);
            END IF;
        END LOOP;

        -- Compras: ordenes de 1 a 4 entradas (a veces con dos tipos) hasta llegar a la venta objetivo
        v_meta := floor((e ->> 'v')::NUMERIC * v_total);
        v_vendidas := 0;
        v_intentos := 0;
        WHILE v_vendidas < v_meta AND v_intentos < 3000 LOOP
            v_intentos := v_intentos + 1;
            v_tipo := v_tipos[1 + floor(power(random(), 1.7) * array_length(v_tipos, 1))::INTEGER];
            v_cant := 1 + floor(random() * 4)::INTEGER;
            v_user := v_usuarios[1 + floor(random() * array_length(v_usuarios, 1))::INTEGER];
            v_lineas := jsonb_build_array(jsonb_build_object('idTipoEntrada', v_tipo, 'cantidad', v_cant));
            IF array_length(v_tipos, 1) > 1 AND random() < 0.3 THEN
                v_tipo2 := v_tipos[1 + (array_position(v_tipos, v_tipo) % array_length(v_tipos, 1))];
                v_lineas := v_lineas || jsonb_build_object('idTipoEntrada', v_tipo2, 'cantidad', 1);
                v_cant := v_cant + 1;
            END IF;
            BEGIN
                SELECT f.orden_id INTO v_orden_id
                  FROM fn_comprar_entradas(v_user, 'datos-' || v_ev || '-' || v_intentos, v_lineas) f;
                v_vendidas := v_vendidas + v_cant;
            EXCEPTION WHEN SQLSTATE 'SP410' THEN
                NULL;   -- se agotaron las entradas de un tipo: se prueba con otra compra
            END;
        END LOOP;

        -- Cancelaciones: cerca del 8 % de las ordenes se reembolsan
        FOR r IN SELECT DISTINCT o.id_orden, o.id_usuario
                   FROM orden o
                   JOIN detalle_compra d ON d.id_orden = o.id_orden
                   JOIN tipo_entrada te ON te.id_tipo_entrada = d.id_tipo_entrada
                  WHERE te.id_evento = v_ev AND o.estado = 'PAGADA' AND random() < 0.08 LOOP
            CALL sp_cancelar_orden(r.id_orden, r.id_usuario, v_estado);
        END LOOP;

        -- Asistentes: en los eventos pasados casi todos los compradores registran asistente
        -- y la mayoria hace check-in; en los futuros solo algunos registran asistente
        FOR r IN SELECT en.id_entrada, en.codigo
                   FROM entrada en
                   JOIN tipo_entrada te ON te.id_tipo_entrada = en.id_tipo_entrada
                  WHERE te.id_evento = v_ev AND en.estado = 'VENDIDA' LOOP
            IF random() < (CASE WHEN (e ->> 'x')::BOOLEAN THEN 0.92 ELSE 0.30 END) THEN
                INSERT INTO asistente (id_entrada, nombre, apellido, documento, correo)
                VALUES (r.id_entrada,
                        v_nombres[1 + floor(random() * 20)::INTEGER],
                        v_apellidos[1 + floor(random() * 20)::INTEGER],
                        (10000000 + floor(random() * 90000000))::BIGINT::TEXT,
                        CASE WHEN random() < 0.5 THEN 'asistente' || r.id_entrada || '@datos.soundpass.test' END);
                IF (e ->> 'x')::BOOLEAN AND random() < 0.88 THEN
                    PERFORM fn_checkin(r.codigo, v_admin);
                END IF;
            END IF;
        END LOOP;

        UPDATE evento SET estado = e ->> 's' WHERE id_evento = v_ev;
    END LOOP;

    -- Fechas realistas: las ordenes se compran entre 2 y 60 dias antes del evento, los check-in llegan
    -- entre 45 minutos antes del inicio y 75 minutos despues, y los clientes se registran en los ultimos 120 dias
    UPDATE orden o
       SET fecha = LEAST(now() - INTERVAL '1 hour', x.inicio - (2 + random() * 58) * INTERVAL '1 day')
      FROM (SELECT DISTINCT d.id_orden, ev.fecha_inicio AS inicio
              FROM detalle_compra d
              JOIN tipo_entrada te ON te.id_tipo_entrada = d.id_tipo_entrada
              JOIN evento ev ON ev.id_evento = te.id_evento) x
     WHERE x.id_orden = o.id_orden AND o.clave_idempotencia LIKE 'datos-%';

    UPDATE asistente a
       SET fecha_checkin = ev.fecha_inicio - INTERVAL '45 minutes' + random() * INTERVAL '120 minutes'
      FROM entrada en
      JOIN tipo_entrada te ON te.id_tipo_entrada = en.id_tipo_entrada
      JOIN evento ev ON ev.id_evento = te.id_evento
     WHERE en.id_entrada = a.id_entrada AND a.fecha_checkin IS NOT NULL;

    UPDATE usuario SET fecha_registro = now() - random() * INTERVAL '120 days'
     WHERE correo LIKE '%@datos.soundpass.test';

    RAISE NOTICE 'Datos de prueba cargados: % ordenes, % entradas vendidas o usadas, % check-in.',
        (SELECT count(*) FROM orden WHERE clave_idempotencia LIKE 'datos-%'),
        (SELECT count(*) FROM entrada WHERE estado IN ('VENDIDA', 'USADA')),
        (SELECT count(*) FROM asistente WHERE fecha_checkin IS NOT NULL);
END
$datos$;
