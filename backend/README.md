# Backend (Supabase)

Scripts SQL del proyecto de Supabase `iroyjlpicrcjrnjdthsp`, el mismo que usan la web y los códigos de regalo.

Se guardan aquí para tener constancia de qué se ha ejecutado y en qué orden. **No se aplican solos**: hay que pegarlos en el SQL Editor del panel de Supabase.

| Script | Estado | Qué hace |
|---|---|---|
| `001_cuenta_y_premium.sql` | ✅ Ejecutado 21-10-2026 | Tabla `perfiles`, RLS, trigger de alta con 7 días de prueba, `puede_registrar()` y `canjear_codigo_regalo()`. Añade `redeemed_by` a `gift_codes`. |
| `002_datos_bebe.sql` | ⬜ Pendiente | Los datos del bebé (registros, medicinas, crecimiento, citas, vacunas, diario, banco de leche, alimentación, enfermedades) con RLS. |
| `004_eliminar_cuenta.sql` | ⬜ Pendiente | Función `eliminar_mi_cuenta()`: el usuario borra su cuenta y todos sus datos desde iOS (Ajustes → Eliminar cuenta). |

## Antes de ejecutar cualquier script

Exporta a CSV las tablas que toque desde el Table Editor. Es base de datos de producción: ya hay clientes con códigos de regalo comprados.

## Decisiones que conviene recordar

- **iOS no usa esto todavía.** La app de iOS sigue con su mecanismo local (`AppTransaction` + iCloud para el premium, y CloudKit para los datos del bebé). Este backend es, de momento, solo para Android.
- **Los valores de texto imitan a iOS.** Los tipos de registro (`siesta`, `baño`…), categorías (`Fruta`, `Lácteo`…) y almacenamiento (`Frigorífico`) conservan exactamente el mismo texto que el `rawValue` de los `enum` de Swift, tildes y mayúsculas incluidas, para que los datos sean intercambiables si algún día iOS también se migra.
- **Los `id` los pone el cliente**, como en iOS (`UUID()`), para que un registro creado sin conexión conserve su identidad al subirlo.
- **Compartir entre padres** (que en iOS existe vía CKShare) no está montado aquí todavía, pero el diseño lo contempla: todas las políticas pasan por `puede_ver_bebe()`, así que se añade en un solo sitio.
