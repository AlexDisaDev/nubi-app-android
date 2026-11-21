-- 021_historial_cambios.sql
-- Fase 0 de feature/ActSupabaseMobile: red de seguridad en el servidor.
--
-- Qué hace: antes de que una fila de datos del bebé se EDITE o se BORRE,
-- guarda una copia completa de cómo estaba en public.historial_cambios,
-- con qué cuenta lo hizo, desde qué cliente (iOS/Android) y cuándo.
--
-- Para qué sirve:
--   1. Recuperar cualquier dato borrado o pisado, sea por un bug o por
--      error de la persona (ya no hacen falta tablas de respaldo a mano
--      como respaldo_incidente_lara_20261112).
--   2. Saber exactamente qué pasó en el próximo incidente: qué fila,
--      qué cuenta y qué app la cambió.
--   3. En la fase 1, las apps leerán de aquí los borrados confirmados
--      (operacion = 'DELETE'), en vez de deducirlos porque una fila "no
--      vino" en la descarga.
--
-- Qué NO cambia: ninguna fila existente, ninguna política de las tablas de
-- datos, nada de lo que hacen las versiones ya publicadas de las apps.
-- Solo añade una tabla, una función, triggers e índices.
--
-- Garantía: si guardar el historial fallase por cualquier motivo, la
-- escritura de la persona sigue adelante igual (el error se traga con un
-- WARNING). El historial nunca puede bloquear un registro.
--
-- Privacidad (Eliminar cuenta): cuando se borra un bebé entero —es lo que
-- hace eliminar_mi_cuenta()— sus filas se van en cascada y NO se copian
-- aquí, porque el bebé ya no existe. Así, eliminar la cuenta sigue sin dejar
-- copia de sus datos. Lo que sí se guarda es lo que se borra o edita
-- mientras el bebé sigue existiendo.
--
-- No se registra fotos_bebe (la foto en base64 pesa mucho y no es un dato
-- que haya que reconstruir) ni los INSERT (la fila nueva ya está en su
-- tabla).

-- 1) Tabla ------------------------------------------------------------------

create table if not exists public.historial_cambios (
  id           bigint generated always as identity primary key,
  tabla        text        not null,
  fila_id      text        not null,
  bebe_id      uuid,
  operacion    text        not null check (operacion in ('UPDATE', 'DELETE')),
  fila_antes   jsonb       not null,
  fila_despues jsonb,
  usuario_id   uuid,
  cliente      text,
  en           timestamptz not null default now()
);

comment on table public.historial_cambios is
  'Copia de cada fila de datos del bebé antes de editarla o borrarla (021_historial_cambios.sql). Solo la escribe el trigger nubi_historial().';

create index if not exists idx_historial_bebe_en on public.historial_cambios (bebe_id, en);
create index if not exists idx_historial_fila on public.historial_cambios (tabla, fila_id, en);

alter table public.historial_cambios enable row level security;

-- Lectura: quien puede ver el bebé (dueño o invitado) puede ver su
-- historial — lo necesitarán las apps en la fase 1 para los borrados.
drop policy if exists historial_ver on public.historial_cambios;
create policy historial_ver on public.historial_cambios
  for select using (bebe_id is not null and public.puede_ver_bebe(bebe_id));

-- Escritura: nadie desde las apps. Solo el trigger (security definer).
revoke insert, update, delete on public.historial_cambios from anon, authenticated;

-- 2) Función del trigger --------------------------------------------------------

create or replace function public.nubi_historial()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_antes   jsonb := to_jsonb(OLD);
  v_despues jsonb;
  v_bebe    uuid;
  v_fila    text;
  v_cliente text;
