-- 020: oferta del mensual tras cancelar el anual o el trimestral (nov 2026).
--
-- Eventos nuevos:
--   downsell_shown           tras cancelar la hoja de pago del anual o del
--                            trimestral, el paywall pasa a solo mensual
--                            (metadata plan_cancelado)
--   downsell_show_all_plans  pulsa "Ver todos los planes"
-- Y los eventos del paywall y de compra hechos en ese modo llevan
-- metadata modo = 'solo_mensual'.
--
-- vista_paywall_contexto incluye ahora estos eventos y una columna modo
-- (al final, para no cambiar las columnas que ya había).

create or replace view public.vista_paywall_contexto as
select
  e.evento,
  coalesce(e.metadata ->> 'origen', 'sin_dato') as origen,
  coalesce(e.metadata ->> 'plan_preseleccionado', e.metadata ->> 'plan_id', 'sin_dato') as plan,
  coalesce(e.metadata ->> 'con_prueba', 'sin_dato') as con_prueba,
  coalesce(e.metadata ->> 'version_app', 'sin_dato') as version_app,
  coalesce(e.metadata ->> 'pais', 'sin_dato') as pais,
  coalesce(e.plataforma, p.plataforma, 'desconocida') as plataforma,
  count(*) as total,
  count(distinct coalesce(e.usuario_id::text, e.dispositivo_id)) as personas,
  percentile_cont(0.5) within group (
    order by case when e.metadata ->> 'segundos' ~ '^[0-9]+$' then (e.metadata ->> 'segundos')::numeric end
  ) as segundos_mediana,
  coalesce(e.metadata ->> 'modo', 'normal') as modo
from public.eventos_funnel e
left join public.perfiles p on p.id = e.usuario_id
where e.evento in ('paywall_viewed', 'paywall_closed', 'plan_selected', 'paywall_cta_tapped',
                   'purchase_started', 'purchase_completed', 'purchase_cancelled',
                   'purchase_failed', 'restore_tapped', 'restore_completed',
                   'downsell_shown', 'downsell_show_all_plans')
  and not coalesce(p.es_prueba, false)
  and not exists (select 1 from public.dispositivos_prueba dp
                  where dp.dispositivo_id = e.dispositivo_id)
group by 1, 2, 3, 4, 5, 6, 7, coalesce(e.metadata ->> 'modo', 'normal');
