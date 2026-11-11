-- =========================================================
-- FASE: bebé compartido entre cuentas (Android <-> Android, y de puente
-- para Android <-> iPhone).
--
-- Esto es la parte 2-4 de 003_compartir_bebe_y_plataforma.sql (el DRAFT
-- que ya existía, nunca ejecutado) SIN su parte 1: la columna
-- perfiles.plataforma y el trigger gestionar_nuevo_usuario() ya se
-- hicieron en 012_plataforma_por_usuario.sql, con una versión mejor
-- (también deja constancia en eventos_funnel). Repetir esa parte aquí
-- pisaría esa mejora.
--
-- Qué hace:
--   1) Tabla bebes_compartidos: quién más (aparte del dueño) puede ver
--      y anotar un bebé.
--   2) puede_ver_bebe(): único sitio que cambia para que las 13 tablas
--      que ya la usan (registros, medidas, citas, diario, vacunas...)
--      empiecen a reconocer también a los compartidos, sin tocar NINGUNA
--      de sus políticas.
--   3) invitaciones_bebe + sus dos funciones (crear/aceptar), mismo
--      patrón que gift_codes/canjear_codigo_regalo.
--
-- eliminar_mi_cuenta() (004_eliminar_cuenta.sql) YA tiene el código
-- defensivo para limpiar estas dos tablas (comprobado con
-- to_regclass antes de este script) — no hace falta tocarla.
--
-- Quién puede usar esto hoy: solo Android, con su propia pantalla de
-- "invitar a mi pareja" (pendiente de construir en la app). El "Nubi
-- compartido" de iOS sigue exactamente igual, por CloudKit — no se
-- toca nada de Sincronizador.swift. Portar esto a iOS (para que un
-- iPhone pueda unirse a un bebé de Android, o que un Android se una al
-- de un iPhone) es trabajo de app en una fase posterior.
-- =========================================================
begin;

-- ---------- 1) Bebé compartido ----------
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

-- ---------- 2) puede_ver_bebe(): único sitio que cambia ----------
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

-- ---------- 3) Invitaciones (código corto, mismo patrón que gift_codes) ----------
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

revoke all on function public.crear_invitacion_bebe(uuid) from public, anon;
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

revoke all on function public.aceptar_invitacion_bebe(text) from public, anon;
grant execute on function public.aceptar_invitacion_bebe(text) to authenticated;

commit;

-- =========================================================
-- PENDIENTE, fuera de este script:
--
-- 1. UI de "invitar a mi pareja" en Android (pantalla de Ajustes):
--    crear_invitacion_bebe -> enlace -> compartir -> la otra persona
--    toca el enlace -> aceptar_invitacion_bebe.
--
-- 2. La pasarela web (nubi-web/invitar.html) necesita entender también
--    un enlace con "?token=..." (hoy solo sabe de "?u=..." de CloudKit),
--    y abrir "nubi://unirme?token=..." igual que ya hace con "?u=...".
--    Android necesita un intent-filter nuevo para "nubi://unirme" (hoy
--    solo tiene "app.nubi://login-callback").
--
-- 3. Portar esto a iOS es una fase aparte y más delicada: hoy iOS solo
--    SUBE un espejo a Supabase (SincronizadorSupabase.swift) como
--    backup/recuperación, nunca descarga en vivo ni fusiona cambios de
--    otra cuenta. Un iPhone que se una a un bebé de Android, o que
--    comparta el suyo con un Android, necesita que ese bebé concreto
--    pase a tratar Supabase como la verdad en vivo (no solo copia de
--    seguridad) — eso sí toca Sincronizador.swift / Almacen, aunque el
--    "Nubi compartido" de iPhone a iPhone por CloudKit se queda exactamente
--    igual que está.
--
-- 4. Storage de fotos de comidas (pendiente de 002, independiente de
--    todo esto).
-- =========================================================
