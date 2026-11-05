/*
Autor:       Brayan David Herrera Acero
Fecha:       2026-11-04
Descripcion: Consultas de los reportes de SoundPass (PostgreSQL). Las explica
             docs/REPORTES_SQL.md. Son de solo lectura: se pueden ejecutar en
             cualquier momento sin modificar datos.
Uso:         psql -U <usuario> -d <base> -f db/reportes/reportes.sql
             Para repetir una sola, copia su bloque (R1 a R15).
Convencion:  una entrada cuenta como vendida si esta VENDIDA o USADA; las CANCELADA
             se reembolsaron. Los ingresos suman solo ordenes PAGADA.
Parametro:   los reportes de un evento usan 'Aurora Nocturna en vivo'; cambia el
             nombre por el del evento que quieras consultar.
Cambios:     20261104- version inicial.
*/

-- ============================================================================
-- R1. Resumen de ventas de un evento por tipo de entrada
--     (es la consulta de GET /api/v1/eventos/{id}/ventas)
-- ============================================================================
SELECT *
  FROM fn_resumen_ventas_evento(
           (SELECT id_evento FROM evento WHERE nombre = 'Aurora Nocturna en vivo'));

-- ============================================================================
-- R2. Ventas, ocupacion e ingresos de todos los eventos
-- ============================================================================
WITH por_tipo AS (
    SELECT t.id_evento, t.id_tipo_entrada, t.cupo,
           count(e.id_entrada) FILTER (WHERE e.estado IN ('VENDIDA', 'USADA')) AS vendidas
      FROM tipo_entrada t
      LEFT JOIN entrada e ON e.id_tipo_entrada = t.id_tipo_entrada
     GROUP BY t.id_evento, t.id_tipo_entrada, t.cupo
), ingresos AS (
    SELECT t.id_evento, sum(d.subtotal) AS ingresos
      FROM detalle_compra d
      JOIN orden o ON o.id_orden = d.id_orden
      JOIN tipo_entrada t ON t.id_tipo_entrada = d.id_tipo_entrada
     WHERE o.estado = 'PAGADA'
     GROUP BY t.id_evento
)
SELECT ev.id_evento, ev.nombre, ev.estado,
       sc.nombre AS escenario, sc.ciudad,
       sum(p.cupo)      AS cupo,
       sum(p.vendidas)  AS vendidas,
       round(100.0 * sum(p.vendidas) / NULLIF(sum(p.cupo), 0), 1) AS ocupacion_pct,
       COALESCE(i.ingresos, 0) AS ingresos
  FROM evento ev
  JOIN escenario sc ON sc.id_escenario = ev.id_escenario
  LEFT JOIN por_tipo p ON p.id_evento = ev.id_evento
  LEFT JOIN ingresos i ON i.id_evento = ev.id_evento
 GROUP BY ev.id_evento, ev.nombre, ev.estado, sc.nombre, sc.ciudad, i.ingresos
 ORDER BY ingresos DESC, ev.id_evento;

-- ============================================================================
-- R3. Ingresos por mes: ordenes, ingresos y ticket promedio
-- ============================================================================
SELECT date_trunc('month', o.fecha)::date AS mes,
       count(*)                          AS ordenes,
       sum(v.total)                      AS ingresos,
       round(avg(v.total), 0)            AS ticket_promedio
  FROM orden o
  JOIN v_orden_total v ON v.id_orden = o.id_orden
 WHERE o.estado = 'PAGADA'
 GROUP BY 1
 ORDER BY 1;

-- ============================================================================
-- R4. Ranking de eventos por ingresos dentro de cada ciudad (funcion ventana)
-- ============================================================================
WITH ingresos AS (
    SELECT t.id_evento, sum(d.subtotal) AS ingresos
      FROM detalle_compra d
      JOIN orden o ON o.id_orden = d.id_orden
      JOIN tipo_entrada t ON t.id_tipo_entrada = d.id_tipo_entrada
     WHERE o.estado = 'PAGADA'
     GROUP BY t.id_evento
)
SELECT sc.ciudad, ev.nombre AS evento, i.ingresos,
       rank() OVER (PARTITION BY sc.ciudad ORDER BY i.ingresos DESC) AS puesto_en_ciudad
  FROM ingresos i
  JOIN evento ev ON ev.id_evento = i.id_evento
  JOIN escenario sc ON sc.id_escenario = ev.id_escenario
 ORDER BY sc.ciudad, puesto_en_ciudad;

