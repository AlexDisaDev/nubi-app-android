# Backend (Supabase)

Scripts SQL del proyecto de Supabase `iroyjlpicrcjrnjdthsp`, el mismo que usan la web y los códigos de regalo.

Se guardan aquí para tener constancia de qué se ha ejecutado y en qué orden. **No se aplican solos**: hay que pegarlos en el SQL Editor del panel de Supabase.

| Script | Estado | Qué hace |
|---|---|---|
| `001_cuenta_y_premium.sql` | ✅ Ejecutado 21-10-2026 | Tabla `perfiles`, RLS, trigger de alta con 7 días de prueba, `puede_registrar()` y `canjear_codigo_regalo()`. Añade `redeemed_by` a `gift_codes`. |
| `002_datos_bebe.sql` | ⬜ Pendiente | Los datos del bebé (registros, medicinas, crecimiento, citas, vacunas, diario, banco de leche, alimentación, enfermedades) con RLS. |
| `004_eliminar_cuenta.sql` | ⬜ Pendiente | Función `eliminar_mi_cuenta()`: el usuario borra su cuenta y todos sus datos desde iOS (Ajustes → Eliminar cuenta). |
| `005_premium_suscripcion_android.sql` | ⬜ Pendiente | Tabla `compras_google_play` y las funciones `activar_premium_por_compra_google()` / `marcar_compra_reconocida()` que usa el Edge Function de abajo para activar premium tras una suscripción real de Android. |

## Edge Functions

| Función | Qué hace | Secretos que necesita |
|---|---|---|
| `verificar-compra-android` | La llama la app Android (Compras.kt) justo después de cada compra de Play Billing. Verifica la compra directamente contra la Google Play Developer API (nunca se fía de lo que diga el móvil) y, si es válida, llama a `activar_premium_por_compra_google()`. | `GOOGLE_SERVICE_ACCOUNT_JSON` — JSON de una cuenta de servicio de Google Cloud con acceso de la API de Android Publisher a este proyecto en Play Console. `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`SUPABASE_SERVICE_ROLE_KEY` los pone Supabase solo. |

### Cómo dar de alta la cuenta de servicio de Google (una sola vez)

1. En [Google Cloud Console](https://console.cloud.google.com), crea un proyecto nuevo (o usa uno existente) y entra en **IAM y administración → Cuentas de servicio → Crear cuenta de servicio**. Nombre sugerido: `nubi-play-verificacion`.
2. Tras crearla, en la pestaña **Claves** → **Agregar clave** → **Crear clave nueva** → tipo **JSON**. Descarga el archivo — es el que va en el secreto `GOOGLE_SERVICE_ACCOUNT_JSON` (el contenido completo del `.json`, tal cual).
3. En [Play Console](https://play.google.com/console) → **Configuración** → **Acceso a la API** → vincula el mismo proyecto de Google Cloud si no está ya vinculado → en la lista de cuentas de servicio debería aparecer la que acabas de crear → **Gestionar permisos de Play Console**.
4. Dale como mínimo el permiso **"Ver datos financieros, pedidos y gestión de cancelaciones"** (o el permiso de administrador de la app si prefieres no afinar). Sin esto, la API devuelve un 403 aunque la cuenta de servicio sea correcta.

### Cómo desplegar el Edge Function

Con la Supabase CLI instalada y logueada (`supabase login`), desde la raíz del proyecto:

```bash
supabase functions deploy verificar-compra-android --project-ref iroyjlpicrcjrnjdthsp
supabase secrets set GOOGLE_SERVICE_ACCOUNT_JSON="$(cat ruta/a/la-clave.json)" --project-ref iroyjlpicrcjrnjdthsp
```

(También se puede pegar el secreto a mano desde el panel: Edge Functions → la función → Secrets.)

## Antes de ejecutar cualquier script

Exporta a CSV las tablas que toque desde el Table Editor. Es base de datos de producción: ya hay clientes con códigos de regalo comprados.

## Decisiones que conviene recordar

- **iOS no usa esto todavía.** La app de iOS sigue con su mecanismo local (`AppTransaction` + iCloud para el premium, y CloudKit para los datos del bebé). Este backend es, de momento, solo para Android.
- **Los valores de texto imitan a iOS.** Los tipos de registro (`siesta`, `baño`…), categorías (`Fruta`, `Lácteo`…) y almacenamiento (`Frigorífico`) conservan exactamente el mismo texto que el `rawValue` de los `enum` de Swift, tildes y mayúsculas incluidas, para que los datos sean intercambiables si algún día iOS también se migra.
- **Los `id` los pone el cliente**, como en iOS (`UUID()`), para que un registro creado sin conexión conserve su identidad al subirlo.
- **Compartir entre padres** (que en iOS existe vía CKShare) no está montado aquí todavía, pero el diseño lo contempla: todas las políticas pasan por `puede_ver_bebe()`, así que se añade en un solo sitio.
