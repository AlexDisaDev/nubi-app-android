-- =========================================================
-- FASE 1: cuenta compartida + estado premium
-- Ejecutado en el proyecto de Supabase el 21-10-2026.
-- Se guarda aquí para dejar constancia de qué hay montado.
-- =========================================================
begin;

-- 1) Perfiles: uno por usuario de auth.users
create table if not exists public.perfiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  premium_hasta timestamptz,        -- null = nunca ha tenido premium
  premium_fuente text,              -- 'codigo_regalo' | 'suscripcion' | null
  fecha_corte_prueba timestamptz not null,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);

alter table public.perfiles enable row level security;

drop policy if exists "perfiles_select_propio" on public.perfiles;
create policy "perfiles_select_propio"
  on public.perfiles for select
  using (auth.uid() = id);
-- Sin política de UPDATE para el cliente a propósito: el premium solo
-- lo cambian las funciones de abajo, nunca el usuario directamente.

-- 2) Trigger: al registrarse, perfil con 7 días de prueba desde ahora
create or replace function public.gestionar_nuevo_usuario()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.perfiles (id, email, fecha_corte_prueba)
  values (new.id, new.email, now() + interval '7 days');
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.gestionar_nuevo_usuario();

-- 3) Columna nueva en gift_codes para saber qué cuenta canjeó el código
alter table public.gift_codes
  add column if not exists redeemed_by uuid references auth.users(id);

-- 4) ¿Puede usar la app ahora mismo?
create or replace function public.puede_registrar()
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  fila public.perfiles;
begin
  select * into fila from public.perfiles where id = auth.uid();
  if fila is null then
    return false;
  end if;
  return (fila.premium_hasta is not null and now() < fila.premium_hasta)
      or now() < fila.fecha_corte_prueba;
end;
$$;

grant execute on function public.puede_registrar() to authenticated;

-- 5) Canjear código de regalo (atómico, suma meses según plan_months)
create or replace function public.canjear_codigo_regalo(p_codigo text)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_code record;
  v_meses int;
  v_base timestamptz;
begin
  select * into v_code from public.gift_codes where code = p_codigo for update;

  if v_code is null then
    raise exception 'codigo_invalido';
  end if;

  if v_code.used then
    raise exception 'codigo_ya_usado';
  end if;

  v_meses := coalesce(v_code.plan_months,
                       case when v_code.plan_duration ilike '%año%' then 12 else 1 end);

  update public.gift_codes
    set used = true, used_at = now(), redeemed_by = auth.uid()
    where code = p_codigo;

  select greatest(coalesce(premium_hasta, now()), now())
    into v_base
    from public.perfiles where id = auth.uid();

  update public.perfiles
    set premium_hasta = v_base + (v_meses || ' months')::interval,
        premium_fuente = 'codigo_regalo',
        actualizado_en = now()
    where id = auth.uid();

  return true;
end;
$$;

grant execute on function public.canjear_codigo_regalo(text) to authenticated;

commit;
