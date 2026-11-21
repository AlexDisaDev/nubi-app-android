-- 022_estado_acceso_bebe.sql
-- Fase 1 de feature/ActSupabaseMobile.
--
-- Para qué: que una app pueda distinguir, sin ambigüedad, entre
--   (a) "he perdido el acceso a este bebé" (el dueño dejó de compartir), y
--   (b) "la sesión se ha caído y Supabase me devuelve 0 filas como si nada".
-- Con la RLS, los dos casos se ven igual desde fuera (una consulta vacía sin
-- error). Hasta ahora las apps interpretaban "vacío" como (a) y vaciaban el
-- móvil del invitado. Con esto solo se actúa si el servidor confirma que hay
-- sesión (autenticado = true) Y que esa cuenta ya no puede ver el bebé.
--
-- Solo añade una función. No cambia ninguna tabla ni ningún dato.
-- Las apps tienen que funcionar igual aunque esta función no exista todavía
-- (en ese caso no hacen nada).

create or replace function public.estado_acceso_bebe(p_bebe_id uuid)
returns json
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select json_build_object(
    'autenticado', auth.uid() is not null,
    'puede_ver',   auth.uid() is not null and public.puede_ver_bebe(p_bebe_id)
  );
$$;

revoke all on function public.estado_acceso_bebe(uuid) from public, anon;
grant execute on function public.estado_acceso_bebe(uuid) to authenticated;
