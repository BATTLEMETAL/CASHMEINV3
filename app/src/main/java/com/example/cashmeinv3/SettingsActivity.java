package com.example.cashmeinv3;

// Importy Androida, pozwoleń, zasobów, aparatu itd.
import android.Manifest; // <-- Do pozwoleń, np. na aparat
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build; // <-- Do sprawdzania wersji Androida (np. dla pozwoleń na powiadomienia)
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

// Nowe importy do obsługi wyników aktywności i pozwoleń
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat; // Przełącznik On/Off
import androidx.core.content.ContextCompat;

// Import do Material Design (np. dolny pasek nawigacji)
import com.google.android.material.bottomnavigation.BottomNavigationView;

// Standardowe importy Javy (np. do ustawiania języka)
import java.util.Locale;

// --- DODANE IMPORTY DO WYLOGOWANIA ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.facebook.login.LoginManager;
// --- KONIEC DODANYCH IMPORTÓW ---

// Aktywność Ustawień - dziedziczy po BaseActivity dla spójności (motyw itp.)
public class SettingsActivity extends BaseActivity {

    // --- Constants ---
    private static final String TAG = "SettingsActivity"; // Tag do logów
    // Nazwa pliku preferencji specyficznych dla ustawień (innych niż motyw)
    private static final String OTHER_PREFERENCES_FILE = "com.example.cashmeinv3.SettingsPreferences";
    // Klucze do zapisywania ustawień w SharedPreferences
    private static final String KEY_NOTIFICATIONS_ON = "notifications_on"; // Czy powiadomienia włączone?
    private static final String KEY_SOUND_ON = "sound_on";                 // Czy dźwięk włączony?
    private static final String KEY_LANGUAGE_POSITION = "language_position"; // Pozycja wybranego języka w spinnerze
    // Stałe do preferencji motywu (współdzielone z BaseActivity)
    private static final String THEME_PREFS_FILE = BaseActivity.PREFERENCES_FILE; // Ten sam plik co w BaseActivity
    private static final String KEY_THEME_SETTING = BaseActivity.KEY_THEME;      // Ten sam klucz co w BaseActivity
    // Nazwy dla różnych motywów
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String THEME_SYSTEM = "system";
    private static final String THEME_HIGH_CONTRAST = "high_contrast";
    // Domyślny motyw, jeśli żaden nie jest zapisany
    private static final String THEME_DEFAULT = THEME_DARK;

    // --- Elementy UI (widoki z layoutu) ---
    private SwitchCompat notificationSwitch; // Przełącznik powiadomień
    private SwitchCompat soundSwitch;        // Przełącznik dźwięku
    private RadioGroup themeGroup;           // Grupa przycisków radio do wyboru motywu
    private Spinner languageSpinner;         // Rozwijana lista języków
    private Button saveSettingsButton, logoutButton; // Przyciski "Zapisz" i "Wyloguj"
    private BottomNavigationView bottomNavigationView; // Dolny pasek nawigacji

    // --- Zmienne przechowujące aktualny stan ustawień (ładowane z SharedPreferences) ---
    private String currentTheme = ""; // Jaki motyw jest aktualnie ustawiony
    private int currentLanguagePosition = 0; // Jaka pozycja języka jest aktualnie wybrana
    private boolean currentNotifications = true; // Czy powiadomienia są aktualnie włączone?
    private boolean currentSound = true; // Czy dźwięk jest aktualnie włączony?

