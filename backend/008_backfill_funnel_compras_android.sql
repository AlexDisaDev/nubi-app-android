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
-- SOBRE EL purchase_token: la versión anterior de este script lo copiaba
-- tal cual dentro de metadata. Era un error: el purchase_token es la
-- credencial con la que se consulta y reconoce una compra contra la API de
-- Google Play, y eventos_funnel es una tabla de analítica, no de pagos —
-- acaba leída por vistas agregadas y por el panel. Aquí se guarda solo un
-- hash (sha256, pgcrypto vive en el esquema extensions) que sirve igual para lo único que hacía falta: no duplicar
-- filas si este script se ejecuta dos veces. El token real sigue donde
-- tiene que estar, en compras_google_play.
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
    'compra_hash', encode(extensions.digest(c.purchase_token, 'sha256'), 'hex'),
    'estado', c.estado,
    'backfill', true
  ),
  c.creado_en
from public.compras_google_play c
where not exists (
  select 1 from public.eventos_funnel e
  where e.metadata ->> 'compra_hash' = encode(extensions.digest(c.purchase_token, 'sha256'), 'hex')
);
