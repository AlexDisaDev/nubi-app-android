# Reglas de ProGuard/R8 para Nubi.
#
# IMPORTANTE: ahora mismo `isMinifyEnabled = false` en build.gradle.kts, así
# que estas reglas NO se están aplicando todavía. Están preparadas para
# cuando se active R8, que es lo deseable (AAB más pequeño y código
# ofuscado), pero activarlo exige probar la app compilada en release de
# verdad: R8 rompe en silencio las librerías que usan reflexión, y el fallo
# no aparece hasta que el usuario toca la función afectada.
#
# Para activarlo: poner isMinifyEnabled = true, compilar en release,
# instalar el AAB y probar al menos login, sincronización y compra. Si algo
# falla, el culpable casi siempre es una clase serializable sin regla.

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
