-- =========================================================
-- Vista agregada de eventos_funnel para el dashboard de marketing.
--
-- Una vista normal en Postgres se ejecuta con los permisos de quien la
-- CREÓ (no de quien la consulta), así que aunque eventos_funnel no tenga
-- ninguna política de SELECT para anon/authenticated (a propósito, ver
-- 006_eventos_funnel.sql), esta vista sí puede leerla y devolver los
-- totales agregados con la clave pública.
-- =========================================================
create or replace view public.vista_funnel_resumen as
select
  evento,
  coalesce(plataforma, 'servidor') as plataforma,
  date_trunc('day', creado_en) as dia,
  count(*) as total_eventos,
  count(distinct usuario_id) as usuarios_unicos
from public.eventos_funnel
group by evento, coalesce(plataforma, 'servidor'), date_trunc('day', creado_en);

grant select on public.vista_funnel_resumen to anon, authenticated;
