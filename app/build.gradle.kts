// Plik build.gradle.kts na poziomie aplikacji (app)

// Tutaj deklarujemy pluginy, czyli takie dodatki do Gradle'a, które pomagają budować apkę
plugins {
    // Podstawowy plugin do budowania aplikacji na Androida
    id("com.android.application")
    // Plugin do obsługi języka Kotlin w Androidzie
    id("org.jetbrains.kotlin.android")
    // Plugin Google Services, niezbędny do działania Firebase (np. logowania, analityki)
    id("com.google.gms.google-services")
}

// Główny blok konfiguracyjny dla Androida
android {
    // Przestrzeń nazw aplikacji, unikalny identyfikator dla kodu (głównie ważne przy bibliotekach)
    namespace = "com.example.cashmeinv3"
    // Wersja SDK, na którą kompilujemy kod. Zalecana najnowsza dla dostępu do nowych funkcji.
    compileSdk = 34

    // Domyślna konfiguracja dla wszystkich wariantów budowania (np. debug, release)
    defaultConfig {
        // Unikalny identyfikator aplikacji w Sklepie Play i na urządzeniu
        applicationId = "com.example.cashmeinv3"
        // Minimalna wersja Androida, na której aplikacja zadziała. 24 to Android 7.0 Nougat.
        minSdk = 24
        // Wersja Androida, na którą celujemy i testujemy. Powinna zgadzać się z compileSdk.
        targetSdk = 34
        // Wewnętrzny numer wersji aplikacji (liczba całkowita, rośnie przy każdej nowej wersji)
        versionCode = 1
        // Nazwa wersji widoczna dla użytkownika (np. "1.0", "1.1-beta")
        versionName = "1.0"

        // Runner do testów instrumentalnych (uruchamianych na urządzeniu/emulatorze)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Włącza wsparcie dla grafik wektorowych (SVG) na starszych wersjach Androida (przed API 21)
        vectorDrawables.useSupportLibrary = true
    }

    // Konfiguracja dla różnych typów budowania (np. debugowa do testów, release do publikacji)
    buildTypes {
        // Konfiguracja dla wersji produkcyjnej (release)
        release {
            // Czy włączyć minimalizację kodu (usuwanie nieużywanego kodu, zaciemnianie nazw)? Na razie wyłączone.
            isMinifyEnabled = false
            // Pliki z regułami dla ProGuarda (narzędzia do optymalizacji i zaciemniania kodu)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Domyślne reguły Androida
                "proguard-rules.pro" // Moje własne reguły (ważne przy niektórych bibliotekach)
            )
        }
        // Można dodać konfigurację dla 'debug', jeśli potrzebne są specjalne ustawienia
    }

    // Opcje kompilacji kodu Java
    compileOptions {
        // Określa wersję kodu źródłowego Java (tutaj Java 11)
        sourceCompatibility = JavaVersion.VERSION_11
        // Określa wersję kodu bajtowego Java (też Java 11)
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Opcje kompilacji Kotlina
    kotlinOptions {
        // Wersja JVM, na którą kompilujemy kod Kotlina (spójna z Javą - 11)
        jvmTarget = "11"
    }

    // Włączanie/wyłączanie dodatkowych funkcji budowania
    buildFeatures {
        // Włącza obsługę Jetpack Compose (nowoczesny framework UI)
        compose = true
        // viewBinding = true // Zakomentowane, bo używam XML i ID (i trochę Compose?) - mogłoby się gryźć
    }

    // Opcje specyficzne dla Jetpack Compose
    composeOptions {
        // Wersja rozszerzenia kompilatora Kotlina dla Compose. Musi być zgodna z wersją Kotlina.
        kotlinCompilerExtensionVersion = "1.5.0" // Warto sprawdzić zgodność tej wersji!
    }

    // Konfiguracja pakowania zasobów
    packaging {
        resources {
            // Wyklucza pewne pliki META-INF, żeby uniknąć konfliktów przy budowaniu,
            // które czasem pojawiają się przy dodawaniu różnych bibliotek.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Tutaj definiujemy wszystkie zewnętrzne biblioteki, z których korzysta projekt
dependencies {
    // Podstawowe biblioteki AndroidX Core (Kotlin Extensions) i Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // Najnowsza stabilna wersja do zarządzania cyklem życia

    // Zależności dla Jetpack Compose (jeśli faktycznie używane)
    implementation("androidx.compose.ui:ui:1.6.4") // Podstawowe UI Compose
    implementation("androidx.compose.material3:material3:1.2.0") // Komponenty Material Design 3 dla Compose
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.4") // Do podglądu layoutów Compose w Android Studio
    implementation("androidx.activity:activity-compose:1.8.2") // Integracja Compose z Aktywnościami

    // Biblioteki Material Design (dla widoków XML) i AppCompat (wsparcie dla starszych wersji)
    implementation("com.google.android.material:material:1.11.0") // Najnowsza wersja komponentów Material
    implementation("androidx.appcompat:appcompat:1.6.1") // Podstawa dla zgodności wstecznej

    // Zależności Firebase - używamy BOM (Bill of Materials) do zarządzania wersjami
    // Dzięki BOM nie musimy podawać wersji dla każdej biblioteki Firebase osobno.
    implementation(platform("com.google.firebase:firebase-bom:32.7.2")) // Najnowszy dostępny BOM
    implementation("com.google.firebase:firebase-auth-ktx") // Firebase Authentication (logowanie/rejestracja) w wersji Kotlin
    implementation("com.google.firebase:firebase-analytics-ktx") // Firebase Analytics (zbieranie danych o użyciu) w wersji Kotlin

    // Google Play Services - potrzebne do logowania przez Google
    implementation("com.google.android.gms:play-services-auth:20.7.0") // Warto sprawdzić, czy nie ma nowszej wersji

    // Facebook SDK - do logowania przez Facebooka
    implementation("com.facebook.android:facebook-login:16.2.0") { // Najnowsza wersja FB SDK
        // Wykluczamy starą bibliotekę 'support', żeby uniknąć konfliktów z AndroidX
        exclude(group = "com.android.support")
    }

    // ML Kit - do rozpoznawania tekstu na obrazach (np. paragonach)
    implementation("com.google.mlkit:text-recognition:16.0.0") // Sprawdzić, czy jest nowsza wersja tej biblioteki

    // Gson - biblioteka do parsowania JSON (np. przy zapisie/odczycie danych z SharedPreferences)
    implementation("com.google.code.gson:gson:2.10.1")

    // Zależności do testów jednostkowych (JUnit) i instrumentalnych (Espresso)
    testImplementation("junit:junit:4.13.2") // Testy jednostkowe na JVM
    androidTestImplementation("androidx.test.ext:junit:1.1.5") // Testy instrumentalne (na urządzeniu/emulatorze)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") // Framework Espresso do testów UI
    // Zależności do testowania UI napisanego w Compose
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.4")
    // Narzędzia do debugowania UI Compose
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.4")

    // Opcjonalny Credential Manager (zakomentowany - do zarządzania hasłami/passkeys, jeśli potrzebne)
    // implementation("androidx.credentials:credentials:1.0.0-alpha05")

    // Material Calendar View (Dodana zależność do widoku kalendarza)
    // Biblioteka zewnętrzna do wyświetlania rozbudowanego kalendarza.
    implementation("com.applandeo:material-calendar-view:1.9.0-rc03")

}

// Zastosowanie pluginu Google Services na końcu pliku (wymagane przez Firebase)
apply(plugin = "com.google.gms.google-services")