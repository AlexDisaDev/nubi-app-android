plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.nubi"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.nubi"
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

    kotlinOptions {
        jvmTarget = "17"
    }

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
    // Versiones puestas al día (estaban ancladas a finales de 2024, de cuando
    // se creó el esqueleto, y chocaban en tiempo de compilación con Material
    // 1.13.0 / compileSdk 36 — un "incompatible class" que hacía crashear al
    // compilador de Kotlin en vez de dar un error claro).
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
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
    // NOTA: "3.8.0" es la última versión estable verificada al escribir esto;
    // si Android Studio avisa de una más nueva al sincronizar, acéptala.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
}
