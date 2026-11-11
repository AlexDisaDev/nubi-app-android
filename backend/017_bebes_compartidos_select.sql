-- =========================================================
-- Arregla un hueco de 016_compartir_bebe.sql: puede_ver_bebe() ya
-- reconoce a los compartidos (bebes_compartidos) y las 13 tablas hijas
-- (registros, medidas, citas, diario, vacunas...) ya la usan, pero la
-- propia tabla "bebes" se quedó con su política de siempre
-- ("bebes_propios": usuario_id = auth.uid()), que NO pasa por
-- puede_ver_bebe(). Resultado: un invitado podía leer los registros de
-- un bebé compartido pero nunca la fila del propio bebé (nombre, fecha
-- de nacimiento) — select * from bebes le devolvía vacío.
--
-- Esto añade una política de SELECT más (aditiva, en paralelo a
-- "bebes_propios", que sigue mandando igual para el dueño en
-- insert/update/delete). Postgres combina varias políticas permisivas
-- del mismo comando con OR, así que esto solo AMPLIA quién puede leer
-- — nadie que ya podía leer o escribir deja de poder.
-- =========================================================
begin;

drop policy if exists "bebes_compartidos_select" on public.bebes;
create policy "bebes_compartidos_select" on public.bebes
  for select using (public.puede_ver_bebe(id));

commit;
