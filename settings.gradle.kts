// Konfiguracja zarządzania pluginami Gradle.
// Tutaj definiujemy, skąd Gradle ma pobierać *same pluginy* (np. plugin Androida, Kotlina),
// zanim jeszcze zacznie konfigurować sam projekt.
pluginManagement {
    repositories {
        // Repozytorium Google - niezbędne dla pluginów związanych z Androidem i usługami Google.
        google()
        // Centralne repozytorium Maven - główne źródło wielu pluginów i bibliotek.
        mavenCentral()
        // Portal pluginów Gradle - umożliwia wyszukiwanie i pobieranie pluginów po ich ID.
        gradlePluginPortal()
        // Repozytorium JitPack - często używane dla bibliotek/pluginów hostowanych np. na GitHubie.
        // W tym projekcie może być używane np. dla SDK Facebooka, chociaż często jest też w Maven Central.
        maven("https://jitpack.io")
    }
}

// Konfiguracja zarządzania zależnościami (bibliotekami) dla całego projektu.
// Ten blok określa, skąd Gradle ma pobierać *biblioteki*, których używa kod aplikacji
// (np. AppCompat, Firebase, Gson).
dependencyResolutionManagement {
    // To jest ważne ustawienie wprowadzone w nowszych wersjach Gradle.
    // Wymusza, aby repozytoria dla zależności były definiowane *tylko* w tym pliku (settings.gradle.kts),
    // a nie w plikach build.gradle poszczególnych modułów. Poprawia to spójność i bezpieczeństwo builda.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Repozytorium Google - konieczne dla bibliotek AndroidX, Material Design, Firebase itp.
        google()
        // Centralne repozytorium Maven - dla większości popularnych bibliotek Java/Kotlin.
        mavenCentral()
        // Repozytorium JitPack - dla bibliotek, które nie są dostępne w Google/MavenCentral.
        maven("https://jitpack.io")
    }
}

// Nazwa głównego projektu (root project). Widoczna np. w Android Studio.
rootProject.name = "CASHMEINV3"
// Włączenie modułu o nazwie "app" do projektu.
// Oznacza to, że katalog "app" zawiera kod źródłowy głównej aplikacji Android.
include(":app")