-- ============================================================================
-- R5. Ingresos y entradas por ciudad y escenario, con subtotales (ROLLUP)
-- ============================================================================
SELECT COALESCE(sc.ciudad, 'TOTAL')                       AS ciudad,
       COALESCE(sc.nombre, CASE WHEN sc.ciudad IS NULL THEN '' ELSE 'subtotal ciudad' END) AS escenario,
       count(DISTINCT o.id_orden)                         AS ordenes,
       sum(d.cantidad)                                    AS entradas,
       sum(d.subtotal)                                    AS ingresos
  FROM detalle_compra d
  JOIN orden o ON o.id_orden = d.id_orden
  JOIN tipo_entrada t ON t.id_tipo_entrada = d.id_tipo_entrada
  JOIN evento ev ON ev.id_evento = t.id_evento
  JOIN escenario sc ON sc.id_escenario = ev.id_escenario
 WHERE o.estado = 'PAGADA'
 GROUP BY ROLLUP (sc.ciudad, sc.nombre)
 ORDER BY sc.ciudad NULLS LAST, sc.nombre NULLS LAST;

-- ============================================================================
-- R6. Mezcla de ventas por nombre de tipo de entrada (participacion en ingresos)
-- ============================================================================
SELECT t.nombre AS tipo_entrada,
       sum(d.cantidad) AS entradas,
       sum(d.subtotal) AS ingresos,
       round(100.0 * sum(d.subtotal) / sum(sum(d.subtotal)) OVER (), 1) AS participacion_pct
  FROM detalle_compra d
  JOIN orden o ON o.id_orden = d.id_orden
  JOIN tipo_entrada t ON t.id_tipo_entrada = d.id_tipo_entrada
 WHERE o.estado = 'PAGADA'
 GROUP BY t.nombre
 ORDER BY ingresos DESC;

-- ============================================================================
-- R7. Los 10 clientes que mas gastan
-- ============================================================================
SELECT u.id_usuario,
       u.nombre || ' ' || u.apellido AS cliente,
       u.correo,
       count(*)     AS ordenes,
       sum(v.total) AS gasto
  FROM v_orden_total v
  JOIN usuario u ON u.id_usuario = v.id_usuario
 WHERE v.estado = 'PAGADA'
 GROUP BY u.id_usuario, u.nombre, u.apellido, u.correo
 ORDER BY gasto DESC
 LIMIT 10;

-- ============================================================================
-- R8. Asistencia por evento finalizado: check-in frente a entradas sin usar
-- ============================================================================
SELECT ev.nombre AS evento,
       count(*) FILTER (WHERE e.estado = 'USADA')   AS asistieron,
       count(*) FILTER (WHERE e.estado = 'VENDIDA') AS no_asistieron,
       round(100.0 * count(*) FILTER (WHERE e.estado = 'USADA')
             / NULLIF(count(*) FILTER (WHERE e.estado IN ('USADA', 'VENDIDA')), 0), 1) AS asistencia_pct
  FROM entrada e
  JOIN tipo_entrada t ON t.id_tipo_entrada = e.id_tipo_entrada
  JOIN evento ev ON ev.id_evento = t.id_evento
 WHERE ev.estado = 'FINALIZADO'
 GROUP BY ev.id_evento, ev.nombre
 ORDER BY asistencia_pct DESC;

-- ============================================================================
-- R9. Ordenes por estado: monto cobrado, reembolsado y cancelado
-- ============================================================================
SELECT o.estado,
       count(*)                  AS ordenes,
       COALESCE(sum(v.total), 0) AS monto,
       round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS pct_de_ordenes
  FROM orden o
  JOIN v_orden_total v ON v.id_orden = o.id_orden
 GROUP BY o.estado
 ORDER BY ordenes DESC;

