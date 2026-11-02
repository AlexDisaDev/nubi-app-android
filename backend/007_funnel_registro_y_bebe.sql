-- =========================================================
-- Registra "registro_completado" y "bebe_creado" en eventos_funnel desde
-- el propio servidor, en vez de desde la app.
--
-- Por qué aquí y no en el cliente: el trigger on_auth_user_created (ver
-- 001_cuenta_y_premium.sql) y un insert en "bebes" solo pueden pasar UNA
-- vez por usuario/bebé, pase lo que pase en la app (un bug de cliente, una
-- reinstalación a medias, una versión vieja sin el SDK todavía). Es más
-- robusto que cualquier comprobación en Kotlin/Swift, y automáticamente
-- vale igual para Android e iOS sin tocar código de la app.
--
-- La contrapartida: desde un trigger de base de datos no se sabe en qué
-- plataforma estaba el usuario, así que estos dos eventos concretos se
-- insertan con plataforma = null. El resto de eventos del funnel (los que
-- sí vienen del cliente) siguen mandando 'android' u 'ios' como siempre.
-- =========================================================
begin;

alter table public.eventos_funnel drop constraint if exists eventos_funnel_plataforma_check;
alter table public.eventos_funnel add constraint eventos_funnel_plataforma_check
  check (plataforma is null or plataforma in ('android', 'ios'));
alter table public.eventos_funnel alter column plataforma drop not null;

-- ---------- registro_completado ----------
create or replace function public.gestionar_nuevo_usuario()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.perfiles (id, email, fecha_corte_prueba)
  values (new.id, new.email, now() + interval '7 days');

  insert into public.eventos_funnel (usuario_id, plataforma, evento)
  values (new.id, null, 'registro_completado');

  return new;
end;
$$;
-- (el trigger on_auth_user_created ya apunta a esta función, no hace falta recrearlo)

-- ---------- bebe_creado ----------
create or replace function public.registrar_evento_bebe_creado()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.eventos_funnel (usuario_id, plataforma, evento, metadata)
  values (new.usuario_id, null, 'bebe_creado', jsonb_build_object('bebe_id', new.id));
  return new;
end;
$$;

drop trigger if exists on_bebe_creado on public.bebes;
create trigger on_bebe_creado
  after insert on public.bebes
  for each row execute function public.registrar_evento_bebe_creado();

commit;