    // --- Launchery Aparatu (tak jak w innych aktywnościach) ---
    // Ten czeka na wynik zrobienia zdjęcia
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Sprawdzamy, czy user zrobił zdjęcie (OK) i czy są dane
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras(); // Bierzemy dodatki
                    if (extras != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data"); // Bierzemy bitmapę
                        if (imageBitmap != null) {
                            // Mamy zdjęcie, odpalamy ExpensesActivity
                            launchExpensesActivityWithBitmap(imageBitmap);
                        } else {
                            Log.w(TAG, "Dane bitmapy były null w wyniku z aparatu.");
                            showToast(getString(R.string.error_getting_image_bitmap));
                        }
                    } else {
                        Log.w(TAG, "Paczka 'extras' była null w wyniku z aparatu.");
                        showToast(getString(R.string.error_camera_data_extras));
                    }
                } else {
                    // Coś poszło nie tak albo user anulował
                    Log.w(TAG, "Wynik z aparatu nie był OK albo dane były null. Kod wyniku: " + result.getResultCode());
                }
            });

    // Ten launcher czeka na odpowiedź usera ws. pozwolenia na aparat
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) { // User się zgodził
                    openCamera(); // Odpalamy aparat
                } else { // User odmówił
                    showToast(getString(R.string.toast_camera_permission_denied));
                }
            });

    // --- >> NOWY: Launcher do pytania o pozwolenie na POWIADOMIENIA (Android 13+) << ---
    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) { // User się zgodził
                    // Dodaj ten string w strings.xml: <string name="notification_permission_granted">Pozwolenie na powiadomienia przyznane</string>
                    showToast(getString(R.string.notification_permission_granted));
                    Log.i(TAG, "Pozwolenie na powiadomienia przyznane przez użytkownika.");
                    // Teraz, gdy mamy pozwolenie, logika sprawdzająca zapisany stan
                    // (KEY_NOTIFICATIONS_ON) w innym miejscu aplikacji (np. przy starcie)
                    // będzie mogła wysyłać powiadomienia.
                } else { // User odmówił
                    // Użyj istniejącego stringa albo stwórz nowy
                    showToast(getString(R.string.notification_permission_denied));
                    Log.w(TAG, "Pozwolenie na powiadomienia odrzucone przez użytkownika.");
                    // Ustawienie przełącznika na false i zapisanie tego stanu
                    // może być dobrym pomysłem, aby odzwierciedlić brak pozwolenia.
                    if (notificationSwitch != null) notificationSwitch.setChecked(false);
                    saveBooleanPreference(OTHER_PREFERENCES_FILE, KEY_NOTIFICATIONS_ON, false);
                    currentNotifications = false; // Zaktualizuj stan wewnętrzny
                }
            });
    // --- >> KONIEC NOWEGO LAUNCHERA << ---


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Standardowy start
        setContentView(R.layout.activity_settings); // Ładujemy layout dla ustawień

        initializeUI(); // Podpinamy widoki z XMLa
        loadSettings(); // Ładujemy zapisane ustawienia
        setupListeners(); // Ustawiamy akcje dla przycisków itp.
        configureBottomNavigation(); // Konfigurujemy dolny pasek
    }

    // Łączy zmienne w kodzie z elementami w pliku XML layoutu
    private void initializeUI() {
        notificationSwitch = findViewById(R.id.notificationSwitch);
        soundSwitch = findViewById(R.id.soundSwitch);
        languageSpinner = findViewById(R.id.languageSpinner);
        themeGroup = findViewById(R.id.themeGroup);
        saveSettingsButton = findViewById(R.id.saveSettingsButton);
        logoutButton = findViewById(R.id.logoutButton);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Sprawdzamy, czy wszystko się znalazło (zostawiamy te logi, są przydatne)
        if (notificationSwitch == null) Log.e(TAG, "Nie znaleziono notificationSwitch!");
        if (soundSwitch == null) Log.e(TAG, "Nie znaleziono soundSwitch!");
        if (languageSpinner == null) Log.e(TAG, "Nie znaleziono languageSpinner!");
        if (themeGroup == null) Log.e(TAG, "Nie znaleziono themeGroup!");
        if (saveSettingsButton == null) Log.e(TAG, "Nie znaleziono saveSettingsButton!");
        if (logoutButton == null) Log.e(TAG, "Nie znaleziono logoutButton!");
        if (bottomNavigationView == null) Log.e(TAG, "Nie znaleziono bottomNavigationView!");
    }

    // Ustawia, co mają robić przyciski po kliknięciu
    private void setupListeners() {
        // Przycisk "Zapisz"
        if (saveSettingsButton != null) {
            saveSettingsButton.setOnClickListener(v -> saveSettings());
        }
        // Przycisk "Wyloguj"
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> logout());
        }
        // Można też dodać listener bezpośrednio do przełącznika powiadomień,
        // aby od razu prosić o pozwolenie przy włączaniu, zamiast czekać na "Zapisz".
        // if (notificationSwitch != null) {
        //    notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
        //        if (isChecked) {
        //            handleNotificationSettingChange(true); // Od razu sprawdź/poproś o pozwolenie
        //        }
        //    });
        // }
    }

    // Konfiguruje dolny pasek nawigacji
    private void configureBottomNavigation() {
        // (Zostawiamy istniejący kod - jest poprawny do nawigacji)
        if (bottomNavigationView == null) return; // Jak nie ma, to nic nie rób
        // Co się dzieje po kliknięciu ikonki
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId(); // ID klikniętej ikonki
            Intent intent = null; // Intencja do odpalenia nowego ekranu
            // Sprawdzamy, co kliknięto
            if (itemId == R.id.navigation_home) {
                intent = new Intent(SettingsActivity.this, HomeActivity.class);
            } else if (itemId == R.id.navigation_calendar) {
                intent = new Intent(SettingsActivity.this, CalendarActivity.class);
            } else if (itemId == R.id.navigation_menu) {
                return true; // Już tu jesteśmy, nic nie rób
            } else if (itemId == R.id.navigation_camera) {
                checkCameraPermissionAndOpenCamera(); // Zmieniono nazwę dla spójności
                return false; // Zostań na Ustawieniach (nie zaznaczaj ikonki aparatu)
            }

            // Jeśli przygotowano intencję, odpal nową aktywność
            if (intent != null) {
                // Standardowe flagi + wyłączenie animacji
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true; // Obsłużono
            }
            return false; // Nie obsłużono
        });
        // Ustawiamy ikonkę Menu (Ustawień) jako zaznaczoną na starcie
        bottomNavigationView.setSelectedItemId(R.id.navigation_menu);
    }


    // --- Ładowanie/Zapisywanie Ustawień ---
    // Ładuje zapisane ustawienia z SharedPreferences i ustawia odpowiednio UI
    private void loadSettings() {
        // (Zostawiamy istniejący kod - poprawnie ładuje zapisane stany)
        // Ładujemy ustawienia z pliku specyficznego dla SettingsActivity
        SharedPreferences otherPrefs = getSharedPreferences(OTHER_PREFERENCES_FILE, MODE_PRIVATE);
        currentNotifications = otherPrefs.getBoolean(KEY_NOTIFICATIONS_ON, true); // Domyślnie włączone
        currentSound = otherPrefs.getBoolean(KEY_SOUND_ON, true);               // Domyślnie włączony
        currentLanguagePosition = otherPrefs.getInt(KEY_LANGUAGE_POSITION, 0); // Domyślnie 0 (pewnie angielski)

        // Ładujemy ustawienie motywu z pliku współdzielonego z BaseActivity
        SharedPreferences themePrefs = getSharedPreferences(THEME_PREFS_FILE, MODE_PRIVATE);
        currentTheme = themePrefs.getString(KEY_THEME_SETTING, THEME_DEFAULT); // Bierzemy domyślny, jak nic nie ma

        // Logujemy załadowane wartości (przydatne do debugowania)
        Log.d(TAG, "Ładuję ustawienia - Powiadomienia: " + currentNotifications + ", Dźwięk: " + currentSound + ", PozJęzyka: " + currentLanguagePosition + ", Motyw: " + currentTheme);

        // Ustawiamy przełączniki zgodnie z załadowanymi wartościami
        if (notificationSwitch != null) {
            notificationSwitch.setChecked(currentNotifications);
        }
        if (soundSwitch != null) {
            soundSwitch.setChecked(currentSound);
        }

        // Ustawiamy wybrany język w spinnerze
        if (languageSpinner != null && currentLanguagePosition >= 0 && currentLanguagePosition < languageSpinner.getCount()) {
            languageSpinner.setSelection(currentLanguagePosition); // Ustawiamy na załadowaną pozycję
        } else if (languageSpinner != null) {
            // Jak załadowana pozycja jest zła (np. spoza zakresu), resetujemy do 0
            Log.w(TAG, "Załadowano nieprawidłową pozycję języka: " + currentLanguagePosition + ". Resetuję do 0.");
            languageSpinner.setSelection(0);
            currentLanguagePosition = 0; // Poprawiamy też stan w zmiennej
        }

        // Ustawiamy zaznaczony przycisk radio dla motywu
        if (themeGroup != null) {
            int themeCheckId = R.id.themeDark; // Domyślne zaznaczenie w UI
            // Wybieramy odpowiednie ID przycisku radio na podstawie załadowanej nazwy motywu
            if (THEME_LIGHT.equals(currentTheme)) themeCheckId = R.id.themeLight;
            else if (THEME_DARK.equals(currentTheme)) themeCheckId = R.id.themeDark;
            else if (THEME_SYSTEM.equals(currentTheme)) themeCheckId = R.id.themeSystem;
            else if (THEME_HIGH_CONTRAST.equals(currentTheme)) themeCheckId = R.id.themeHighContrast;
            else { Log.w(TAG, "Załadowano nieznany motyw: '" + currentTheme + "'. Używam domyślnego zaznaczenia w UI."); }
            // Zaznaczamy odpowiedni przycisk radio
            try {
                themeGroup.check(themeCheckId);
            } catch (Exception e) {
                // Jakby ID było złe (nie powinno się zdarzyć)
                Log.e(TAG, "Błąd przy ustawianiu zaznaczonego przycisku radio motywu o ID: " + themeCheckId, e);
                // Plan B: zaznacz pierwszy przycisk w grupie
                if (themeGroup.getChildCount() > 0) themeGroup.check(themeGroup.getChildAt(0).getId());
            }
        }
    }

    // Zapisuje aktualne ustawienia wybrane w UI do SharedPreferences
    private void saveSettings() {
        // Sprawdzamy, czy wszystkie potrzebne elementy UI istnieją
        if (notificationSwitch == null || soundSwitch == null || languageSpinner == null || themeGroup == null) {
            Log.e(TAG, "Nie mogę zapisać ustawień, brakuje jednego lub więcej elementów UI.");
            showToast(getString(R.string.error_saving_settings));
            return;
        }

        // Pobieramy NOWE stany ustawień z elementów UI
        boolean newNotificationsOn = notificationSwitch.isChecked();
        boolean newSoundOn = soundSwitch.isChecked();
        int newLanguagePosition = languageSpinner.getSelectedItemPosition();
        String newSelectedThemeString = getSelectedThemeString(); // Używamy funkcji pomocniczej

        Log.d(TAG, "Próbuję zapisać ustawienia - Powiadomienia: " + newNotificationsOn + ", Dźwięk: " + newSoundOn + ", PozJęzyka: " + newLanguagePosition + ", Motyw: " + newSelectedThemeString);

        // Sprawdzamy, czy którekolwiek ustawienie faktycznie się zmieniło w porównaniu do stanu ZAŁADOWANEGO na początku
        boolean languageChanged = (newLanguagePosition != currentLanguagePosition);
        boolean themeChanged = !newSelectedThemeString.equals(currentTheme);
        boolean notificationChanged = (newNotificationsOn != currentNotifications);
        boolean soundChanged = (newSoundOn != currentSound);

        // Jeśli nic się nie zmieniło, nie ma sensu zapisywać
        if (!languageChanged && !themeChanged && !notificationChanged && !soundChanged) {
            Log.d(TAG, "Żadne ustawienia się nie zmieniły. Nic nie zapisuję.");
            // Dodaj ten string w strings.xml: <string name="settings_no_changes">Nie wprowadzono zmian</string>
            showToast(getString(R.string.settings_no_changes));
            return;
        }

        // Zapisujemy WSZYSTKIE zmienione ustawienia
        // Otwieramy edytory dla obu plików preferencji
        SharedPreferences.Editor otherEditor = getSharedPreferences(OTHER_PREFERENCES_FILE, MODE_PRIVATE).edit();
        SharedPreferences.Editor themeEditor = getSharedPreferences(THEME_PREFS_FILE, MODE_PRIVATE).edit();
        boolean needsRestart = false; // Czy potrzebny będzie restart aktywności?

        // Zapisujemy zmienione ustawienia i logujemy zmiany
        if (notificationChanged) {
            otherEditor.putBoolean(KEY_NOTIFICATIONS_ON, newNotificationsOn);
            Log.i(TAG, "Ustawienie powiadomień zmienione na: " + newNotificationsOn);
            // Obsługujemy zmianę (np. prośba o pozwolenie)
            handleNotificationSettingChange(newNotificationsOn);
        }
        if (soundChanged) {
            otherEditor.putBoolean(KEY_SOUND_ON, newSoundOn);
            Log.i(TAG, "Ustawienie dźwięku zmienione na: " + newSoundOn);
            // Stosujemy zmianę dźwięku od razu (placeholder - patrz metoda)
            handleSoundSettingChange(newSoundOn);
        }
        if (languageChanged) {
            otherEditor.putInt(KEY_LANGUAGE_POSITION, newLanguagePosition);
            Log.i(TAG, "Ustawienie języka zmienione na pozycję: " + newLanguagePosition);
            // Stosujemy zmianę języka
            applyLocale(newLanguagePosition);
            needsRestart = true; // Zmiana języka wymaga restartu
        }
        if (themeChanged) {
            themeEditor.putString(KEY_THEME_SETTING, newSelectedThemeString);
            Log.i(TAG, "Ustawienie motywu zmienione na: " + newSelectedThemeString);
            needsRestart = true; // Zmiana motywu wymaga restartu
        }

        // Zatwierdzamy zmiany w obu edytorach
        otherEditor.apply();
        themeEditor.apply();

        // --- >> WAŻNE: Aktualizujemy zmienne przechowujące obecny stan PO zapisaniu << ---
        currentNotifications = newNotificationsOn;
        currentSound = newSoundOn;
        currentLanguagePosition = newLanguagePosition;
        currentTheme = newSelectedThemeString;
        // --- >> KONIEC AKTUALIZACJI << ---

        // Restartujemy aktywność tylko jeśli zmienił się język lub motyw
        if (needsRestart) {
            // Dodaj ten string w strings.xml: <string name="settings_saved_restarting">Ustawienia zapisane. Aplikacja zostanie ponownie uruchomiona.</string>
            showToast(getString(R.string.settings_saved_restarting));
            restartActivity();
        } else {
            // Pokazujemy tylko komunikat o zapisaniu, jeśli zmieniły się tylko powiadomienia/dźwięk
            // Dodaj ten string w strings.xml: <string name="settings_saved">Ustawienia zapisane.</string>
            showToast(getString(R.string.settings_saved));
        }
    }

    // --- >> NOWA: Funkcja pomocnicza do pobrania nazwy wybranego motywu << ---
    // Zwraca string reprezentujący wybrany motyw (np. "dark", "light")
    private String getSelectedThemeString() {
        if (themeGroup == null) return THEME_DEFAULT; // Jak nie ma grupy, zwróć domyślny
        int selectedThemeId = themeGroup.getCheckedRadioButtonId(); // Pobierz ID zaznaczonego przycisku
        // Sprawdź, które ID jest zaznaczone i zwróć odpowiednią nazwę motywu
        if (selectedThemeId == R.id.themeLight) return THEME_LIGHT;
        if (selectedThemeId == R.id.themeDark) return THEME_DARK;
        if (selectedThemeId == R.id.themeSystem) return THEME_SYSTEM;
        if (selectedThemeId == R.id.themeHighContrast) return THEME_HIGH_CONTRAST;
        // Jak żaden nie jest zaznaczony (nie powinno się zdarzyć), zwróć domyślny
        Log.w(TAG, "Wygląda na to, że żaden przycisk radio motywu nie jest zaznaczony. Zwracam domyślny: " + THEME_DEFAULT);
        return THEME_DEFAULT;
    }
    // --- >> KONIEC NOWEJ FUNKCJI << ---

    // --- >> NOWA: Obsługa zmiany ustawienia powiadomień << ---
    // Wywoływana, gdy użytkownik zmienia stan przełącznika powiadomień (i zapisuje)
    private void handleNotificationSettingChange(boolean enabled) {
        if (enabled) { // Użytkownik WŁĄCZYŁ powiadomienia
            Log.d(TAG, "Przełącznik powiadomień WŁĄCZONY. Sprawdzam pozwolenie...");
            // Sprawdzamy pozwolenie tylko na Androidzie 13 (TIRAMISU) i nowszych
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Sprawdzamy, czy mamy już pozwolenie
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED) {
                    // Pozwolenie już jest
                    Log.i(TAG, "Pozwolenie na powiadomienia już przyznane.");
                    // Miejsce na logikę aktywacji: Aplikacja powinna sprawdzać zapisane ustawienie
                    // (KEY_NOTIFICATIONS_ON) przed wysłaniem jakiegokolwiek powiadomienia.
                    // Upewnienie się, że kanały są stworzone (np. w onCreate aplikacji) jest kluczowe.

                } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Opcjonalnie: Wyjaśnij, dlaczego potrzebujesz pozwolenia (np. w dialogu)
                    // zanim poprosisz ponownie. Na razie po prostu prosimy.
                    Log.i(TAG, "Trzeba poprosić o pozwolenie na powiadomienia (zasadne pokazanie wyjaśnienia).");
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS); // Używamy launchera
                } else {
                    // Pozwolenie nie zostało jeszcze przyznane, prosimy o nie
                    Log.i(TAG, "Proszę o pozwolenie na powiadomienia.");
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS); // Używamy launchera
                }
            } else {
                // Poniżej Androida 13 pozwolenie jest przyznawane automatycznie przy instalacji
                Log.i(TAG, "Poniżej Androida 13 nie potrzeba pozwolenia runtime na powiadomienia.");
                // Miejsce na logikę aktywacji: Podobnie jak wyżej, aplikacja powinna sprawdzać
                // zapisane ustawienie przed wysłaniem powiadomienia.
            }
        } else { // Użytkownik WYŁĄCZYŁ powiadomienia
            Log.i(TAG, "Przełącznik powiadomień WYŁĄCZONY.");
            // Miejsce na logikę deaktywacji: Najprościej jest, aby kod wysyłający powiadomienia
            // sprawdzał zapisane ustawienie (KEY_NOTIFICATIONS_ON) i po prostu *nie wysyłał*
            // powiadomienia, jeśli jest ustawione na false. Zatrzymywanie usług/kanałów
            // może być bardziej skomplikowane i nie zawsze konieczne.
        }
    }
    // --- >> KONIEC NOWEJ OBSŁUGI << ---

    // --- >> NOWA: Obsługa zmiany ustawienia dźwięku << ---
    // Wywoływana, gdy użytkownik zmienia stan przełącznika dźwięku (i zapisuje)
    private void handleSoundSettingChange(boolean enabled) {
        if (enabled) { // Użytkownik WŁĄCZYŁ dźwięk
            Log.i(TAG, "Przełącznik dźwięku WŁĄCZONY.");
            // Ważne: Faktyczna logika włączania/wyłączania dźwięku
            // powinna znajdować się tam, gdzie dźwięki są odtwarzane.
            // Kod odtwarzający dźwięk (np. w ExpensesActivity, HomeActivity)
            // powinien najpierw sprawdzić stan zapisany w SharedPreferences:
            // SharedPreferences prefs = getSharedPreferences(OTHER_PREFERENCES_FILE, MODE_PRIVATE);
            // boolean soundOn = prefs.getBoolean(KEY_SOUND_ON, true); // true to domyślna wartość
            // if (soundOn) { /* Odtwórz dźwięk */ }
            showToast("Dźwięk WŁĄCZONY (Sprawdzenie stanu w miejscu odtwarzania)"); // Uaktualniony toast
        } else { // Użytkownik WYŁĄCZYŁ dźwięk
            Log.i(TAG, "Przełącznik dźwięku WYŁĄCZONY.");
            // Podobnie jak wyżej, logika wyłączania polega na tym,
            // że kod odtwarzający dźwięk sprawdza SharedPreferences
            // i *nie odtwarza* dźwięku, jeśli ustawienie jest wyłączone.
            showToast("Dźwięk WYŁĄCZONY (Sprawdzenie stanu w miejscu odtwarzania)"); // Uaktualniony toast
        }
    }
    // --- >> KONIEC NOWEJ OBSŁUGI << ---


    // --- Zmiana Języka ---
    // Stosuje wybrany język w aplikacji
    private void applyLocale(int languagePosition) {
        // (Zostawiamy istniejący kod - jest poprawny do stosowania języka)
        String langCode = "en"; // Domyślnie angielski
        // Tablica kodów języków - powinna odpowiadać @array/language_options w strings.xml
        final String[] languageCodes = {"en", "pl", "es", "fr"};

        // Wybieramy kod języka na podstawie pozycji ze spinnera
        if (languagePosition >= 0 && languagePosition < languageCodes.length) {
            langCode = languageCodes[languagePosition];
        } else {
            // Jak pozycja jest zła, wracamy do domyślnej
            Log.w(TAG, "Otrzymano nieprawidłową pozycję języka ("+ languagePosition + ") w applyLocale. Ustawiam domyślną 'en'.");
            languagePosition = 0; // Resetujemy indeks
            langCode = languageCodes[0];
        }

        Log.i(TAG, "Stosuję język: " + langCode);
        Locale locale = new Locale(langCode); // Tworzymy obiekt Locale
        Locale.setDefault(locale); // Ustawiamy go jako domyślny dla całej apki

        // Aktualizujemy konfigurację zasobów aplikacji
        Resources resources = getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale); // Ustawiamy nowy język w konfiguracji
        // Używamy createConfigurationContext do poprawnego stosowania zmian konfiguracji
        // Context updatedContext = createConfigurationContext(config); // To może być potrzebne w niektórych przypadkach
        // getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics()); // To może mieć problemy
        // Prawidłowy sposób aktualizacji zasobów dla bieżącej aktywności
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        // Aktualizujemy zmienną stanu PO zastosowaniu języka
        currentLanguagePosition = languagePosition;
    }

    // --- Wylogowanie ---
    private void logout() {
        Log.i(TAG, "Użytkownik się wylogowuje.");

        // --- DODANA LOGIKA WYLOGOWANIA ---

        // 1. Wylogowanie z Firebase (najważniejsze dla stanu aplikacji w tej apce)
        FirebaseAuth.getInstance().signOut();
        Log.i(TAG, "Wylogowanie z Firebase zainicjowane.");

        // 2. Wylogowanie z Google
        // Inicjalizujemy klienta Google Sign-In tylko na potrzeby wylogowania.
        // W bardziej złożonej apce lepiej mieć jedną instancję w np. Application class.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .build(); // Podstawowe opcje wystarczą do wylogowania
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
        // Wywołanie signOut() jest asynchroniczne. Można dodać listenera, żeby wiedzieć, kiedy się zakończy.
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.i(TAG, "Wylogowanie z Google zakończone pomyślnie.");
            } else {
                Log.w(TAG, "Wylogowanie z Google nie powiodło się.", task.getException());
            }
            // Nawigacja do ekranu logowania odbywa się niezależnie od wyniku tego listenera
        });
        Log.i(TAG, "Wylogowanie z Google zainicjowane.");


        // 3. Wylogowanie z Facebooka
        // Sprawdzenie, czy użytkownik jest faktycznie zalogowany przez FB nie jest tu konieczne,
        // wywołanie logOut() po prostu nic nie zrobi, jeśli nie ma aktywnej sesji FB.
        LoginManager.getInstance().logOut();
        Log.i(TAG, "Wylogowanie z Facebook zainicjowane.");

        // --- KONIEC DODANEJ LOGIKI ---

        showToast(getString(R.string.button_logout)); // Pokazujemy toast (można go przenieść do listenerów powyżej, jeśli chcemy potwierdzenia sukcesu)
        // Tworzymy intencję powrotu do ekranu logowania (MainActivity)
        Intent intent = new Intent(this, MainActivity.class);
        // Ustawiamy flagi, żeby wyczyścić stos aktywności (user nie wróci przyciskiem wstecz)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent); // Odpalamy ekran logowania
        finish(); // Zamykamy obecną aktywność (SettingsActivity)
    }


    // --- Logika Aparatu (taka sama jak w innych aktywnościach) ---
    private void checkCameraPermissionAndOpenCamera() { // Zmieniono nazwę dla spójności
        // Sprawdzamy, czy mamy pozwolenie
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(); // Mamy, odpalamy
        } else {
            // Można tu dodać wyjaśnienie przed prośbą
            Log.i(TAG, "Proszę o pozwolenie na aparat.");
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA); // Prosimy
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // Intencja zrobienia zdjęcia
        // Używamy resolveActivity, żeby upewnić się, że jest apka aparatu
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                Log.d(TAG, "Odpalam intencję aparatu.");
                cameraLauncher.launch(takePictureIntent); // Odpalamy przez nasz launcher
            } catch (Exception e) { // Łapiemy potencjalne błędy
                Log.e(TAG, "Nie udało się odpalić intencji aparatu", e);
                showToast(getString(R.string.error_camera_launch));
            }
        } else {
            Log.w(TAG, "Brak dostępnej apki aparatu do obsłużenia intencji.");
            showToast(getString(R.string.error_no_camera_app));
        }
    }

    // Odpala ExpensesActivity z przekazaną bitmapą
    private void launchExpensesActivityWithBitmap(Bitmap bitmap) {
        // (Zostawiamy istniejący kod)
        Intent intent = new Intent(this, ExpensesActivity.class);
        // Upewnij się, że ten klucz jest zdefiniowany spójnie (np. w HomeActivity albo pliku Constants)
        intent.putExtra(HomeActivity.EXTRA_BITMAP_FOR_PROCESSING, bitmap);
        // Flaga, żeby przenieść na wierzch, jak już działa
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent); // Odpalamy
    }
    // --- Koniec Logiki Aparatu ---

    // --- Narzędzia Pomocnicze ---
    // Restartuje obecną aktywność (przydatne po zmianie języka/motywu)
    private void restartActivity() {
        // (Zostawiamy istniejący kod)
        Intent intent = getIntent(); // Bierzemy obecną intencję
        finish(); // Zamykamy obecną instancję
        startActivity(intent); // Odpalamy nową instancję z tą samą intencją
        overridePendingTransition(0, 0); // Bez animacji dla płynnego restartu
    }

    // Pokazuje krótki toast
    private void showToast(@NonNull String message) {
        // (Zostawiamy istniejący kod)
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else {
            Log.w(TAG, "Próbowano pokazać pusty toast.");
        }
    }

    // --- >> NOWA: Funkcja pomocnicza do zapisywania wartości boolean (opcjonalna, mniej powtórzeń) << ---
    // Zapisuje wartość boolean do podanego pliku preferencji pod podanym kluczem
    private void saveBooleanPreference(String prefsFile, String key, boolean value) {
        SharedPreferences sharedPreferences = getSharedPreferences(prefsFile, MODE_PRIVATE); // Otwieramy plik
        SharedPreferences.Editor editor = sharedPreferences.edit(); // Otwieramy edytor
        editor.putBoolean(key, value); // Wpisujemy wartość
        editor.apply(); // Zapisujemy asynchronicznie
    }
    // --- >> KONIEC NOWEJ FUNKCJI << ---

} // Koniec klasy SettingsActivity