# Nubi — Android

Conversión a Android (Kotlin + Jetpack Compose) de Nubi, la app de seguimiento de bebé para iOS. Mismo backend (Supabase — incluidos los códigos de regalo, compartido con la web), mismos colores, mismas funciones. Se reconstruye nativo porque SwiftUI, StoreKit, iCloud y UserNotifications de iOS no tienen equivalente directo en Android.

## Estado actual: esqueleto

Este primer commit solo deja el proyecto abriendo y mostrando una pantalla provisional con Material 3. Todavía NO incluye:

- Los colores/tipografía reales de Nubi (de `Theme.swift` en el proyecto de iOS) — ahora mismo son los morados por defecto de Material 3.
- Conexión con Supabase.
- Ninguna de las pantallas reales (Día, Alimentación, Crecimiento, Salud, Ajustes…).
- Compras (Google Play Billing).
- Notificaciones.
- El icono real de la app (hay un marcador de posición, una "N" morada).

No se ha compilado en un entorno con SDK de Android instalado (este repo se generó sin Android Studio disponible) — al abrirlo es normal que Android Studio pida instalar o actualizar el SDK, el AGP o la versión de Gradle.

## Hoja de ruta

1. **Backend**: cuenta ligera + estado de suscripción/plazo de gracia en Supabase, compartido entre iOS, Android y la web — sustituye el mecanismo local que usa iOS ahora (AppTransaction + iCloud).
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