begin
  if TG_OP = 'UPDATE' then
    v_despues := to_jsonb(NEW);
    -- Un upsert que no cambia nada (mismos datos) no es un cambio.
    if (v_antes - 'actualizado_en') = (v_despues - 'actualizado_en') then
      return null;
    end if;
  end if;

  -- A qué bebé pertenece la fila.
  if TG_TABLE_NAME = 'bebes' then
    v_bebe := (v_antes ->> 'id')::uuid;
  elsif TG_TABLE_NAME = 'vacunas_propias' then
    select g.bebe_id into v_bebe
      from public.vacunas_grupos g
     where g.id = (v_antes ->> 'grupo_id')::uuid;
  else
    v_bebe := (v_antes ->> 'bebe_id')::uuid;
  end if;

  -- Bebé ya borrado (eliminar cuenta, cascada): no se guarda copia.
  if v_bebe is null
     or not exists (select 1 from public.bebes b where b.id = v_bebe) then
    return null;
  end if;

  -- Clave de la fila: "id", salvo vacunas_estado, que es (bebe_id, clave).
  v_fila := coalesce(v_antes ->> 'id', (v_antes ->> 'bebe_id') || '|' || (v_antes ->> 'clave'));

  -- Cabecera que mandan las librerías de Supabase (supabase-swift /
  -- supabase-kt...): permite saber si el cambio vino de iOS o de Android.
  begin
    v_cliente := current_setting('request.headers', true)::json ->> 'x-client-info';
  exception when others then
    v_cliente := null;
  end;

  insert into public.historial_cambios
    (tabla, fila_id, bebe_id, operacion, fila_antes, fila_despues, usuario_id, cliente)
  values
    (TG_TABLE_NAME, v_fila, v_bebe, TG_OP, v_antes, v_despues, auth.uid(), v_cliente);

  return null;
exception when others then
  -- El historial nunca bloquea la escritura de la persona.
  raise warning 'nubi_historial (%): %', TG_TABLE_NAME, sqlerrm;
  return null;
end;
$$;

revoke all on function public.nubi_historial() from public, anon, authenticated;

-- 3) Triggers (AFTER: ven la fila ya con actualizado_en puesto por el servidor)

do $$
declare
  t text;
begin
  foreach t in array array[
    'bebes', 'registros', 'medidas', 'citas', 'diario',
    'vacunas_estado', 'vacunas_grupos', 'vacunas_propias',
    'extracciones_leche', 'usos_leche', 'comidas_complementarias',
    'medicinas', 'enfermedades'
  ] loop
    execute format('drop trigger if exists trg_historial on public.%I', t);
    execute format(
      'create trigger trg_historial after update or delete on public.%I
         for each row execute function public.nubi_historial()', t);
  end loop;
end;
$$;

-- 4) Índices para la descarga incremental de la fase 1
--    ("dame lo de este bebé cambiado desde X"). Solo aceleran consultas.

create index if not exists idx_registros_bebe_act on public.registros (bebe_id, actualizado_en);
create index if not exists idx_medidas_bebe_act on public.medidas (bebe_id, actualizado_en);
create index if not exists idx_citas_bebe_act on public.citas (bebe_id, actualizado_en);
create index if not exists idx_diario_bebe_act on public.diario (bebe_id, actualizado_en);
create index if not exists idx_vacunas_estado_bebe_act on public.vacunas_estado (bebe_id, actualizado_en);
create index if not exists idx_vacunas_grupos_bebe_act on public.vacunas_grupos (bebe_id, actualizado_en);
create index if not exists idx_vacunas_propias_grupo on public.vacunas_propias (grupo_id);
create index if not exists idx_extracciones_bebe_act on public.extracciones_leche (bebe_id, actualizado_en);
create index if not exists idx_usos_leche_bebe_act on public.usos_leche (bebe_id, actualizado_en);
create index if not exists idx_comidas_bebe_act on public.comidas_complementarias (bebe_id, actualizado_en);
create index if not exists idx_medicinas_bebe_act on public.medicinas (bebe_id, actualizado_en);
create index if not exists idx_enfermedades_bebe_act on public.enfermedades (bebe_id, actualizado_en);
