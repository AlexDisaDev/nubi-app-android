-- =========================================================
-- Añade el género del bebé a public.bebes.
--
-- Aditivo y opcional a propósito: las filas que ya existen quedan con
-- genero = NULL, y los inserts de Android (que hoy no manda esta
-- columna) siguen funcionando igual que antes.
--
-- Nace en iOS (entrevista de alta por voz, rama feature/alta-bebe-por-voz
-- del repo nubiapp) junto con el enum GeneroBebe en Modelos.swift. Mismo
-- texto exacto que el rawValue de Swift, tildes incluidas, siguiendo la
-- convención del resto de este fichero (ver 002_datos_bebe.sql).
-- =========================================================
begin;

alter table public.bebes
  add column if not exists genero text check (genero in ('niño', 'niña'));

commit;
