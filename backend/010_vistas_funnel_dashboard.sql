-- =========================================================
-- Más vistas agregadas de eventos_funnel para que el dashboard
-- (que se conecta con el acceso del propio lector, sujeto a RLS)
-- pueda leer totales sin tener permiso de SELECT sobre la tabla
-- en crudo eventos_funnel.
-- =========================================================

-- Totales por paso del embudo (todas las plataformas juntas)
create or replace view public.vista_funnel_por_evento as
select
  evento,
  count(*) as total_eventos,
  count(distinct usuario_id) as usuarios_unicos
from public.eventos_funnel
group by evento;

grant select on public.vista_funnel_por_evento to anon, authenticated;

-- Totales por paso del embudo, separado por plataforma
create or replace view public.vista_funnel_por_evento_plataforma as
select
  evento,
  coalesce(plataforma, 'servidor') as plataforma,
  count(*) as total_eventos,
  count(distinct usuario_id) as usuarios_unicos
from public.eventos_funnel
group by evento, coalesce(plataforma, 'servidor');

grant select on public.vista_funnel_por_evento_plataforma to anon, authenticated;

-- Motivos de compra fallida
create or replace view public.vista_funnel_motivos_fallo as
select
  coalesce(metadata ->> 'motivo', 'desconocido') as motivo,
  coalesce(plataforma, 'servidor') as plataforma,
  count(*) as total
from public.eventos_funnel
where evento = 'compra_fallida'
group by coalesce(metadata ->> 'motivo', 'desconocido'), coalesce(plataforma, 'servidor');

grant select on public.vista_funnel_motivos_fallo to anon, authenticated;

-- Popularidad de planes seleccionados
create or replace view public.vista_funnel_planes as
select
  coalesce(metadata ->> 'plan_id', 'desconocido') as plan_id,
  coalesce(plataforma, 'servidor') as plataforma,
  count(*) as total
from public.eventos_funnel
where evento = 'plan_seleccionado'
group by coalesce(metadata ->> 'plan_id', 'desconocido'), coalesce(plataforma, 'servidor');

grant select on public.vista_funnel_planes to anon, authenticated;
