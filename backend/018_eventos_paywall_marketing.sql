-- 018: eventos del paywall pedidos por marketing (nov 2026).
--
-- Desde esta versión las apps registran:
--   paywall_viewed, paywall_closed, plan_selected (metadata plan_id),
--   paywall_cta_tapped (plan_id, con_prueba), purchase_started,
--   purchase_completed, purchase_cancelled, purchase_failed (motivo),
--   restore_tapped, restore_completed.
-- Los nombres antiguos (paywall_mostrado, plan_seleccionado, trial_iniciado,
-- trial_confirmado, compra_fallida, restaurar_compra_pulsado) se quedan en la
-- tabla como histórico. vista_funnel_resumen no filtra por nombre, así que
-- recoge los nuevos sin cambios. vista_funnel_detalle sí: se amplía para
-- desglosar plan_id y motivo también con los nombres nuevos.

create or replace view public.vista_funnel_detalle as
select
  e.evento,
  case
    when e.evento in ('compra_fallida', 'purchase_failed')
      then coalesce(e.metadata ->> 'motivo', 'desconocido')
    when e.evento in ('plan_seleccionado', 'plan_selected', 'paywall_cta_tapped',
                      'purchase_started', 'purchase_completed', 'purchase_cancelled')
      then coalesce(nullif(e.metadata ->> 'plan_id', ''), 'desconocido')
    else null
  end as detalle,
  coalesce(e.plataforma, p.plataforma, 'desconocida') as plataforma,
  count(*) as total
from public.eventos_funnel e
left join public.perfiles p on p.id = e.usuario_id
where e.evento in ('compra_fallida', 'plan_seleccionado',
                   'purchase_failed', 'plan_selected', 'paywall_cta_tapped',
                   'purchase_started', 'purchase_completed', 'purchase_cancelled')
  and not coalesce(p.es_prueba, false)
  and not exists (select 1 from public.dispositivos_prueba dp
                  where dp.dispositivo_id = e.dispositivo_id)
group by 1, 2, 3;
