// Plik build.gradle.kts na poziomie projektu (główny plik konfiguracyjny Gradle)

// Deklaracja wersji głównych pluginów używanych w całym projekcie.
// `apply false` oznacza, że są one tylko zdefiniowane tutaj,
// a faktycznie używane będą w plikach `build.gradle.kts` poszczególnych modułów (np. `:app`).
// Ważne jest utrzymanie zgodności wersji tych pluginów.
plugins {
    // Plugin do budowania aplikacji Android
    id("com.android.application") version "8.1.1" apply false
    // Plugin do budowania bibliotek Android (jeśli byśmy je mieli)
    id("com.android.library") version "8.1.1" apply false
    // Plugin do obsługi języka Kotlin w Androidzie
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

// Konfiguracja `buildscript` - to odnosi się do samego procesu budowania projektu Gradle.
// Wewnątrz definiujemy, skąd Gradle ma pobierać narzędzia potrzebne do zbudowania (repozytoria)
// oraz jakie to są narzędzia (zależności classpath).
buildscript {
    // Repozytoria dla `buildscript` - tu Gradle szuka samych pluginów do budowania:
    repositories {
        // Repozytorium Google, wymagane dla zależności Androida i Firebase.
        google()
        // Centralne repozytorium Maven, dla ogólnych bibliotek i narzędzi.
        mavenCentral()
        // Portal pluginów Gradle, do znajdowania pluginów po ich ID.
        gradlePluginPortal()
        // Repozytorium JitPack, często używane dla bibliotek publikowanych bezpośrednio z GitHub/itp.
        maven("https://jitpack.io")
    }
    // Zależności `buildscript` (narzędzia do budowania):
    dependencies {
        // Określa wersję Android Gradle Plugin (AGP), która buduje kod Androida. Wersja musi być zgodna z używaną wersją Gradle.
        classpath("com.android.tools.build:gradle:8.1.1")
        // Plugin Kotlina dla Gradle, umożliwiający kompilację kodu Kotlin. Wersja powiązana z wersją języka Kotlin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        // Plugin Google Services, przetwarza plik `google-services.json` i konfiguruje zależności Firebase.
        classpath("com.google.gms:google-services:4.3.15")
    }
}

// Usunięto blok `allprojects`, ponieważ w nowszych wersjach Gradle zaleca się
// definiowanie repozytoriów dla zależności *projektu* (a nie buildscriptu)
// centralnie w pliku `settings.gradle.kts` (tryb `RepositoriesMode.FAIL_ON_PROJECT_REPOS`).

// Rejestracja zadania `clean` typu `Delete`.
// To zadanie, uruchamiane np. przez `./gradlew clean`, usuwa cały katalog `build` projektu,
// czyszcząc wyniki poprzednich kompilacji i inne pliki tymczasowe generowane przez Gradle.
tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}