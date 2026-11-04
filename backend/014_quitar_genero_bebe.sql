-- =========================================================
-- Revierte 013_genero_bebe.sql: se decidió no pedir el género del
-- bebé, así que se quita la columna antes de que ningún cliente
-- (iOS ni Android) llegue a escribir en ella de verdad.
-- =========================================================
begin;

alter table public.bebes
  drop column if exists genero;

commit;
