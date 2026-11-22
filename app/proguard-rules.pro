# Reglas de ProGuard/R8 para Nubi.
#
# R8 está ACTIVADO (isMinifyEnabled = true en build.gradle.kts) desde que
# Google Play avisó de que la ofuscación estaba al 0 % (umbral: 25 %).
#
# R8 rompe en silencio lo que usa reflexión, y el fallo no aparece hasta que
# se toca la función afectada. Antes de subir cada versión: compilar en
# release, instalarla y probar al menos login, sincronización, compartir y
# compra. Si algo falla, el culpable casi siempre es una clase serializable
# o una librería sin regla.
#
# Supabase (supabase-kt 3.6.0) no trae reglas propias en sus AAR, y Ktor
# carga cosas por ServiceLoader: por eso se conservan enteros más abajo. El
# resto (la app, Compose, AndroidX, Kotlin…) sí se ofusca, que es lo que
# mide Google Play.

# --- kotlinx.serialization -------------------------------------------------
# Los serializadores se generan en tiempo de compilación y se localizan por
# reflexión; sin esto, R8 los borra por "no usados" y la app peta al
# serializar con un SerializationException difícil de leer.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Las clases @Serializable propias de la app (modelos, DatosNubi,
# EventoFunnelPendiente, FilaEventoFunnel…) y sus serializadores.
-keep,includedescriptorclasses class app.nubi.**$$serializer { *; }
-keepclassmembers class app.nubi.** {
    *** Companion;
}
-keepclasseswithmembers class app.nubi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Supabase / Ktor -------------------------------------------------------
# El cliente de Supabase usa kotlinx.serialization y Ktor, que a su vez
# carga motores por servicio (ServiceLoader) y reflexión.
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# OkHttp (motor de Ktor en este proyecto)
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Google Play Billing ---------------------------------------------------
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# --- Coroutines ------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Enums de la app -------------------------------------------------------
# Algunos se guardan por su nombre y se recuperan con valueOf() (TipoRuido en
# ServicioRuido, Comunidad en PantallaSalud): se conservan sus nombres.
-keepclassmembers enum app.nubi.** { *; }
