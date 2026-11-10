-- =========================================================
-- Foto de perfil del bebé (de momento solo la escribe/lee iOS — ver
-- SincronizadorSupabase.swift del repo de iOS).
--
-- Va en su PROPIA tabla, no en una columna de "bebes": Android sincroniza
-- esa tabla con select(*) y decodifica cada fila directamente a su data
-- class Bebe (ver Sincronizador.kt), que no tiene (todavía) este campo.
-- Añadir una columna nueva ahí arriesgaba romper esa decodificación en
-- Android sin que hiciera falta para nada — con una tabla aparte, Android
-- ni se entera de que existe.
--
-- Guardada como base64 en una columna de texto, no en Supabase Storage:
-- es una sola foto de perfil por bebé (se sobrescribe, no se acumula) y
-- pequeña (redimensionada a 400px antes de subirla), así que no vale la
-- pena la complejidad de un bucket todavía. Si esto cambiara (más fotos,
-- más grandes), se migraría a Storage como ya se documentó para
-- comidas_complementarias en 002_datos_bebe.sql.
-- =========================================================
begin;

create table if not exists public.fotos_bebe (
  bebe_id uuid primary key references public.bebes(id) on delete cascade,
  foto_base64 text,
  actualizado_en timestamptz not null default now()
);

alter table public.fotos_bebe enable row level security;

drop policy if exists "fotos_bebe_del_bebe" on public.fotos_bebe;
create policy "fotos_bebe_del_bebe" on public.fotos_bebe
  for all using (public.puede_ver_bebe(bebe_id))
  with check (public.puede_ver_bebe(bebe_id));

commit;
