# Nubi — Android

Conversión a Android (Kotlin + Jetpack Compose) de Nubi, la app de seguimiento de bebé para iOS. Mismo backend (Supabase — incluidos los códigos de regalo, compartido con la web), mismos colores, mismas funciones. Se reconstruye nativo porque SwiftUI, StoreKit, iCloud y UserNotifications de iOS no tienen equivalente directo en Android.

## Estado actual: esqueleto + backend conectado (fase 1 en marcha)

El proyecto abre con Material 3 y ya tiene login real contra Supabase (email con enlace mágico), comprobación de acceso (`puede_registrar()`) y canjeo de código de regalo (`canjear_codigo_regalo()`) de extremo a extremo. Todavía NO incluye:

- Los colores/tipografía reales de Nubi (de `Theme.swift` en el proyecto de iOS) — ahora mismo son los morados por defecto de Material 3.
- Ninguna de las pantallas reales (Día, Alimentación, Crecimiento, Salud, Ajustes…) — la pantalla tras el login es solo de prueba para el backend.
- Compras (Google Play Billing).
- Notificaciones.
- El icono real de la app (hay un marcador de posición, una "N" morada).

No se ha compilado en un entorno con SDK de Android instalado (este repo se generó sin Android Studio disponible) — al abrirlo es normal que Android Studio pida instalar o actualizar el SDK, el AGP, Gradle o alguna versión de dependencia de Supabase/Ktor.

## Supabase

- Proyecto: `iroyjlpicrcjrnjdthsp` (mismo que usan la web y los códigos de regalo — tabla `gift_codes`).
- Cliente en `app/src/main/java/app/nubi/data/Supabase.kt`, con la clave publicable (segura de compartir, no es la `service_role`).
- Backend (fase 1): tabla `perfiles`, RLS, trigger de alta con 7 días de prueba, `puede_registrar()` y `canjear_codigo_regalo()` — ver el script SQL ya ejecutado en el proyecto de Supabase.
- Requiere en el dashboard: Authentication → Providers → Email activado (ya lo está) y Authentication → URL Configuration → Redirect URLs con `app.nubi://login-callback` añadida.

## Hoja de ruta

1. **Backend** ✅ cuenta + estado de suscripción/plazo de gracia en Supabase, compartido entre iOS, Android y la web — sustituye el mecanismo local que usa iOS ahora (AppTransaction + iCloud). Login y comprobación de acceso ya conectados en Android.
2. **Sistema de diseño**: portar `Theme.swift` y `Componentes.swift` a Compose (colores, tipografía, componentes base, icono real).
3. **Pantallas**, empezando por el tab "Día" (`VistaLinea.swift`), que es la que más lógica de bloqueo tiene.
4. Resto de pantallas, compras (Play Billing), notificaciones (ongoing notification como base; "Live Updates" de Android 16 más adelante, cuando esté más extendido).

## Requisitos para abrir el proyecto

- Android Studio reciente.
- JDK 17.
- Conexión a internet la primera vez (para descargar dependencias de Gradle/AndroidX).

## Estructura

```
app/src/main/java/app/nubi/
├── MainActivity.kt
├── NubiApplication.kt
└── ui/theme/        (Color.kt, Theme.kt, Type.kt — provisionales)
```
