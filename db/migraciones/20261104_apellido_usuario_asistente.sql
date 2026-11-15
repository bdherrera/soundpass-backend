/*
Autor:       Brayan David Herrera Acero
Fecha:       2026-11-04
Descripcion: Migración manual para bases de datos ya creadas: separa el nombre
             en nombre y apellido en usuario y en asistente (docs/DECISIONES.md).
             Docker solo ejecuta db/init con el volumen vacío, así que en una base
             existente este script se aplica a mano, una vez y antes de desplegar
             la versión nueva de la aplicación (ddl-auto=validate falla si falta).
             Es idempotente: se puede repetir sin efecto.
Datos:       Los registros existentes parten el nombre en la primera palabra
             (nombre) y el resto (apellido). Si el nombre es una sola palabra,
             el apellido queda en '-' y se corrige a mano.
*/

BEGIN;

ALTER TABLE usuario   ADD COLUMN IF NOT EXISTS apellido VARCHAR(100);
ALTER TABLE asistente ADD COLUMN IF NOT EXISTS apellido VARCHAR(100);

UPDATE usuario
   SET apellido = CASE WHEN strpos(btrim(nombre), ' ') > 0
                       THEN btrim(substr(btrim(nombre), strpos(btrim(nombre), ' ') + 1))
                       ELSE '-' END,
       nombre   = split_part(btrim(nombre), ' ', 1)
 WHERE apellido IS NULL;

UPDATE asistente
   SET apellido = CASE WHEN strpos(btrim(nombre), ' ') > 0
                       THEN btrim(substr(btrim(nombre), strpos(btrim(nombre), ' ') + 1))
                       ELSE '-' END,
       nombre   = split_part(btrim(nombre), ' ', 1)
 WHERE apellido IS NULL;

ALTER TABLE usuario   ALTER COLUMN apellido SET NOT NULL;
ALTER TABLE asistente ALTER COLUMN apellido SET NOT NULL;

COMMIT;
