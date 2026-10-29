-- 004_eliminar_cuenta.sql
-- Permite que el propio usuario elimine su cuenta y todos sus datos desde la app
-- (requisito de Apple, guía 5.1.1(v)). Lo llama iOS con rpc("eliminar_mi_cuenta").
--
-- Qué borra, todo por el id del usuario que hace la llamada (auth.uid()):
--   * sus bebés y, en cascada, todos sus registros, medicinas, medidas, citas,
--     vacunas, diario, banco de leche, comidas y enfermedades
--   * sus accesos a bebés compartidos y las invitaciones que creó
--   * su perfil (perfiles)
--   * el usuario en auth.users (identidades y sesiones se van en cascada)
--
-- Qué NO borra: el código de regalo que canjeó sigue marcado como usado
-- (solo se quita la referencia al usuario, para que no se pueda reutilizar).
--
-- OJO: gift_codes.redeemed_by e invitaciones_bebe.usado_por referencian a
-- auth.users SIN "on delete cascade"; sin el paso 1 el borrado fallaría para
-- quien hubiera canjeado un código.
--
-- Antes de ejecutar: es producción. Pégalo en el SQL Editor del panel de Supabase.

create or replace function public.eliminar_mi_cuenta()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'No autenticado' using errcode = '28000';
  end if;

  -- 1) Referencias sin cascada: se anulan para que no bloqueen el borrado.
  update public.gift_codes set redeemed_by = null where redeemed_by = v_uid;

  if to_regclass('public.invitaciones_bebe') is not null then
    execute 'update public.invitaciones_bebe set usado_por = null where usado_por = $1'
      using v_uid;
  end if;

  -- 2) Datos del usuario (explícito, no solo por cascada).
  delete from public.bebes where usuario_id = v_uid;

  if to_regclass('public.bebes_compartidos') is not null then
    execute 'delete from public.bebes_compartidos where usuario_id = $1' using v_uid;
  end if;

  delete from public.perfiles where id = v_uid;

  -- 3) La cuenta en sí.
  delete from auth.users where id = v_uid;
end;
$$;

-- Solo usuarios con sesión; nunca anónimos ni público.
revoke all on function public.eliminar_mi_cuenta() from public, anon;
grant execute on function public.eliminar_mi_cuenta() to authenticated;
