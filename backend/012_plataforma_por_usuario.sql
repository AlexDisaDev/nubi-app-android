-- =========================================================
-- Saber de qué plataforma viene cada usuario, también en los eventos que
-- genera el servidor (registro_completado y bebe_creado).
--
-- EL PROBLEMA
-- Esos dos eventos los crean triggers dentro de Postgres. Un trigger no
-- sabe si el INSERT que lo disparó venía de un iPhone o de un Android: eso
-- se queda en el cliente HTTP y no llega a la base de datos. Por eso hasta
-- ahora se guardaban con plataforma nula.
--
-- LA SOLUCIÓN
-- Guardar la plataforma UNA VEZ por usuario, en perfiles.plataforma, y
-- resolverla desde ahí. Así:
--   - los eventos nuevos la llevan estampada,
--   - y los que ya existen la recuperan, porque las vistas la resuelven
--     mirando el perfil del usuario. No hay que reescribir el histórico.
--
-- La columna perfiles.plataforma ya estaba prevista en
-- 003_compartir_bebe_y_plataforma.sql, pero esa migración nunca llegó a
-- ejecutarse en este proyecto (comprobado: no existían ni la columna ni
-- bebes_compartidos ni invitaciones_bebe). Aquí se crea solo la parte de
-- plataforma; el resto de la 003 sigue pendiente aparte.
-- =========================================================

-- 1. La columna ------------------------------------------------------------
alter table public.perfiles
  add column if not exists plataforma text;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'perfiles_plataforma_check'
  ) then
    alter table public.perfiles
      add constraint perfiles_plataforma_check
      check (plataforma is null or plataforma in ('ios', 'android'));
  end if;
end $$;

-- 2. Cómo la escribe la app -------------------------------------------------
-- NO se abre una política de UPDATE sobre perfiles: en esa tabla viven
-- premium_hasta y premium_fuente, y con un UPDATE abierto cualquiera podría
-- regalarse premium editando su propia fila. En vez de eso, una función
-- security definer que solo puede tocar esta columna y solo del usuario que
-- llama.
create or replace function public.registrar_plataforma(p_plataforma text)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if p_plataforma is null or p_plataforma not in ('ios', 'android') then
    raise exception 'plataforma no válida: %', p_plataforma;
  end if;

  update public.perfiles
     set plataforma = p_plataforma,
         actualizado_en = now()
   where id = auth.uid()
     and plataforma is distinct from p_plataforma;
end;
$$;

revoke all on function public.registrar_plataforma(text) from public, anon;
grant execute on function public.registrar_plataforma(text) to authenticated;

-- 3. Los triggers la estampan cuando ya se conoce ---------------------------
create or replace function public.gestionar_nuevo_usuario()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_plataforma text;
begin
  -- Si el cliente la mandó en los metadatos del alta, se aprovecha. Si no,
  -- queda nula y la rellenará registrar_plataforma() en cuanto la app
  -- arranque; las vistas la resuelven igual.
  v_plataforma := nullif(new.raw_user_meta_data ->> 'plataforma', '');
  if v_plataforma is not null and v_plataforma not in ('ios', 'android') then
    v_plataforma := null;
  end if;

  insert into public.perfiles (id, email, fecha_corte_prueba, plataforma)
  values (new.id, new.email, now() + interval '7 days', v_plataforma);

  insert into public.eventos_funnel (usuario_id, plataforma, evento)
  values (new.id, v_plataforma, 'registro_completado');

  return new;
end;
$$;

create or replace function public.registrar_evento_bebe_creado()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_plataforma text;
begin
  select plataforma into v_plataforma
    from public.perfiles
   where id = new.usuario_id;

  insert into public.eventos_funnel (usuario_id, plataforma, evento, metadata)
  values (new.usuario_id, v_plataforma, 'bebe_creado',
          jsonb_build_object('bebe_id', new.id));
  return new;
end;
$$;

-- 4. Las vistas resuelven la plataforma que falte ---------------------------
-- coalesce en dos pasos: lo que trae el propio evento (la app lo manda) y,
-- si no trae nada, lo que diga el perfil del usuario. Esto arregla también
-- las filas ya guardadas, sin tocarlas.
create or replace view public.vista_funnel_resumen as
select
  e.evento,
  coalesce(e.plataforma, p.plataforma, 'desconocida') as plataforma,
  date_trunc('day', e.creado_en) as dia,
  count(*) as total_eventos,
  count(distinct e.usuario_id) as usuarios_unicos
from public.eventos_funnel e
left join public.perfiles p on p.id = e.usuario_id
group by 1, 2, 3;

grant select on public.vista_funnel_resumen to anon, authenticated;

create or replace view public.vista_funnel_detalle as
select
  e.evento,
  case
    when e.evento = 'compra_fallida' then coalesce(e.metadata ->> 'motivo', 'desconocido')
    when e.evento = 'plan_seleccionado' then coalesce(e.metadata ->> 'plan_id', 'desconocido')
  end as detalle,
  coalesce(e.plataforma, p.plataforma, 'desconocida') as plataforma,
  count(*) as total
from public.eventos_funnel e
left join public.perfiles p on p.id = e.usuario_id
where e.evento in ('compra_fallida', 'plan_seleccionado')
group by 1, 2, 3;

grant select on public.vista_funnel_detalle to anon, authenticated;
