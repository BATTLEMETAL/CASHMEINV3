package com.example.cashmeinv3;

// Rzeczy z Androida i żeby działało na różnych wersjach
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; // Albo BaseActivity, jak tam mamy
import androidx.core.content.ContextCompat;

// Importy do tego fajnego kalendarza Material Calendar View
import com.applandeo.materialcalendarview.CalendarView;
import com.applandeo.materialcalendarview.EventDay; // Jakbyśmy chcieli dodawać jakieś eventy później
import com.applandeo.materialcalendarview.exceptions.OutOfDateRangeException;
import com.applandeo.materialcalendarview.listeners.OnDayClickListener;

// Importy do Material Design (żeby ładnie wyglądało)
import com.google.android.material.bottomnavigation.BottomNavigationView;

// Importy do ogarniania JSONa (zapis/odczyt danych)
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

// Standardowe rzeczy z Javy
import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Zakładamy, że BaseActivity ogarnia wspólne rzeczy typu motyw, język itp.
public class CalendarActivity extends BaseActivity { // Albo BaseActivity

    // --- Stałe, żeby nie pisać tego wszędzie ---
    private static final String TAG = "CalendarActivity"; // Do logów, żeby wiedzieć, co się dzieje
    private static final String SHARED_PREFS = "BudgetData"; // Nazwa pliku, gdzie trzymamy dane
    // *** Klucz do CZYTANIA mapy zapisanej przez ExpensesActivity ***
    private static final String DAILY_SAVINGS_KEY = "DailySavings"; // Klucz do mapy z wynikami <Data jako tekst, Wynik dnia>
    // Format daty MUSI być taki sam wszędzie (w Kalendarzu, w kluczach mapy)
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    // Do formatowania kasy na polski (złotówki)
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pl", "PL"));

    // --- Elementy interfejsu (widoki) ---
    private CalendarView calendarView; // Ten wypasiony kalendarz
    private TextView selectedDateTextView; // Tekst, gdzie pokazujemy wybraną datę
    private TextView dailyResultTextView; // Tu pokazujemy, ile kasy zostało (lub ile na minusie) danego dnia
    private BottomNavigationView bottomNavigationView; // Ten pasek nawigacji na dole

    // --- Dane ---
    private SharedPreferences sharedPreferences; // Do zapisywania/odczytywania danych z pamięci telefonu
    private Map<String, Double> dailySavingsMap; // Tu trzymamy dane: <Data jako tekst, Zaoszczędzona kwota>

