-- 019: contexto de los eventos del paywall (nov 2026).
--
-- Las apps añaden a los eventos del paywall:
--   origen               botón o pantalla desde la que se abrió
--                        (hoy_prediccion, linea_dias_anteriores, obligatorio…)
--   plan_preseleccionado plan marcado al abrir (solo en paywall_viewed)
--   con_prueba           si se ofrecía prueba gratis
--   moneda               EUR, USD…
--   segundos             segundos desde que se abrió el paywall
-- y a TODOS los eventos: version_app y pais.
--
-- Esta vista lo agrupa para el dashboard, con las mismas exclusiones que
-- las demás (perfiles es_prueba y dispositivos_prueba).

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
  ) as segundos_mediana
from public.eventos_funnel e
left join public.perfiles p on p.id = e.usuario_id
where e.evento in ('paywall_viewed', 'paywall_closed', 'plan_selected', 'paywall_cta_tapped',
                   'purchase_started', 'purchase_completed', 'purchase_cancelled',
                   'purchase_failed', 'restore_tapped', 'restore_completed')
  and not coalesce(p.es_prueba, false)
  and not exists (select 1 from public.dispositivos_prueba dp
                  where dp.dispositivo_id = e.dispositivo_id)
group by 1, 2, 3, 4, 5, 6, 7;

grant select on public.vista_paywall_contexto to anon, authenticated;
