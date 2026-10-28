-- =========================================================
-- FASE 4 (backend): bebé compartido entre cuentas + plataforma de registro
--
-- DRAFT — todavía NO ejecutado en el proyecto de Supabase. Se guarda aquí
-- para revisión antes de correrlo en el panel, igual que 001 y 002.
--
-- Motivo: hasta ahora cada bebé tenía un único dueño (bebes.usuario_id).
-- Esto cubre dos casos nuevos:
--   1) Una pareja (p. ej. un iPhone y un Android) viendo y editando el
--      mismo bebé con dos cuentas distintas — lo que hoy en iOS resuelve
--      "Nubi compartido" vía CloudKit, y que Android no tiene todavía.
--   2) Saber desde qué app (iOS/Android) se dio de alta cada cuenta.
--
-- Tal y como ya dejó anotado 002_datos_bebe.sql: "el día que añadamos
-- compartir entre padres, se toca puede_ver_bebe() y una tabla nueva.
-- Ninguna otra política hay que tocar." Es justo lo que hace este script:
-- ni una sola política de las 11 tablas existentes se modifica.
-- =========================================================
begin;

-- ---------- 1) Plataforma de registro ----------
alter table public.perfiles
  add column if not exists plataforma text check (plataforma in ('ios','android'));

-- El cliente manda la plataforma en las opciones de alta (auth.signUp /
-- signInWithIdToken "data": {"plataforma": "ios"}), y aquí se copia al
-- crear el perfil. Si no llega (cuentas ya existentes, o un flujo que no
-- la mande), se queda en null — no rompe nada.
create or replace function public.gestionar_nuevo_usuario()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.perfiles (id, email, fecha_corte_prueba, plataforma)
  values (
    new.id,
    new.email,
    now() + interval '7 days',
    new.raw_user_meta_data ->> 'plataforma'
  );
  return new;
end;
$$;

-- ---------- 2) Bebé compartido ----------
create table if not exists public.bebes_compartidos (
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  usuario_id uuid not null references auth.users(id) on delete cascade,
  invitado_en timestamptz not null default now(),
  primary key (bebe_id, usuario_id)
);

alter table public.bebes_compartidos enable row level security;

-- Cada participante ve su propia fila (para saber a qué bebés tiene
-- acceso compartido); el dueño del bebé ve todas las suyas (para
-- gestionar quién está invitado).
drop policy if exists "bebes_compartidos_visibles" on public.bebes_compartidos;
create policy "bebes_compartidos_visibles" on public.bebes_compartidos
  for select using (
    usuario_id = auth.uid()
    or exists (select 1 from public.bebes b where b.id = bebe_id and b.usuario_id = auth.uid())
  );

-- Solo el dueño del bebé puede quitar a alguien (el propio participante
-- también puede borrarse a sí mismo para "salir" del bebé compartido).
drop policy if exists "bebes_compartidos_borrar" on public.bebes_compartidos;
create policy "bebes_compartidos_borrar" on public.bebes_compartidos
  for delete using (
    usuario_id = auth.uid()
    or exists (select 1 from public.bebes b where b.id = bebe_id and b.usuario_id = auth.uid())
  );
-- Sin política de insert para el cliente a propósito: solo se entra por
-- la función de abajo (atómica, con token), nunca escribiendo la tabla
-- a mano desde la app.

-- ---------- 3) puede_ver_bebe(): único sitio que cambia ----------
create or replace function public.puede_ver_bebe(p_bebe_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.bebes b
    where b.id = p_bebe_id and b.usuario_id = auth.uid()
  ) or exists (
    select 1 from public.bebes_compartidos c
    where c.bebe_id = p_bebe_id and c.usuario_id = auth.uid()
  );
$$;

-- ---------- 4) Invitaciones (código corto, igual de patrón que gift_codes) ----------
create table if not exists public.invitaciones_bebe (
  token text primary key default encode(gen_random_bytes(6), 'base64'),
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  creado_por uuid not null references auth.users(id) on delete cascade,
  creado_en timestamptz not null default now(),
  caduca_en timestamptz not null default (now() + interval '7 days'),
  usado boolean not null default false,
  usado_por uuid references auth.users(id)
);

alter table public.invitaciones_bebe enable row level security;

drop policy if exists "invitaciones_bebe_propias" on public.invitaciones_bebe;
create policy "invitaciones_bebe_propias" on public.invitaciones_bebe
  for select using (creado_por = auth.uid());
-- Insert/update solo vía las funciones de abajo (security definer).

create or replace function public.crear_invitacion_bebe(p_bebe_id uuid)
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_token text;
begin
  if not exists (select 1 from public.bebes where id = p_bebe_id and usuario_id = auth.uid()) then
    raise exception 'no_eres_el_dueño';
  end if;

  insert into public.invitaciones_bebe (bebe_id, creado_por)
  values (p_bebe_id, auth.uid())
  returning token into v_token;

  return v_token;
end;
$$;

grant execute on function public.crear_invitacion_bebe(uuid) to authenticated;

create or replace function public.aceptar_invitacion_bebe(p_token text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_inv record;
begin
  select * into v_inv from public.invitaciones_bebe where token = p_token for update;

  if v_inv is null then
    raise exception 'invitacion_invalida';
  end if;
  if v_inv.usado then
    raise exception 'invitacion_ya_usada';
  end if;
  if v_inv.caduca_en < now() then
    raise exception 'invitacion_caducada';
  end if;
  if v_inv.creado_por = auth.uid() then
    raise exception 'no_puedes_aceptar_tu_propia_invitacion';
  end if;

  update public.invitaciones_bebe
    set usado = true, usado_por = auth.uid()
    where token = p_token;

  insert into public.bebes_compartidos (bebe_id, usuario_id)
  values (v_inv.bebe_id, auth.uid())
  on conflict (bebe_id, usuario_id) do nothing;

  return v_inv.bebe_id;
end;
$$;

grant execute on function public.aceptar_invitacion_bebe(text) to authenticated;

commit;

-- =========================================================
-- PENDIENTE, fuera de este script:
--
-- 1. Storage: el bucket "fotos-comidas" sigue pendiente (ver nota al
--    final de 002). Se vuelve indispensable con esta fase: las fotos de
--    Alimentación complementaria de iOS hoy van como [Data] embebido en
--    el JSON de iCloud (JPEG en base64, no una ruta), así que migrarlas
--    exige subir cada foto al bucket y sustituir el dato por su ruta.
--
-- 2. Esto da acceso compartido en Supabase, pero la UI de "invitar a
--    alguien" hay que construirla en las dos apps: Android no tiene hoy
--    ningún concepto de bebé compartido, e iOS tiene que cambiar su
--    "Nubi compartido" (CloudKit) por este flujo (crear_invitacion_bebe /
--    aceptar_invitacion_bebe).
-- =========================================================