    // --- Launchery do aparatu (nowy sposób obsługi wyników) ---
    // Ten czeka na wynik zrobienia zdjęcia
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Sprawdzamy, czy użytkownik zrobił zdjęcie (OK) i czy są jakieś dane
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras(); // Wyciągamy dodatki z wyniku
                    if (extras != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data"); // Wyciągamy samą bitmapę (zdjęcie)
                        if (imageBitmap != null) {
                            // Mamy zdjęcie, lecimy z nim do ExpensesActivity
                            launchExpensesActivityWithBitmap(imageBitmap);
                        } else {
                            // Coś poszło nie tak z bitmapą
                            showToast(getString(R.string.error_getting_image_bitmap));
                        }
                    } else {
                        // Nie ma dodatków w wyniku z aparatu? Dziwne.
                        showToast(getString(R.string.error_camera_data_extras));
                    }
                } else {
                    // Użytkownik anulował albo coś innego poszło nie tak
                    Log.w(TAG, "Wynik z aparatu nie był OK albo dane były puste");
                    // Można by tu pokazać toasta, że anulowano
                    // showToast(getString(R.string.camera_cancelled));
                }
            });

    // Ten launcher odpala się, jak pytamy o pozwolenie na aparat
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    // Użytkownik się zgodził, możemy odpalać aparat
                    openCamera();
                } else {
                    // Użytkownik odmówił :(
                    showToast(getString(R.string.toast_camera_permission_denied));
                }
            });
    // --- Koniec rzeczy od aparatu ---

    // --- Cykl życia aktywności (co się dzieje po kolei) ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Najpierw standardowe rzeczy
        setContentView(R.layout.activity_calendar); // Ustawiamy wygląd z pliku XML (sprawdź, czy na pewno istnieje!)

        initializeUI(); // Łączymy zmienne z widokami z XMLa
        sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE); // Otwieramy nasz plik z danymi
        loadDailySavingsMap(); // Ładujemy zapisane wcześniej wyniki dzienne
        setupCalendarView(); // Konfigurujemy kalendarz (zakres dat, klikanie)
        displayTodayDateAndSavings(); // Od razu pokazujemy info dla dzisiejszego dnia
        configureBottomNavigation(); // Ustawiamy działanie dolnego paska nawigacji
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Wracamy do apki, przeładowuję dane i odświeżam widok.");
        loadDailySavingsMap(); // Przeładuj dane, bo może coś się zmieniło w ExpensesActivity
        refreshDisplayForCurrentSelection(); // Odśwież widok dla aktualnie zaznaczonej daty
        // Upewnijmy się, że na dolnym pasku dalej podświetlony jest Kalendarz
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.navigation_calendar);
        }
    }

    // --- Inicjalizacja (przygotowanie rzeczy) ---
    private void initializeUI() {
        // Łączymy zmienne z ID elementów w pliku activity_calendar.xml
        calendarView = findViewById(R.id.calendarView);
        selectedDateTextView = findViewById(R.id.selectedDateTextView);
        dailyResultTextView = findViewById(R.id.expensesTextView); // To jest ID, gdzie pokazujemy wynik dnia
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Lepiej sprawdzić, czy wszystko się znalazło, bo inaczej będzie crash
        if (calendarView == null) {
            Log.e(TAG, "MASAKRA: Nie ma CalendarView o ID 'calendarView' w activity_calendar.xml!");
            // Można by tu pokazać błąd i zamknąć apkę
            Toast.makeText(this, "Błąd UI: Coś nie tak z kalendarzem.", Toast.LENGTH_LONG).show();
            // finish(); // Albo jakoś inaczej to ogarnąć
            return; // Nie idziemy dalej, bo bez kalendarza to bez sensu
        }
        if (selectedDateTextView == null) {
            Log.e(TAG, "MASAKRA: Nie ma TextView o ID 'selectedDateTextView' w activity_calendar.xml!");
            Toast.makeText(this, "Błąd UI: Brak pola na datę.", Toast.LENGTH_LONG).show();
        }
        if (dailyResultTextView == null) {
            Log.e(TAG, "MASAKRA: Nie ma TextView o ID 'expensesTextView' w activity_calendar.xml!");
            Toast.makeText(this, "Błąd UI: Brak pola na wynik.", Toast.LENGTH_LONG).show();
        }
        if (bottomNavigationView == null) {
            Log.e(TAG, "MASAKRA: Nie ma BottomNavigationView o ID 'bottom_navigation' w activity_calendar.xml!");
            Toast.makeText(this, "Błąd UI: Brak dolnej nawigacji.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Ustawia kalendarz (CalendarView):
     * - Określa, od kiedy do kiedy można wybierać daty (np. od poprzedniego miesiąca do teraz).
     * - Ustawia, żeby na starcie był zaznaczony dzisiejszy dzień.
     * - Podpina funkcję, która reaguje na kliknięcie dnia.
     */
    private void setupCalendarView() {
        if (calendarView == null) return; // Już sprawdzaliśmy, ale dla pewności

        Calendar today = Calendar.getInstance(); // Bierzemy dzisiejszą datę

        // --- START ZMIAN: Ustawiamy zakres dat ---
        // Data minimalna (Początek poprzedniego miesiąca)
        Calendar minDate = Calendar.getInstance();
        minDate.setTime(today.getTime()); // Kopiujemy dzisiejszą datę
        minDate.add(Calendar.MONTH, -1); // Cofamy o jeden miesiąc
        minDate.set(Calendar.DAY_OF_MONTH, 1); // Ustawiamy na pierwszy dzień tego miesiąca
        // Można też wyzerować godziny, minuty itp., żeby było równo od początku dnia
        minDate.set(Calendar.HOUR_OF_DAY, 0);
        minDate.set(Calendar.MINUTE, 0);
        minDate.set(Calendar.SECOND, 0);
        minDate.set(Calendar.MILLISECOND, 0);

        Log.d(TAG, "Ustawiam minimalną datę kalendarza na: " + DATE_FORMAT.format(minDate.getTime()));
        calendarView.setMinimumDate(minDate);

        // Data maksymalna (Opcjonalnie - np. koniec obecnego miesiąca albo trochę w przyszłość)
        // Dla przykładu ustawmy na koniec bieżącego miesiąca
        Calendar maxDate = Calendar.getInstance();
        maxDate.setTime(today.getTime()); // Kopiujemy dzisiejszą
        maxDate.set(Calendar.DAY_OF_MONTH, maxDate.getActualMaximum(Calendar.DAY_OF_MONTH)); // Ostatni dzień obecnego miesiąca
        // Można ustawić czas na sam koniec dnia
        maxDate.set(Calendar.HOUR_OF_DAY, 23);
        maxDate.set(Calendar.MINUTE, 59);
        maxDate.set(Calendar.SECOND, 59);
        maxDate.set(Calendar.MILLISECOND, 999);

        Log.d(TAG, "Ustawiam maksymalną datę kalendarza na: " + DATE_FORMAT.format(maxDate.getTime()));
        // Odkomentuj, jeśli chcesz zablokować przewijanie kalendarza w przyszłość
        // calendarView.setMaximumDate(maxDate);

        // --- KONIEC ZMIAN ---

        // Ustawiamy, żeby na starcie była zaznaczona dzisiejsza data - ważne, żeby zrobić to PO ustawieniu min/max daty
        try {
            Log.d(TAG, "Ustawiam startową datę w kalendarzu na: " + DATE_FORMAT.format(today.getTime()));
            calendarView.setDate(today); // Ustawiamy dzisiaj jako startową datę
        } catch (OutOfDateRangeException e) {
            // Ups, dzisiejsza data jest poza dozwolonym zakresem? Dziwne.
            Log.e(TAG, "Błąd przy ustawianiu startowej daty ("+ DATE_FORMAT.format(today.getTime()) +"). Chyba jest poza min/max.", e);
            // Spróbujmy ustawić minimalną dozwoloną datę jako awaryjną
            try {
                calendarView.setDate(minDate);
                Log.w(TAG, "Plan B: Ustawiono startową datę na minimum: " + DATE_FORMAT.format(minDate.getTime()));
            } catch (OutOfDateRangeException ex) {
                // No to już grubo, nawet minimalnej się nie dało ustawić
                Log.e(TAG, "Plan B zawiódł: Nawet minimalnej daty nie dało się ustawić.", ex);
            }
        }

        // Listener - co ma się dziać, jak klikniesz na jakiś dzień (tego nie ruszamy, było OK)
        calendarView.setOnDayClickListener(eventDay -> {
            Calendar clickedDayCalendar = eventDay.getCalendar(); // Kalendarz dla klikniętego dnia
            String selectedDate = DATE_FORMAT.format(clickedDayCalendar.getTime()); // Formatujemy datę na tekst
            Log.d(TAG, "Kliknięto datę: " + selectedDate);
            // Pokazujemy wybraną datę w TextView
            if (selectedDateTextView != null) {
                selectedDateTextView.setText(getString(R.string.selected_date_text, selectedDate));
            }
            // Wyświetlamy zapisany wynik dla tej klikniętej daty
            displaySavingsForDate(selectedDate);
        });
    }

    // --- Ogarnianie Danych ---
    /** Wczytuje mapę <Data jako tekst, Wynik dnia> z pamięci telefonu (SharedPreferences) */
    private void loadDailySavingsMap() {
        // Na wszelki wypadek, gdyby sharedPreferences było null
        if (sharedPreferences == null) {
            Log.w(TAG, "SharedPreferences było null w loadDailySavingsMap. Łapię instancję.");
            sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        }
        // Wyciągamy zapisany JSON, jak nie ma, to dajemy pusty obiekt "{}"
        String json = sharedPreferences.getString(DAILY_SAVINGS_KEY, "{}");
        Log.d(TAG, "loadDailySavingsMap() - Ładuję JSON z klucza '" + DAILY_SAVINGS_KEY + "': " + json);
        try {
            // Określamy typ danych: Mapa (HashMap) z kluczem String i wartością Double
            Type type = new TypeToken<HashMap<String, Double>>() {}.getType();
            // Używamy biblioteki Gson do przetworzenia JSONa na naszą mapę
            dailySavingsMap = new Gson().fromJson(json, type);

            // Jeśli po przetworzeniu mapa jest null (np. zły JSON), tworzymy nową, pustą
            if (dailySavingsMap == null) {
                dailySavingsMap = new HashMap<>();
                Log.w(TAG, "Mapa DailySavingsMap po parsowaniu była null. Tworzę nową, pustą.");
            } else {
                // Udało się wczytać, super!
                Log.i(TAG, "Pomyślnie załadowano DailySavingsMap. Liczba wpisów: " + dailySavingsMap.size());
                // Jakby były problemy, można odkomentować, żeby zobaczyć zawartość mapy w logach
                // for (Map.Entry<String, Double> entry : dailySavingsMap.entrySet()) {
                //     Log.v(TAG, "Wpis w mapie: " + entry.getKey() + " -> " + entry.getValue());
                // }
            }
        } catch (JsonSyntaxException e) {
            // Błąd w strukturze JSONa
            Log.e(TAG, "Błąd parsowania JSONa mapy oszczędności: " + json, e);
            dailySavingsMap = new HashMap<>(); // Dajemy pustą mapę, żeby apka się nie wywaliła dalej
            showToast(getString(R.string.error_loading_calendar_data));
        } catch (Exception e){ // Jakiś inny, niespodziewany błąd
            Log.e(TAG, "Nieoczekiwany błąd przy ładowaniu mapy oszczędności: " + json, e);
            dailySavingsMap = new HashMap<>(); // Też dajemy pustą mapę na wszelki wypadek
            showToast(getString(R.string.error_loading_calendar_data));
        }
    }

    /** Zwraca zapisany wynik dzienny dla podanej daty (jako tekst) z wczytanej mapy */
    private double getSavingsForDate(String date) {
        // Sprawdzamy, czy mapa w ogóle istnieje
        if (dailySavingsMap == null) {
            Log.e(TAG, "Wołano getSavingsForDate, ale dailySavingsMap jest null! Próbuję załadować ponownie.");
            loadDailySavingsMap(); // Spróbujmy ją załadować jeszcze raz
            // Sprawdźmy ponownie
            if (dailySavingsMap == null) {
                Log.e(TAG, "Ponowne ładowanie nie pomogło, dailySavingsMap dalej null. Zwracam 0.0 dla daty " + date);
                // Żeby się nie wywaliło, zwracamy domyślną wartość
                return 0.0;
            }
        }
        // getOrDefault jest super - jak znajdzie datę w mapie, to zwraca jej wartość,
        // a jak nie znajdzie, to zwraca wartość domyślną (tutaj 0.0).
        double savedValue = dailySavingsMap.getOrDefault(date, 0.0);
        Log.d(TAG, "getSavingsForDate(" + date + ") -> Wyciągnięta wartość z mapy: " + savedValue);
        return savedValue;
    }

    // --- Pokazywanie rzeczy na ekranie ---
    /** Wyświetla info (datę i wynik) dla dzisiejszego dnia */
    private void displayTodayDateAndSavings() {
        String todayDate = DATE_FORMAT.format(new Date()); // Bierzemy dzisiejszą datę i formatujemy
        // Ustawiamy tekst z dzisiejszą datą
        if (selectedDateTextView != null) {
            selectedDateTextView.setText(getString(R.string.selected_date_text, todayDate));
        } else {
            Log.w(TAG, "selectedDateTextView jest null, nie mogę pokazać dzisiejszej daty.");
        }
        // Wywołujemy funkcję do pokazania wyniku, ona sama sprawdzi, czy ma gdzie to pokazać
        displaySavingsForDate(todayDate);
        Log.d(TAG, "Wyświetlono startowe dane dla dzisiaj: " + todayDate);
    }

    /** Pokazuje zapisany wynik dla podanej daty (jako tekst) w odpowiednim TextView */
    private void displaySavingsForDate(String date) {
        // Sprawdzamy, czy mamy gdzie wyświetlić wynik
        if (dailyResultTextView == null) {
            Log.e(TAG,"dailyResultTextView jest null w displaySavingsForDate. Nie pokażę wyniku dla " + date);
            return; // Jak nie ma, to nic nie robimy
        }
        double savedValue = getSavingsForDate(date); // Bierzemy wynik z mapy
        String formattedResult = CURRENCY_FORMAT.format(savedValue); // Formatujemy na złotówki (np. 12,34 zł)

        // Dodajemy plusik z przodu, jeśli kwota jest dodatnia (i nie zero), żeby było czytelniej
        // Znak minusa dodaje się sam
        if (savedValue > 0) {
            formattedResult = "+" + formattedResult;
        }
        // Dla zera format może już mieć walutę, np. "0,00 zł"

        Log.d(TAG, "displaySavingsForDate(" + date + ") - Ustawiam tekst w TextView na: " + formattedResult);
        // Używamy stringa z zasobów, który ma miejsce na wstawienie naszego wyniku (%1$s)
        dailyResultTextView.setText(getString(R.string.daily_result_display, formattedResult));
    }

    /** Odświeża pokazany wynik na podstawie daty, która jest aktualnie w selectedDateTextView */
    private void refreshDisplayForCurrentSelection() {
        // Sprawdzamy, czy mamy TextView z datą
        if (selectedDateTextView == null) {
            Log.e(TAG, "selectedDateTextView jest null w refreshDisplay. Nie wiem, jaka data jest wybrana. Pokazuję dzisiejszą.");
            displayTodayDateAndSavings(); // W razie czego pokazujemy dzisiejszą
            return;
        }

        String fullText = selectedDateTextView.getText().toString(); // Bierzemy cały tekst z TextView
        String dateOnly = ""; // Tu będziemy trzymać samą datę

        try {
            // Próbujemy wyciągnąć samą datę z tekstu, np. z "Wybrana data: 15/07/2024"
            // Pobieramy szablon tekstu z zasobów, żeby wiedzieć, jaki jest początek
            String prefixTemplate = getString(R.string.selected_date_text, "DATE_PLACEHOLDER");
            String prefix = prefixTemplate.substring(0, prefixTemplate.indexOf("DATE_PLACEHOLDER")).trim(); // Wycinamy tekst przed datą

            // Sprawdzamy, czy tekst zaczyna się od oczekiwanego początku
            if (fullText.startsWith(prefix)) {
                // Jak tak, to wycinamy datę po tym początku
                dateOnly = fullText.substring(prefix.length()).trim();
                Log.d(TAG, "Wyciągnąłem datę po prefiksie: " + dateOnly);
            } else {
                // Jak nie, to może w TextView jest *tylko* data? Spróbujmy tak.
                Log.w(TAG, "Coś nie pasuje prefiks w refreshDisplay. Tekst: '" + fullText + "', Oczekiwany początek: '" + prefix + "'. Próbuję cały tekst jako datę.");
                dateOnly = fullText.trim();
            }

            // BARDZO WAŻNE: Sprawdzamy, czy wyciągnięty tekst wygląda jak data w naszym formacie (dd/MM/yyyy)
            DATE_FORMAT.setLenient(false); // Nie pozwalamy na luźne formaty (np. 32/13/2024)
            DATE_FORMAT.parse(dateOnly);   // Jak format jest zły, to tu poleci wyjątek ParseException

            // Jak doszliśmy tutaj, to data jest OK
            Log.d(TAG,"Odświeżam widok dla daty wyciągniętej z TextView: " + dateOnly);
            displaySavingsForDate(dateOnly); // Pokazujemy wynik dla tej daty

        } catch (ParseException pe) {
            // Format daty był zły
            Log.e(TAG, "Błąd parsowania wyciągniętej daty '" + dateOnly + "' z tekstu '" + fullText + "'. Zły format. Pokazuję dzisiejszą.", pe);
            displayTodayDateAndSavings(); // Wracamy do pokazywania dzisiejszej daty
        } catch (Exception e) { // Jakiś inny błąd
            Log.e(TAG, "Nieoczekiwany błąd przy wyciąganiu/sprawdzaniu daty z TextView: '" + fullText + "'. Pokazuję dzisiejszą.", e);
            displayTodayDateAndSavings(); // Też wracamy do dzisiejszej
        }
    }


    // --- Nawigacja (przechodzenie między ekranami) ---
    private void configureBottomNavigation() {
        // Znowu, upewnijmy się, że pasek nawigacji istnieje
        if (bottomNavigationView == null) {
            Log.e(TAG, "Nie mogę ustawić dolnej nawigacji - widok jest null.");
            return;
        }

        // Ustawiamy, co ma się dziać po kliknięciu ikonek na dole
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId(); // ID klikniętego elementu
            Intent intent = null; // Intencja do uruchomienia nowego ekranu
            boolean handled = false; // Czy obsłużyliśmy to kliknięcie?

            if (itemId == R.id.navigation_home) {
                // Kliknięto "Home"
                intent = new Intent(CalendarActivity.this, HomeActivity.class);
                handled = true;
            } else if (itemId == R.id.navigation_calendar) {
                // Kliknięto "Calendar" - jesteśmy już tutaj, nic nie rób
                handled = true;
                return true; // Zostajemy
            } else if (itemId == R.id.navigation_menu) { // Zakładamy, że to są Ustawienia
                // Kliknięto "Menu" (Ustawienia)
                intent = new Intent(CalendarActivity.this, SettingsActivity.class);
                handled = true;
            } else if (itemId == R.id.navigation_camera) {
                // KLIKNIĘTO APARAT!
                Log.d(TAG, "Kliknięto przycisk aparatu na dole.");
                checkCameraPermissionAndOpenCamera(); // Odpalamy logikę aparatu
                // WAŻNE! Zwracamy 'false', żeby zakładka Kalendarz dalej była podświetlona.
                // Uruchomienie aparatu to tylko akcja, nie przechodzimy na stałe do innego "ekranu" w nawigacji.
                return false; // <<< Ten wybór NIE jest obsłużony przez zmianę ekranu/fragmentu
            }

            // Jeśli przygotowaliśmy jakiś Intent (czyli przechodzimy do innego ekranu)
            if (intent != null) {
                // Standardowe flagi, żeby nie tworzyć wielu kopii tego samego ekranu
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent); // Odpalamy nowy ekran
                overridePendingTransition(0, 0); // Wyłączamy animację przejścia (żeby było szybciej)
                return true; // Obsłużyliśmy kliknięcie, uruchamiając nową aktywność
            }
            // Jak tu doszliśmy, a handled jest false, to kliknięto coś nieznanego
            Log.w(TAG, "Nieobsługiwany element dolnej nawigacji kliknięty: " + item.getTitle());
            return false; // Nie obsłużyliśmy tego
        });

        // Na starcie/po powrocie upewnijmy się, że ikonka Kalendarza jest podświetlona
        bottomNavigationView.setSelectedItemId(R.id.navigation_calendar);
    }


    // --- Logika odpalania aparatu ---
    private void checkCameraPermissionAndOpenCamera() {
        // Sprawdzamy, czy mamy już pozwolenie na używanie aparatu
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Mamy pozwolenie, super! Odpalamy aparat.
            Log.d(TAG, "Pozwolenie na aparat już jest. Otwieram aparat.");
            openCamera();
        } else {
            // Nie mamy pozwolenia, trzeba poprosić użytkownika.
            // Wynik prośby obsłuży nasz permissionLauncher.
            Log.d(TAG, "Nie ma pozwolenia na aparat. Pytam użytkownika.");
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        // Tworzymy intencję (prośbę) o zrobienie zdjęcia
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Sprawdzamy, czy w telefonie jest jakakolwiek apka, która potrafi robić zdjęcia
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                // Jest apka, odpalamy ją za pomocą naszego cameraLaunchera
                Log.d(TAG, "Odpalam intencję aparatu...");
                cameraLauncher.launch(takePictureIntent);
            } catch (Exception e) {
                // Jakiś błąd przy odpalaniu (np. brak uprawnień systemowych?)
                Log.e(TAG, "Nie udało się odpalić intencji aparatu", e);
                showToast(getString(R.string.error_camera_launch));
            }
        } else {
            // W telefonie nie ma apki aparatu (np. na emulatorze)
            showToast(getString(R.string.error_no_camera_app));
            Log.w(TAG, "Nie ma żadnej apki do obsługi intencji ACTION_IMAGE_CAPTURE.");
        }
    }

    /** Przekazuje zrobione zdjęcie (Bitmapę) do ekranu ExpensesActivity do dalszej analizy */
    private void launchExpensesActivityWithBitmap(Bitmap bitmap) {
        // Sprawdzamy, czy na pewno dostaliśmy zdjęcie
        if (bitmap == null) {
            Log.e(TAG, "Bitmapa przekazana do launchExpensesActivityWithBitmap jest null. Nic nie robię.");
            showToast(getString(R.string.error_processing_image)); // Jakiś ogólny błąd
            return;
        }
        Log.d(TAG, "Odpalam ExpensesActivity ze zrobionym zdjęciem.");
        Intent intent = new Intent(this, ExpensesActivity.class);
        // Używamy stałego klucza do przekazania bitmapy. ExpensesActivity musi używać tego samego klucza!
        // Najlepiej zdefiniować go gdzieś publicznie (np. w pliku Constants albo w HomeActivity jak było wcześniej).
        intent.putExtra(HomeActivity.EXTRA_BITMAP_FOR_PROCESSING, bitmap);
        // Jak ExpensesActivity już działa, to tylko przenieś ją na wierzch, zamiast tworzyć nową.
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent); // Odpalamy ExpensesActivity
    }
    // --- Koniec rzeczy od aparatu ---

    // --- Pomocnicze drobiazgi ---
    /** Pokazuje krótki dymek z wiadomością na dole ekranu (Toast) */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Jakby co, można zrobić też wersję do dłuższych wiadomości
    // private void showLongToast(String message) {
    //     Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    // }
}