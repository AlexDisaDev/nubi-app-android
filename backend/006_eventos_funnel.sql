-- Eventos del embudo (funnel) de conversión del paywall — común a Android e
-- iOS. Pensada para que Alejandro pueda añadir o quitar eventos y variables
-- sin tener que modificar el esquema cada vez:
--   - "evento" es texto libre (no un enum fijo), así que un evento nuevo
--     (ej. "precio_cambiado_tester") se puede empezar a mandar sin migrar
--     nada aquí.
--   - "metadata" guarda cualquier dato extra en JSON (plan, precio, mensaje
--     de error, etc.), también sin tocar columnas.
--
-- Eventos sugeridos para arrancar (se pueden ampliar luego libremente):
--   paywall_mostrado, plan_seleccionado, trial_iniciado,
--   compra_completada, compra_fallida, paywall_cerrado_sin_comprar,
--   restaurar_compra_pulsado

create table if not exists public.eventos_funnel (
    id uuid primary key default gen_random_uuid(),
    usuario_id uuid references auth.users(id) on delete set null,
    dispositivo_id text, -- id anónimo local, para eventos antes de iniciar sesión
    plataforma text not null check (plataforma in ('android', 'ios')),
    evento text not null,
    metadata jsonb not null default '{}'::jsonb,
    creado_en timestamptz not null default now()
);

create index if not exists eventos_funnel_evento_idx on public.eventos_funnel (evento);
create index if not exists eventos_funnel_plataforma_idx on public.eventos_funnel (plataforma);
create index if not exists eventos_funnel_usuario_idx on public.eventos_funnel (usuario_id);
create index if not exists eventos_funnel_creado_en_idx on public.eventos_funnel (creado_en);

alter table public.eventos_funnel enable row level security;

-- Un usuario autenticado solo puede insertar SUS propios eventos (o eventos
-- sin usuario_id). No hay política de SELECT a propósito: así, con la
-- publishable key, nadie puede leer los eventos de otros usuarios desde la
-- app. Las estadísticas del dashboard se leerán más adelante a través de
-- una vista agregada y anónima, con su propia política de lectura.
create policy "insertar_propios_eventos"
    on public.eventos_funnel
    for insert
    to authenticated
    with check (usuario_id = auth.uid() or usuario_id is null);

-- Permite registrar eventos antes de iniciar sesión (ej. en una pantalla
-- previa al login), siempre sin usuario_id.
create policy "insertar_eventos_anonimos"
    on public.eventos_funnel
    for insert
    to anon
    with check (usuario_id is null);
