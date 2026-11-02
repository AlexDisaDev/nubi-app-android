-- =========================================================
-- Simplifica las vistas del dashboard de marketing: de 5 a 2.
--
-- vista_funnel_resumen (ya existía) cubre casi todo. Las 4 vistas
-- creadas en 010 se sustituyen por una sola vista combinada
-- (vista_funnel_detalle) para "motivo de compra fallida" y
-- "plan elegido".
-- =========================================================

drop view if exists public.vista_funnel_por_evento;
drop view if exists public.vista_funnel_por_evento_plataforma;
drop view if exists public.vista_funnel_motivos_fallo;
drop view if exists public.vista_funnel_planes;

create or replace view public.vista_funnel_detalle as
select
  evento,
  case
    when evento = 'compra_fallida' then coalesce(metadata ->> 'motivo', 'desconocido')
    when evento = 'plan_seleccionado' then coalesce(metadata ->> 'plan_id', 'desconocido')
  end as detalle,
  coalesce(plataforma, 'servidor') as plataforma,
  count(*) as total
from public.eventos_funnel
where evento in ('compra_fallida', 'plan_seleccionado')
group by evento, 2, coalesce(plataforma, 'servidor');

grant select on public.vista_funnel_detalle to anon, authenticated;
