// Plugins declarados aquí (apply false) y aplicados en app/build.gradle.kts.
// Versiones alineadas con las que genera el asistente de proyecto nuevo de
// Android Studio, para no ir adivinando combinaciones compatibles.
//
// OJO: a partir de AGP 9 el plugin "org.jetbrains.kotlin.android" ya NO se
// declara — AGP trae Kotlin incorporado ("built-in Kotlin") y declararlo
// provoca el error "Cannot add extension with name 'kotlin'".
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