-- ============================================================================
-- R10. Disponibilidad actual de un evento por tipo de entrada
--      (es la consulta de GET /api/v1/eventos/{id}/disponibilidad)
-- ============================================================================
SELECT t.id_tipo_entrada, t.nombre, t.precio, t.cupo,
       count(e.id_entrada) AS disponibles
  FROM tipo_entrada t
  LEFT JOIN entrada e ON e.id_tipo_entrada = t.id_tipo_entrada AND e.estado = 'DISPONIBLE'
 WHERE t.id_evento = (SELECT id_evento FROM evento WHERE nombre = 'Pop Latino Tour')
 GROUP BY t.id_tipo_entrada, t.nombre, t.precio, t.cupo
 ORDER BY t.id_tipo_entrada;

-- ============================================================================
-- R11. Rendimiento por artista: eventos y entradas vendidas de sus eventos
--      (en eventos con varios artistas, las ventas se cuentan completas para cada uno)
-- ============================================================================
SELECT a.nombre_artistico AS artista, a.genero,
       count(DISTINCT ea.id_evento) AS eventos,
       count(e.id_entrada) FILTER (WHERE e.estado IN ('VENDIDA', 'USADA')) AS entradas_vendidas
  FROM artista a
  JOIN evento_artista ea ON ea.id_artista = a.id_artista
  JOIN tipo_entrada t ON t.id_evento = ea.id_evento
  LEFT JOIN entrada e ON e.id_tipo_entrada = t.id_tipo_entrada
 GROUP BY a.id_artista, a.nombre_artistico, a.genero
 ORDER BY entradas_vendidas DESC, artista;

-- ============================================================================
-- R12. Auditoria: transiciones de estado registradas por los triggers
-- ============================================================================
SELECT tabla,
       COALESCE(estado_anterior, '(alta)') AS desde,
       estado_nuevo                        AS hasta,
       count(*)                            AS cambios
  FROM auditoria_estado
 GROUP BY tabla, estado_anterior, estado_nuevo
 ORDER BY tabla, cambios DESC;

-- ============================================================================
-- R13. Alerta comercial: eventos publicados con menos de 30 % de ocupacion
-- ============================================================================
SELECT ev.nombre AS evento,
       (ev.fecha_inicio::date - current_date) AS dias_para_el_evento,
       count(e.id_entrada) AS cupo,
       count(e.id_entrada) FILTER (WHERE e.estado IN ('VENDIDA', 'USADA')) AS vendidas,
       round(100.0 * count(e.id_entrada) FILTER (WHERE e.estado IN ('VENDIDA', 'USADA'))
             / NULLIF(count(e.id_entrada), 0), 1) AS ocupacion_pct
  FROM evento ev
  JOIN tipo_entrada t ON t.id_evento = ev.id_evento
  JOIN entrada e ON e.id_tipo_entrada = t.id_tipo_entrada
 WHERE ev.estado = 'PUBLICADO' AND ev.fecha_inicio > now()
 GROUP BY ev.id_evento, ev.nombre, ev.fecha_inicio
HAVING 100.0 * count(e.id_entrada) FILTER (WHERE e.estado IN ('VENDIDA', 'USADA'))
       / NULLIF(count(e.id_entrada), 0) < 30
 ORDER BY ocupacion_pct;

-- ============================================================================
-- R14. Llegada de asistentes por hora en un evento
-- ============================================================================
SELECT date_trunc('hour', a.fecha_checkin) AS hora,
       count(*)                            AS check_ins
  FROM asistente a
  JOIN entrada e ON e.id_entrada = a.id_entrada
  JOIN tipo_entrada t ON t.id_tipo_entrada = e.id_tipo_entrada
 WHERE t.id_evento = (SELECT id_evento FROM evento WHERE nombre = 'Aurora Nocturna en vivo')
   AND a.fecha_checkin IS NOT NULL
 GROUP BY 1
 ORDER BY 1;

-- ============================================================================
-- R15. Consistencia de la linea de compra: cada linea debe tener tantas entradas
--      como su cantidad. Devuelve las lineas con diferencia (debe salir vacio).
-- ============================================================================
SELECT d.id_orden, d.id_tipo_entrada, d.cantidad AS cantidad_linea,
       count(e.id_entrada) AS entradas_asignadas
  FROM detalle_compra d
  LEFT JOIN entrada e ON e.id_orden = d.id_orden AND e.id_tipo_entrada = d.id_tipo_entrada
 GROUP BY d.id_orden, d.id_tipo_entrada, d.cantidad
HAVING d.cantidad <> count(e.id_entrada)
 ORDER BY d.id_orden;
