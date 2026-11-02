-- =========================================================
-- Backfill (opcional, una sola vez): reconstruye en eventos_funnel el
-- resultado final de las compras de Android ya hechas ANTES de que
-- existiera el tracking del funnel.
--
-- Limitaciones, para que quede constancia:
--   - Solo Android: iOS todavía no verifica compras contra servidor (ver
--     nota al final de 005_premium_suscripcion_android.sql), así que no
--     hay ningún registro de quién pagó en iOS para poder recuperarlo.
--   - Solo el resultado final (compra confirmada o no), no los pasos de
--     antes (nunca se guardó si vio el paywall, qué plan tocó primero,
--     etc.) — eso solo existe a partir de ahora.
--
-- Es seguro ejecutar esto más de una vez: el "where not exists" evita
-- duplicar filas si ya se corrió antes.
-- =========================================================
insert into public.eventos_funnel (usuario_id, plataforma, evento, metadata, creado_en)
select
  c.usuario_id,
  'android',
  case
    when c.estado in ('activa', 'en_periodo_de_gracia', 'cancelada_con_acceso')
      then 'trial_confirmado'
    else 'compra_fallida'
  end,
  jsonb_build_object(
    'plan_id', c.plan_base_id,
    'producto_id', c.producto_id,
    'purchase_token', c.purchase_token,
    'estado', c.estado,
    'backfill', true
  ),
  c.creado_en
from public.compras_google_play c
where not exists (
  select 1 from public.eventos_funnel e
  where e.metadata ->> 'purchase_token' = c.purchase_token
);
