plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.nubi"
    compileSdk = 37

    defaultConfig {
        // El "application ID" es el identificador que ve Play Console, y es
        // distinto del namespace/paquete Kotlin de arriba (que puede quedarse
        // como "app.nubi" sin problema). Tiene que coincidir exactamente con
        // el nombre de paquete que se registró al crear la ficha de la app en
        // Play Console (com.app.nubi) — ese nombre no se puede cambiar una
        // vez creada la ficha, así que es el proyecto el que se ajusta a él.
        applicationId = "com.app.nubi"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Con el Kotlin incorporado de AGP 9 ya no hace falta el bloque
    // kotlinOptions: jvmTarget sale de compileOptions.targetCompatibility.

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // Versiones alineadas con la plantilla de proyecto nuevo de Android Studio.
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // Material Components "clásico": aporta los estilos XML (p.ej.
    // Theme.Material3.DayNight.NoActionBar) que usa res/values/themes.xml
    // para el tema base de la app fuera de Compose. Distinto de
    // androidx.compose.material3 (ese es solo para las pantallas en Compose).
    implementation("com.google.android.material:material:1.13.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Supabase: cuenta compartida + estado de suscripción/plazo de gracia
    // (mismo backend que iOS y la web — ver README, fase 1).
    //
    // NO SUBIR a 3.7.0+ sin comprobar antes el Kotlin del proyecto: a partir
    // de ahí Supabase se compila con Kotlin 2.4, y el Kotlin incorporado de
    // AGP 9.4 es el 2.2.10, que solo lee metadatos hasta 2.3. La 3.6.0
    // (abril 2026) es la última anterior a ese salto.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    // Para llamar al Edge Function verificar-compra-android (ver Compras.kt).
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")

    // Google Play Billing (suscripción Premium) — ver app.nubi.data.Compras.
    // 9.1.0 es la última estable (jun 2026); Google exige v8+ desde el
    // 31 ago 2026 para subir versiones nuevas, así que no vale quedarse en 7.x.
    implementation("com.android.billingclient:billing-ktx:9.1.0")
}
