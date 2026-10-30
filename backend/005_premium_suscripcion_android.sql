-- =========================================================
-- FASE 5 (backend): premium vía suscripción real de Google Play
--
-- DRAFT — todavía NO ejecutado en el proyecto de Supabase. Se guarda aquí
-- para revisión antes de correrlo en el panel, igual que 002/003.
--
-- Contexto: Android pasa a pedir tarjeta desde el principio (trial nativo
-- de 7 días de Play Billing, "tal cual como iOS"), en vez del plazo de
-- gracia silencioso que daba fecha_corte_prueba sin pedir nada. La compra
-- se verifica en un Edge Function (backend/edge-functions/
-- verificar-compra-android) que llama a la Google Play Developer API con
-- una cuenta de servicio y, SOLO si Google confirma que la suscripción
-- está activa, llama a la función de abajo CON LA SERVICE_ROLE KEY — el
-- cliente nunca puede llamarla directamente con datos inventados.
-- =========================================================
begin;

-- Registro de cada compra de Android verificada, por purchase_token.
-- Sirve para no reprocesar el mismo token dos veces y para depurar.
create table if not exists public.compras_google_play (
  purchase_token text primary key,
  usuario_id uuid not null references auth.users(id) on delete cascade,
  producto_id text not null,
  plan_base_id text,
  estado text not null check (estado in (
    'activa','en_periodo_de_gracia','cancelada_con_acceso',
    'pausada','en_espera','pendiente','expirada'
  )),
  expira_en timestamptz,
  reconocida boolean not null default false,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);

create index if not exists idx_compras_google_play_usuario
  on public.compras_google_play(usuario_id);

alter table public.compras_google_play enable row level security;

drop policy if exists "compras_google_play_select_propio" on public.compras_google_play;
create policy "compras_google_play_select_propio"
  on public.compras_google_play for select
  using (auth.uid() = usuario_id);
-- A propósito SIN política de insert/update para 'authenticated': solo
-- escribe aquí el Edge Function, con la service_role key (que ignora RLS).

-- Activa (o renueva) el premium a partir de una compra de Google Play ya
-- verificada contra la API de Google. A propósito SIN "grant ... to
-- authenticated" más abajo: el cliente nunca puede llamarla con un
-- purchase_token inventado, solo el Edge Function con la service_role key.
create or replace function public.activar_premium_por_compra_google(
  p_usuario_id uuid,
  p_purchase_token text,
  p_producto_id text,
  p_plan_base_id text,
  p_estado text,
  p_expira_en timestamptz
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.compras_google_play
    (purchase_token, usuario_id, producto_id, plan_base_id, estado, expira_en)
  values
    (p_purchase_token, p_usuario_id, p_producto_id, p_plan_base_id, p_estado, p_expira_en)
  on conflict (purchase_token) do update
    set estado = excluded.estado,
        expira_en = excluded.expira_en,
        actualizado_en = now();

  -- Solo concede acceso si Google dice que sigue con acceso (activa, en
  -- periodo de gracia, o cancelada pero todavía no caducada). Un estado
  -- como 'pendiente' o 'expirada' no toca premium_hasta a propósito: no
  -- se rebaja aquí — esta función está pensada para justo después de una
  -- compra nueva, no para procesar caducidades (eso le toca a un futuro
  -- webhook de Real-time Developer Notifications, ver nota al final).
  if p_estado in ('activa', 'en_periodo_de_gracia', 'cancelada_con_acceso') then
    update public.perfiles
      set premium_hasta = greatest(coalesce(premium_hasta, p_expira_en), p_expira_en),
          premium_fuente = 'suscripcion',
          actualizado_en = now()
      where id = p_usuario_id;
  end if;
end;
$$;

revoke all on function public.activar_premium_por_compra_google(
  uuid, text, text, text, text, timestamptz
) from public, authenticated, anon;

-- Marca una compra como reconocida (acknowledged) ante Google. La llama
-- el mismo Edge Function tras confirmar que el acknowledge tuvo éxito.
create or replace function public.marcar_compra_reconocida(p_purchase_token text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  update public.compras_google_play
    set reconocida = true, actualizado_en = now()
    where purchase_token = p_purchase_token;
end;
$$;

revoke all on function public.marcar_compra_reconocida(text)
  from public, authenticated, anon;

commit;

-- =========================================================
-- PENDIENTE, fuera de este script:
--
-- 1. Real-time Developer Notifications (RTDN): para que una cancelación,
--    renovación o impago se reflejen SIN que el usuario tenga que abrir
--    la app (hoy premium_hasta solo se actualiza cuando se llama al Edge
--    Function, es decir, justo tras completar una compra). Necesita un
--    topic de Pub/Sub en Google Cloud + otro Edge Function que lo
--    escuche. No es necesario para probar el flujo de compra, pero sí
--    antes de lanzar de verdad: si no, una cancelación o un impago real
--    solo se detectaría la próxima vez que el usuario comprara algo, no
--    cuando realmente deja de pagar.
--
-- 2. Esta fase es solo para Android. iOS sigue con su propio mecanismo
--    (AppTransaction + iCloud), sin tocar premium_fuente='suscripcion'
--    todavía — el día que iOS también verifique en servidor, puede
--    reusar esta misma tabla/función con su propio Edge Function.
-- =========================================================
