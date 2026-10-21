-- =========================================================
-- FASE 3 (backend): datos del bebé en Supabase
--
-- Traducción de Modelos.swift del proyecto iOS. Los valores de texto
-- (tipos de registro, categorías, almacenamiento…) conservan EXACTAMENTE
-- el mismo rawValue que usa iOS, incluidas tildes y mayúsculas, para que
-- los datos sean intercambiables si algún día iOS también se migra aquí.
--
-- Los id son uuid y los pone el cliente, igual que en iOS (UUID()), para
-- que un registro creado sin conexión conserve su identidad al subir.
--
-- Seguridad: todo cuelga de "bebes". Un usuario solo ve las filas de sus
-- propios bebés, y eso se comprueba con la función puede_ver_bebe(), que
-- está aparte a propósito: el día que añadamos compartir entre padres,
-- se toca esa función y no las 11 políticas.
-- =========================================================
begin;

-- ---------- Bebé ----------
create table if not exists public.bebes (
  id uuid primary key,
  usuario_id uuid not null references auth.users(id) on delete cascade,
  nombre text not null,
  fecha_nacimiento timestamptz not null,
  semanas_prematuro int not null default 0,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);

create index if not exists idx_bebes_usuario on public.bebes(usuario_id);

-- ¿Puede el usuario actual ver/editar este bebé?
-- Hoy: solo si es suyo. Mañana: aquí se añade el compartido.
create or replace function public.puede_ver_bebe(p_bebe_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public, pg_temp
as $$
  select exists (
    select 1 from public.bebes b
    where b.id = p_bebe_id and b.usuario_id = auth.uid()
  );
$$;

grant execute on function public.puede_ver_bebe(uuid) to authenticated;

-- ---------- Registros (siestas, tomas, pañales…) ----------
create table if not exists public.registros (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  tipo text not null check (tipo in
    ('siesta','noche','biberon','pecho','panal','despertar','medicina','baño')),
  inicio timestamptz not null,
  fin timestamptz,
  nota text not null default '',
  lado text check (lado in ('izquierda','derecha')),
  cantidad_biberon double precision,
  unidad_biberon text check (unidad_biberon in ('ml','oz')),
  panal_pis boolean,
  panal_caca boolean,
  tipo_leche text check (tipo_leche in ('materna','formula')),
  bano_contento boolean,
  lugar_sueno text check (lugar_sueno in
    ('brazos','pecho','biberon','cuna','carrito','coche')),
  actualizado_en timestamptz not null default now()
);

-- La pantalla "Día" pide siempre los registros de un bebé por fecha.
create index if not exists idx_registros_bebe_inicio
  on public.registros(bebe_id, inicio desc);

-- ---------- Medicinas ----------
create table if not exists public.medicinas (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  fecha_toma timestamptz not null,
  nombre text not null default '',
  nota text not null default '',
  hora_siguiente timestamptz,
  actualizado_en timestamptz not null default now()
);

create index if not exists idx_medicinas_bebe on public.medicinas(bebe_id, fecha_toma desc);

-- ---------- Crecimiento ----------
create table if not exists public.medidas (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  fecha timestamptz not null,
  peso_kg double precision,
  altura_cm double precision,
  perimetro_cm double precision,
  nota text not null default '',
  actualizado_en timestamptz not null default now()
);

create index if not exists idx_medidas_bebe on public.medidas(bebe_id, fecha desc);

-- ---------- Citas ----------
create table if not exists public.citas (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  tipo text not null default 'pediatra'
    check (tipo in ('pediatra','enfermeria','otra')),
  titulo text not null default '',
  fecha timestamptz not null,
  lugar text not null default '',
  nota text not null default '',
  recordar boolean not null default true,
  actualizado_en timestamptz not null default now()
);

create index if not exists idx_citas_bebe on public.citas(bebe_id, fecha);

-- ---------- Vacunas del calendario oficial ----------
-- Una fila por vacuna marcada. "clave" es el identificador que usa
-- CalendarioVacunal.swift en iOS.
create table if not exists public.vacunas_estado (
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  clave text not null,
  puesta boolean not null default false,
  fecha timestamptz,
  nota text not null default '',
  actualizado_en timestamptz not null default now(),
  primary key (bebe_id, clave)
);

-- ---------- Vacunas añadidas por el usuario ----------
create table if not exists public.vacunas_grupos (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  etiqueta text not null,
  meses double precision not null,
  actualizado_en timestamptz not null default now()
);

create table if not exists public.vacunas_propias (
  id uuid primary key,
  grupo_id uuid not null references public.vacunas_grupos(id) on delete cascade,
  nombre text not null,
  puesta boolean not null default false,
  fecha timestamptz,
  actualizado_en timestamptz not null default now()
);

-- ---------- Diario ----------
create table if not exists public.diario (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  fecha timestamptz not null,
  titulo text not null default '',
  texto text not null default '',
  hito boolean not null default false,
  actualizado_en timestamptz not null default now()
);

create index if not exists idx_diario_bebe on public.diario(bebe_id, fecha desc);

-- ---------- Banco de leche ----------
-- OJO: 'Frigorífico' y 'Congelador' van con mayúscula y tilde porque es
-- literalmente el rawValue que iOS ya tiene guardado en su JSON.
create table if not exists public.extracciones_leche (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  fecha timestamptz not null,
  cantidad_ml double precision not null,
  almacenamiento text not null default 'Congelador'
    check (almacenamiento in ('Frigorífico','Congelador')),
  nota text not null default '',
  actualizado_en timestamptz not null default now()
);

create table if not exists public.usos_leche (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  fecha timestamptz not null,
  cantidad_ml double precision not null,
  nota text not null default '',
  actualizado_en timestamptz not null default now()
);

-- ---------- Alimentación complementaria ----------
-- Las fotos NO van aquí: iOS guarda los JPEG dentro del propio objeto, y
-- meter binarios en una fila de base de datos es mala idea. Aquí se guardan
-- las rutas dentro del bucket de Supabase Storage (ver nota al final).
create table if not exists public.comidas_complementarias (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  fecha timestamptz not null,
  nombre text not null default '',
  categoria text not null default 'Otro'
    check (categoria in ('Fruta','Verdura','Proteína','Cereal','Lácteo','Otro')),
  gusto text check (gusto in ('leGusto','noLeGusto')),
  nota text not null default '',
  fotos text[] not null default '{}',
  actualizado_en timestamptz not null default now()
);

-- ---------- Enfermedades ----------
create table if not exists public.enfermedades (
  id uuid primary key,
  bebe_id uuid not null references public.bebes(id) on delete cascade,
  nombre text not null default '',
  fecha_inicio timestamptz not null,
  fecha_fin timestamptz,
  medicacion text not null default '',
  notas text not null default '',
  actualizado_en timestamptz not null default now()
);

-- =========================================================
-- Seguridad a nivel de fila
-- =========================================================
alter table public.bebes enable row level security;

drop policy if exists "bebes_propios" on public.bebes;
create policy "bebes_propios" on public.bebes
  for all using (usuario_id = auth.uid())
  with check (usuario_id = auth.uid());

-- Para el resto de tablas, la misma política repetida: se puede tocar la
-- fila si se puede ver su bebé. vacunas_propias va por su grupo.
do $$
declare
  t text;
begin
  foreach t in array array[
    'registros','medicinas','medidas','citas','vacunas_estado',
    'vacunas_grupos','diario','extracciones_leche','usos_leche',
    'comidas_complementarias','enfermedades'
  ]
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists "%s_del_bebe" on public.%I', t, t);
    execute format(
      'create policy "%s_del_bebe" on public.%I for all
         using (public.puede_ver_bebe(bebe_id))
         with check (public.puede_ver_bebe(bebe_id))', t, t);
  end loop;
end $$;

alter table public.vacunas_propias enable row level security;

drop policy if exists "vacunas_propias_del_bebe" on public.vacunas_propias;
create policy "vacunas_propias_del_bebe" on public.vacunas_propias
  for all using (
    exists (
      select 1 from public.vacunas_grupos g
      where g.id = grupo_id and public.puede_ver_bebe(g.bebe_id)
    )
  )
  with check (
    exists (
      select 1 from public.vacunas_grupos g
      where g.id = grupo_id and public.puede_ver_bebe(g.bebe_id)
    )
  );

commit;

-- =========================================================
-- PENDIENTE, fuera de este script (hay que hacerlo en el panel):
--
-- 1. Storage: crear un bucket PRIVADO llamado "fotos-comidas" para las
--    fotos de la alimentación complementaria, con política de acceso por
--    usuario. Las rutas se guardan en comidas_complementarias.fotos.
--
-- 2. Compartir entre padres: cuando toque, se crea una tabla
--    bebes_compartidos (bebe_id, usuario_id) y se añade un OR dentro de
--    puede_ver_bebe(). Ninguna otra política hay que tocar.
-- =========================================================
