package com.example.cashmeinv3;

// Rzeczy z Androida i żeby działało na różnych wersjach
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat; // Do proszenia o pozwolenia
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

// Importy do Material Design (żeby ładnie wyglądało)
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

// Importy do Google ML Kit Vision (do rozpoznawania tekstu)
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text; // Nie używamy bezpośrednio, ale może się przydać do rozbudowy
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

// Importy do ogarniania JSONa (zapis/odczyt danych)
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

// Standardowe rzeczy z Javy
import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Zakładamy, że BaseActivity istnieje, inaczej dziedziczymy po AppCompatActivity
public class ExpensesActivity extends BaseActivity { // Albo AppCompatActivity

    // --- Stałe, żeby nie pisać tego wszędzie ---
    private static final String TAG = "ExpensesActivity"; // Do logów, żeby wiedzieć co się dzieje
    private static final String SHARED_PREFS = "BudgetData"; // Wspólna nazwa pliku z danymi
    // Klucze do SharedPreferences (MUSZĄ być takie same we wszystkich aktywnościach!)
    private static final String DAILY_SAVINGS_KEY = "DailySavings"; // Mapa<Data jako tekst, Wynik dnia>
    private static final String SCANNED_ITEMS_LIST_KEY = "ScannedItemsList"; // Lista<Map<String, String>> z dzisiejszymi produktami
    private static final String MONTHLY_INCOME_KEY = "monthlyIncome"; // float (przychód)
    private static final String MONTHLY_EXPENSE_TARGET_KEY = "monthlyExpenseTarget"; // float (cel wydatków)
    private static final String LAST_UPDATE_DATE_KEY = "lastUpdateDate"; // String "dd/MM/yyyy" (data ostatniej aktualizacji)
    private static final String ROLLOVER_KEY = "previousDayRollover"; // float (ile zostało z wczoraj)
    private static final String DAILY_ALLOWANCE_KEY = "dailyAllowance"; // float (ile można wydać dzisiaj)
    // *** DODANE: Klucz do flagi aktualizacji budżetu ***
    private static final String BUDGET_UPDATED_FLAG_KEY = "budget_updated_flag"; // boolean (czy budżet był zmieniony w Home?)
    // Stałe do powiadomień
    private static final String NOTIFICATION_CHANNEL_ID = "MonthlySummaryChannel"; // ID kanału powiadomień
    private static final int MONTHLY_SUMMARY_NOTIFICATION_ID = 1001; // ID samego powiadomienia
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 123; // Kod do pytania o pozwolenie na powiadomienia
    // Formatowanie
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()); // Format daty dd/MM/yyyy
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pl", "PL")); // Format kasy na PLN

    // --- Elementy interfejsu (widoki) ---
    private TextView dailyBudgetTextView, monthlyBudgetTextView, savingsTextView, totalScannedItemsValueTextView;
    private ListView scannedItemsListView; // Lista zeskanowanych produktów
    private MaterialButton viewExpensesButton, addProductButton, confirmAddProductButton; // Przyciski
    private EditText productNameEditText, productPriceEditText; // Pola do wpisywania nazwy i ceny
    private Spinner productCategorySpinner; // Rozwijana lista kategorii
    private LinearLayout addProductContainer; // Kontener na formularz dodawania produktu
    private BottomNavigationView bottomNavigationView; // Dolny pasek nawigacji

    // --- Dane i Stan Aplikacji ---
    private ArrayList<HashMap<String, String>> scannedItemsList = new ArrayList<>(); // Lista zeskanowanych/dodanych rzeczy
    private double dailyAllowance; // Ile kasy na dzisiaj
    private double monthlyIncome; // Miesięczny przychód
    private double monthlyExpenseTarget; // Miesięczny cel wydatków
    private double previousDayRollover; // Ile zostało/przekroczyło się z wczoraj
    private String lastUpdateDate; // Przechowuje datę ("dd/MM/yyyy") ostatniego obliczenia
    private boolean isScannedItemsListVisible = false; // Czy lista produktów jest widoczna?
    private CustomAdapter scannedItemsAdapter; // Adapter do naszej listy
    private SharedPreferences sharedPreferences; // Do zapisywania/odczytywania danych

    // --- Activity Result Launchers (nowy sposób na wyniki z innych aktywności) ---
    // Do aparatu (zrobienie zdjęcia)
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Sprawdzamy, czy użytkownik zrobił zdjęcie (OK) i czy są jakieś dane
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras(); // Wyciągamy dodatki z wyniku
                    if (extras != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data"); // Wyciągamy samą bitmapę (zdjęcie)
                        if (imageBitmap != null) {
                            Log.d(TAG, "Zdjęcie (bitmapa) zrobione pomyślnie przez wewnętrzny launcher aparatu.");
                            processReceipt(imageBitmap); // Od razu przetwarzamy paragon
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
                    Log.w(TAG, "Wynik z aparatu nie był OK albo dane były puste (wewnętrzny launcher)");
                }
            });

    // Do pytania o pozwolenie na powiadomienia (Android 13+)
    // (Logika pytania jest bezpośrednio w requestNotificationPermissionIfNeeded używając requestPermissions)


    // --- Metody Cyklu Życia Aktywności ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Używamy layoutu zrobionego specjalnie dla ekranu Wydatków
        setContentView(R.layout.activity_expenses);

        // Ustawienia zanim zaczniemy grzebać w UI albo danych
        createNotificationChannel(); // Trzeba to zrobić wcześnie
        requestNotificationPermissionIfNeeded(); // Sprawdzamy/pytamy o pozwolenie na powiadomienia

        // Inicjalizujemy komponenty
        initializeUI(); // Łączymy zmienne z widokami z XMLa
        sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE); // Otwieramy nasz plik z danymi
        loadData(); // Ładujemy budżet, ostatnią datę, dzisiejsze produkty
        setupScannedItemsListAdapter(); // Ustawiamy adapter listy
        setupCategorySpinner(); // Ustawiamy spinner kategorii
        // Uwaga: Kolejność w onResume jest teraz ważniejsza, początkowe sprawdzenie tutaj może użyć starych danych, jeśli ustawiono flagę aktualizacji
        // checkAndCalculateNewDay(); // Wstępne sprawdzenie przeniesione głównie do onResume dla spójności
        updateUI(); // Robimy pierwsze odświeżenie interfejsu

        // Ustawiamy listenery (co ma się dziać po kliknięciu)
        setupButtonClickListeners();
        configureBottomNavigationView(); // Ustawiamy dolny pasek nawigacji

        // Ogarniamy ewentualną bitmapę przekazaną z innej aktywności (np. z Home)
        handleIntent(getIntent()); // <<< Wywołanie handleIntent >>>

        // Robimy wstępne sprawdzenie dnia po ustawieniu wszystkiego, zanim user zacznie klikać
        // To zapewnia, że rollover/limit są poprawne przy pierwszym załadowaniu
        checkAndCalculateNewDay();
        updateUI(); // Znowu odświeżamy UI po sprawdzeniu
    }

    // --- ZMODYFIKOWANE onResume ---
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume odpalone.");

        // 1. Załaduj kluczowe dane (potrzebne zarówno do sprawdzenia aktualizacji, jak i sprawdzenia dnia)
        loadCoreBudgetData();
        Log.d(TAG, "Przeładowano Podstawowy Budżet w onResume - Przychód: " + monthlyIncome + ", Cel: " + monthlyExpenseTarget);

        // 2. Sprawdź, czy budżet był aktualizowany w HomeActivity i zresetuj, jeśli trzeba
        checkForBudgetUpdateAndReset(); // <<< Sprawdza flagę, przelicza dzisiejszy limit, jeśli trzeba

        // 3. Wykonaj standardowe sprawdzenie dzienne (zmiana dnia, obliczenie rollover)
        //    Użyje to potencjalnie zaktualizowanego stanu z kroku 2.
        checkAndCalculateNewDay();

        // 4. Zaktualizuj UI na podstawie ostatecznego stanu
        updateUI();
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent odpalone");
        setIntent(intent); // Zaktualizuj intencję aktywności na nową
        handleIntent(intent); // <<< Wywołanie handleIntent >>>
    }

    // --- Metody Inicjalizacyjne ---
    private void initializeUI() {
        Log.d(TAG, "Inicjalizuję elementy UI...");
        // Znajdujemy wszystkie widoki po ich ID z pliku R.layout.activity_expenses
        dailyBudgetTextView = findViewById(R.id.dailyBudgetTextView);
        monthlyBudgetTextView = findViewById(R.id.monthlyBudgetTextView);
        savingsTextView = findViewById(R.id.savingsTextView); // Pokazuje ile zostało z wczoraj (rollover)
        totalScannedItemsValueTextView = findViewById(R.id.totalScannedItemsValueTextView); // Pokazuje sumę dzisiejszych wydatków

        scannedItemsListView = findViewById(R.id.scannedItemsListView);
        addProductContainer = findViewById(R.id.addProductContainer);
        productNameEditText = findViewById(R.id.productNameEditText);
        productPriceEditText = findViewById(R.id.productPriceEditText);
        productCategorySpinner = findViewById(R.id.productCategorySpinner);

        viewExpensesButton = findViewById(R.id.viewExpensesButton);
        addProductButton = findViewById(R.id.addProductButton);
        confirmAddProductButton = findViewById(R.id.confirmAddProductButton);

        bottomNavigationView = findViewById(R.id.bottomNavigation); // Poprawne ID dla layoutu Expenses

        // Ustawiamy początkową widoczność
        scannedItemsListView.setVisibility(View.GONE); // Lista na start schowana
        addProductContainer.setVisibility(View.GONE); // Formularz dodawania też schowany
        viewExpensesButton.setText(getString(R.string.show_expenses)); // Tekst przycisku "Pokaż wydatki"
        Log.d(TAG, "Inicjalizacja UI zakończona.");
    }

    private void setupCategorySpinner() {
        // Bierzemy kategorie z pliku arrays.xml
        List<String> categories = Arrays.asList(getResources().getStringArray(R.array.categories_array));
        // Używamy własnego layoutu dla elementów spinnera
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.custom_spinner_item, // Layout dla każdego elementu na liście
                R.id.spinner_text_item,     // ID TextView wewnątrz tego layoutu
                categories
        );
        // Layout dla samego widoku rozwijanego
        adapter.setDropDownViewResource(R.layout.custom_spinner_item); // Może być ten sam albo inny
        productCategorySpinner.setAdapter(adapter);
        productCategorySpinner.setSelection(0, false); // Wybieramy pierwszy element (podpowiedź) na starcie, bez odpalania listenerów
        Log.d(TAG,"Spinner kategorii ustawiony.");
    }

    private void setupScannedItemsListAdapter() {
        scannedItemsAdapter = new CustomAdapter(); // Tworzymy nasz własny adapter
        scannedItemsListView.setAdapter(scannedItemsAdapter); // Podpinamy go do ListView
        // Wysokość ustawiana dynamicznie w updateUI, gdy lista jest widoczna
        Log.d(TAG, "CustomAdapter podpięty do ListView.");
    }

    private void setupButtonClickListeners() {
        // Co ma się dziać po kliknięciu przycisków
        viewExpensesButton.setOnClickListener(v -> toggleScannedItemsListVisibility()); // Pokaż/ukryj listę
        addProductButton.setOnClickListener(v -> toggleAddProductVisibility()); // Pokaż/ukryj formularz dodawania
        confirmAddProductButton.setOnClickListener(v -> confirmAddProduct()); // Zatwierdź dodanie produktu
    }

    // --- Ładowanie i Zapisywanie Danych ---
    private void loadData() {
        Log.d(TAG, "loadData odpala się...");
        loadCoreBudgetData();        // Ładujemy dane budżetowe, ostatnią datę, rollover, dzisiejszy limit
        loadScannedItemsFromPrefs(); // Ładujemy listę produktów dla aktualnej 'lastUpdateDate'
        Log.d(TAG, "loadData zakończone. Dane załadowane dla daty: " + (lastUpdateDate == null || lastUpdateDate.isEmpty() ? "Brak (Pierwsze uruchomienie?)" : lastUpdateDate));
    }

    private void loadCoreBudgetData() {
        // Na wszelki wypadek, gdyby sharedPreferences było null
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        }
        // Ładujemy wartości, jak nie ma, to dajemy domyślne 0.0f
        monthlyIncome = sharedPreferences.getFloat(MONTHLY_INCOME_KEY, 0.0f);
        monthlyExpenseTarget = sharedPreferences.getFloat(MONTHLY_EXPENSE_TARGET_KEY, 0.0f);
        previousDayRollover = sharedPreferences.getFloat(ROLLOVER_KEY, 0.0f);
        dailyAllowance = sharedPreferences.getFloat(DAILY_ALLOWANCE_KEY, 0.0f);
        lastUpdateDate = sharedPreferences.getString(LAST_UPDATE_DATE_KEY, ""); // Ważne: Ładujemy ostatnią przetworzoną datę
        Log.d(TAG, "Załadowano Podstawowy Budżet: Przychód="+CURRENCY_FORMAT.format(monthlyIncome)+
                ", Cel="+CURRENCY_FORMAT.format(monthlyExpenseTarget)+
                ", Rollover="+CURRENCY_FORMAT.format(previousDayRollover)+
                ", Limit="+CURRENCY_FORMAT.format(dailyAllowance)+
                ", OstatniaData="+lastUpdateDate);
    }

    private void loadScannedItemsFromPrefs() {
        // Znowu, sprawdzamy czy mamy SharedPreferences
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        }
        // Wyciągamy JSONa z listą, jak nie ma, to dajemy pustą listę "[]"
        String scannedItemsJson = sharedPreferences.getString(SCANNED_ITEMS_LIST_KEY, "[]");
        scannedItemsList.clear(); // Czyścimy obecną listę przed załadowaniem nowej
        try {
            // Określamy typ: Lista (ArrayList) Map (HashMap) String->String
            Type type = new TypeToken<ArrayList<HashMap<String, String>>>() {}.getType();
            // Używamy Gson do przerobienia JSONa na naszą listę
            ArrayList<HashMap<String, String>> loadedList = new Gson().fromJson(scannedItemsJson, type);
            if (loadedList != null) {
                scannedItemsList.addAll(loadedList); // Dodajemy załadowane elementy do naszej listy
                Log.d(TAG, "Załadowano " + scannedItemsList.size() + " produktów z SharedPreferences dla klucza " + SCANNED_ITEMS_LIST_KEY);
            } else {
                Log.w(TAG, "Załadowano null listę z SharedPreferences dla zeskanowanych produktów. Lista pozostaje pusta.");
            }
        } catch (JsonSyntaxException e) {
            // Błąd w strukturze JSONa
            Log.e(TAG, "Błąd parsowania JSONa zeskanowanych produktów: " + scannedItemsJson, e);
            showToast(getString(R.string.error_loading_items_list));
        } catch (Exception e) { // Jakiś inny, niespodziewany błąd
            Log.e(TAG, "Nieoczekiwany błąd przy ładowaniu zeskanowanych produktów", e);
            showToast(getString(R.string.error_loading_items_list));
        }
    }

    /**
     * Zapisuje dane aplikacji do SharedPreferences.
     * @param saveAll Jeśli true, zapisuje wszystkie dane budżetowe razem z listą i datą.
     *                Jeśli false, zapisuje tylko listę produktów i datę ostatniej aktualizacji (np. po dodaniu/usunięciu produktu).
     */
    private void saveData(boolean saveAll) {
        if (sharedPreferences == null) {
            Log.e(TAG,"SharedPreferences jest null w saveData. Nie mogę zapisać.");
            return;
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(LAST_UPDATE_DATE_KEY, lastUpdateDate); // Zawsze zapisujemy znacznik daty

        // Zawsze zapisujemy aktualny stan listy produktów dla 'lastUpdateDate'
        String listJson = new Gson().toJson(scannedItemsList);
        editor.putString(SCANNED_ITEMS_LIST_KEY, listJson);

        if (saveAll) { // Zapisujemy wszystko (zwykle na nowy dzień albo przy pierwszym uruchomieniu)
            editor.putFloat(MONTHLY_INCOME_KEY, (float) monthlyIncome);
            editor.putFloat(MONTHLY_EXPENSE_TARGET_KEY, (float) monthlyExpenseTarget);
            editor.putFloat(ROLLOVER_KEY, (float) previousDayRollover); // Zapisujemy rollover obliczony *dla* nowego dnia
            editor.putFloat(DAILY_ALLOWANCE_KEY, (float) dailyAllowance); // Zapisujemy limit obliczony *dla* nowego dnia
            Log.i(TAG, "Zapisuję WSZYSTKIE dane - Data: " + lastUpdateDate +
                    ", Limit: " + CURRENCY_FORMAT.format(dailyAllowance) +
                    ", Rollover: " + CURRENCY_FORMAT.format(previousDayRollover) +
                    ", Przychód: " + CURRENCY_FORMAT.format(monthlyIncome) +
                    ", Cel: " + CURRENCY_FORMAT.format(monthlyExpenseTarget) +
                    ", RozmiarListy: " + scannedItemsList.size());
        } else { // Zapisujemy tylko listę i datę (np. po dodaniu/usunięciu produktu, skanowaniu paragonu)
            Log.d(TAG, "Zapisuję tylko listę (" + scannedItemsList.size() + " produktów) i datę (" + lastUpdateDate + "). Lista JSON: " + listJson);
        }
        editor.apply(); // Zapis asynchroniczny (nie blokuje głównego wątku)
    }

    /**
     * Zapisuje obliczony dzienny wynik (oszczędności/deficyt) do współdzielonej mapy w SharedPreferences.
     * @param dateStr Ciąg daty ("dd/MM/yyyy"), dla której obliczono wynik.
     * @param result Obliczone oszczędności (dodatnie) lub deficyt (ujemny).
     */
    private void saveDailyResult(String dateStr, double result) {
        // Upewniamy się, że mamy SharedPreferences
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // Ładujemy obecną mapę wyników jako JSON
        String savingsJson = sharedPreferences.getString(DAILY_SAVINGS_KEY, "{}");
        // Określamy typ: Mapa (HashMap) String -> Double
        Type type = new TypeToken<HashMap<String, Double>>() {}.getType();
        HashMap<String, Double> dailySavingsMap;
        try {
            dailySavingsMap = new Gson().fromJson(savingsJson, type);
            // Jak mapa jest null (np. zły JSON), tworzymy nową
            if (dailySavingsMap == null) {
                dailySavingsMap = new HashMap<>();
                Log.w(TAG,"Sparsoana mapa dziennych oszczędności była null, stworzono nową.");
            }
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Błąd parsowania JSONa mapy dziennych oszczędności: " + savingsJson, e);
            dailySavingsMap = new HashMap<>(); // Zaczynamy od nowa, jak JSON jest zepsuty
        } catch (Exception e) {
            Log.e(TAG, "Nieoczekiwany błąd przy ładowaniu mapy dziennych oszczędności, tworzę nową mapę.", e);
            dailySavingsMap = new HashMap<>();
        }

        dailySavingsMap.put(dateStr, result); // Dodajemy/Aktualizujemy wpis dla konkretnej daty
        String updatedJson = new Gson().toJson(dailySavingsMap); // Konwertujemy zaktualizowaną mapę z powrotem na JSON
        editor.putString(DAILY_SAVINGS_KEY, updatedJson); // Zapisujemy nowego JSONa
        editor.apply(); // Zapisujemy zmiany asynchronicznie
        Log.i(TAG, "Zapisano wynik " + CURRENCY_FORMAT.format(result) + " dla daty " + dateStr + " do DailySavingsMap.");
        Log.d(TAG, "Zaktualizowany '" + DAILY_SAVINGS_KEY + "' JSON: " + updatedJson);
    }

    // --- Główna Logika Dzienna ---

    // *** DODANE: Metoda do obsługi aktualizacji budżetu z HomeActivity ***
    /**
     * Sprawdza w SharedPreferences flagę wskazującą, że budżet został zaktualizowany w HomeActivity.
     * Jeśli flaga jest ustawiona, czyści dzisiejszą listę wydatków, przelicza dzisiejszy limit
     * na podstawie nowych danych budżetowych (ale zachowując istniejący rollover), zapisuje nowy stan
     * i resetuje flagę.
     */
    private void checkForBudgetUpdateAndReset() {
        // Sprawdźmy SharedPreferences
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        }
        // Sprawdzamy, czy flaga jest ustawiona (domyślnie false)
        boolean budgetWasUpdated = sharedPreferences.getBoolean(BUDGET_UPDATED_FLAG_KEY, false);

        if (budgetWasUpdated) {
            Log.i(TAG, "Wykryto flagę aktualizacji budżetu. Resetuję dzisiejszy stan wydatków.");

            // Upewniamy się, że mamy najnowsze dane budżetowe (już załadowane w onResume -> loadCoreBudgetData)
            Log.d(TAG, "Używam zaktualizowanego budżetu - Przychód: " + monthlyIncome + ", Cel: " + monthlyExpenseTarget);

            // 1. Czyścimy dzisiejszą listę produktów
            scannedItemsList.clear();
            Log.d(TAG, "Dzisiejsza lista produktów wyczyszczona.");

            // 2. Resetujemy flagę natychmiast
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(BUDGET_UPDATED_FLAG_KEY, false);
            // Jeszcze nie zapisujemy, zrobimy to razem z innymi danymi poniżej

            // 3. Przeliczamy dzisiejszy limit używając NOWEGO budżetu, ale ISTNIEJĄCEGO rollovera
            //    Upewnijmy się, że lastUpdateDate jest poprawna (powinna być dzisiejsza data, jeśli tu jesteśmy)
            String todayDate = DATE_FORMAT.format(new Date());
            if (lastUpdateDate == null || !lastUpdateDate.equals(todayDate)) {
                Log.w(TAG, "lastUpdateDate (" + lastUpdateDate + ") to nie dzisiaj (" + todayDate + ") podczas resetu budżetu. Ustawiam na dzisiaj.");
                lastUpdateDate = todayDate; // Upewniamy się, że liczymy dla dzisiaj
            }
            // Używamy aktualnie załadowanego previousDayRollover (z wczoraj)
            dailyAllowance = calculateDailyAllowance(lastUpdateDate, previousDayRollover);
            Log.d(TAG, "Przeliczono dzisiejszy limit dla " + lastUpdateDate + " po aktualizacji budżetu: " + CURRENCY_FORMAT.format(dailyAllowance));

            // 4. Zapisujemy *cały* obecny stan (nowy limit, istniejący rollover, dzisiejsza data, PUSTA lista)
            //    Wywołujemy saveData(true), co również zapisze zresetowaną flagę.
            saveData(true);

            // 5. UI zostanie zaktualizowane przez główne wywołanie updateUI w onResume
            Log.i(TAG, "Reset dzisiejszego stanu wydatków zakończony z powodu aktualizacji budżetu.");

        } else {
            Log.d(TAG, "Nie wykryto flagi aktualizacji budżetu.");
        }
    }


    /**
     * Sprawdza, czy bieżąca data różni się od ostatnio przetwarzanej daty.
     * Jeśli tak (nowy dzień): Oblicza i zapisuje wynik poprzedniego dnia, sprawdza koniec miesiąca,
     *                   oblicza nowy limit/rollover, czyści listę, zapisuje stan.
     * Jeśli nie (ten sam dzień): Nic nie robi związanego z dziennym obliczeniem.
     */
    private void checkAndCalculateNewDay() {
        String currentDateStr = DATE_FORMAT.format(new Date()); // Dzisiejsza data jako tekst
        // Upewnij się, że lastUpdateDate jest załadowana przed tym sprawdzeniem
        if (lastUpdateDate == null) {
            Log.w(TAG, "checkAndCalculateNewDay odpalone, ale lastUpdateDate jest null. Próba odzyskania.");
            loadCoreBudgetData(); // Spróbujmy załadować ponownie
            if (lastUpdateDate == null || lastUpdateDate.isEmpty()) { // Sprawdź, czy dalej null/pusta po próbie ładowania
                Log.i(TAG, "checkAndCalculateNewDay: Ogarniam scenariusz pierwszego uruchomienia.");
                lastUpdateDate = currentDateStr; // Ustawiamy na dzisiaj
                previousDayRollover = 0.0; // Na start zero
                dailyAllowance = calculateDailyAllowance(currentDateStr, previousDayRollover); // Obliczamy limit
                scannedItemsList.clear(); // Lista na start pusta
                saveData(true); // Zapisujemy wszystko
                return; // Wychodzimy po obsłużeniu pierwszego uruchomienia
            }
            Log.d(TAG, "Odzyskano lastUpdateDate: " + lastUpdateDate);
        }

        Log.d(TAG, "checkAndCalculateNewDay - Dzisiejsza Data: " + currentDateStr + ", Ostatnia Data Aktualizacji: " + lastUpdateDate);

        // --- Logika Zmiany Dnia ---
        if (!currentDateStr.equals(lastUpdateDate)) {
            // Mamy nowy dzień!
            Log.i(TAG, "Wykryto nowy dzień. Przetwarzam koniec dnia dla: " + lastUpdateDate + " -> Nowy dzień: " + currentDateStr);

            // --- Oblicz i Zapisz Wynik Wczorajszego Dnia ---
            // scannedItemsList powinna w tym momencie zawierać produkty z 'lastUpdateDate'
            double yesterdaysSpending = calculateTotalExpenses(); // Ile wydano wczoraj
            // Pobierz limit, który był zapisany dla 'lastUpdateDate'
            double yesterdaysAllowance = sharedPreferences.getFloat(DAILY_ALLOWANCE_KEY, 0.0f);
            // Oblicz, ile faktycznie zostało/przekroczono z wczoraj
            double yesterdaysResult = yesterdaysAllowance - yesterdaysSpending;

            Log.d(TAG, "Koniec Dnia (" + lastUpdateDate + ") Obliczenie: Limit=" + CURRENCY_FORMAT.format(yesterdaysAllowance)
                    + ", Wydatki=" + CURRENCY_FORMAT.format(yesterdaysSpending)
                    + ", Wynik Końcowy=" + CURRENCY_FORMAT.format(yesterdaysResult));

            // *** Ustaw rollover na NOWY dzień na podstawie WYNIKU WCZORAJSZEGO dnia ***
            previousDayRollover = yesterdaysResult;
            Log.d(TAG, ">>> Rollover na dzisiaj (" + currentDateStr + ") ustawiony na: " + CURRENCY_FORMAT.format(previousDayRollover));

            // Zapisz wynik *poprzedniego* dnia do mapy dla Kalendarza
            saveDailyResult(lastUpdateDate, yesterdaysResult);

            // --- Sprawdź Koniec Miesiąca i Wyślij Powiadomienie ---
            checkForMonthEndAndNotify(lastUpdateDate, currentDateStr);

            // --- Przygotuj na Dzisiaj (nowy bieżący dzień) ---
            // Oblicz dzisiejszy limit używając NOWEJ wartości rollovera
            dailyAllowance = calculateDailyAllowance(currentDateStr, previousDayRollover);
            Log.d(TAG, "Obliczono dzisiejszy (" + currentDateStr + ") limit (z nowym rolloverem): " + CURRENCY_FORMAT.format(dailyAllowance));

            scannedItemsList.clear(); // Wyczyść listę produktów NA DZISIAJ
            String previousLastUpdateDate = lastUpdateDate; // Zachowaj dla jasności logów
            lastUpdateDate = currentDateStr; // Zaktualizuj znacznik daty na dzisiaj

            // Zapisz kompletny nowy stan na dzisiaj
            saveData(true); // Zapisuje: nowy limit, nowy rollover, nową datę, PUSTĄ listę

            Log.i(TAG, "Przeniesienie dzienne zakończone. Stan zapisany na nowy dzień: " + lastUpdateDate + ". Poprzedni przetworzony dzień: " + previousLastUpdateDate);
            // updateUI(); // UI jest aktualizowane później w onResume

        } else {
            // Ten sam dzień
            Log.d(TAG, "Ten sam dzień ("+ currentDateStr +"). Pomięto dzienne obliczenia/zapis.");
        }
    }

    /**
     * Oblicza dzienny limit na podstawie (Przychód - Cel Wydatków) / Dni + Rollover.
     * To reprezentuje docelowe dzienne *oszczędności* plus kwotę przeniesioną.
     */
    private double calculateDailyAllowance(String dateStr, double rollover) {
        // --- POCZĄTEK POPRAWIONEJ METODY ---
        Calendar cal = Calendar.getInstance();
        try {
            // Próbujemy sparsować datę, żeby wiedzieć, ile dni ma miesiąc
            Date date = DATE_FORMAT.parse(dateStr);
            if (date != null) cal.setTime(date);
            else cal.setTime(new Date()); // Jak się nie uda, bierzemy dzisiejszą datę
        } catch (ParseException e) {
            Log.e(TAG, "Błąd parsowania daty do obliczenia limitu: " + dateStr, e);
            cal.setTime(new Date()); // Jak błąd, to też bierzemy dzisiejszą
        }

        // Ile dni ma dany miesiąc
        int totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Obliczamy Planowane Miesięczne Oszczędności Netto
        double netMonthlySavings = monthlyIncome - monthlyExpenseTarget;

        Log.d(TAG, "Obliczam limit dla " + dateStr +
                ": Przychód=" + CURRENCY_FORMAT.format(monthlyIncome) +
                ", CelWydatków=" + CURRENCY_FORMAT.format(monthlyExpenseTarget) +
                ", OszczędnościNetto=" + CURRENCY_FORMAT.format(netMonthlySavings) + // Logujemy oszczędności netto
                ", LiczbaDni=" + totalDaysInMonth +
                ", Rollover=" + CURRENCY_FORMAT.format(rollover));

        if (totalDaysInMonth <= 0) {
            Log.e(TAG,"Nieprawidłowa liczba dni w miesiącu: " + totalDaysInMonth + ". Limit oparty tylko na rolloverze.");
            // Zwróć tylko rollover, jeśli obliczenie dni jest niemożliwe
            // Zastanów się, czy rollover powinien być ograniczony do 0, jeśli jest ujemny
            return rollover; // Lub Math.max(0.0, rollover), jeśli nie chcemy ujemnego limitu
        }

        // Obliczamy Dzienną Część Oszczędności Netto
        double dailyNetSavingsPortion = 0.0;
        // Unikamy dzielenia przez zero, jeśli liczba dni jest poprawna
        if (totalDaysInMonth > 0) {
            dailyNetSavingsPortion = netMonthlySavings / totalDaysInMonth; // Używamy oszczędności netto
        } else {
            Log.e(TAG, "Zapobieżono dzieleniu przez zero przy obliczaniu dailyNetSavingsPortion.");
        }


        // Końcowe Obliczenie Limitu
        // Limit = Docelowe Dziennie Oszczędności + Rollover z poprzedniego dnia
        double calculatedAllowance = dailyNetSavingsPortion + rollover;

        Log.d(TAG, "Dzienna Część Oszczędności Netto = " + CURRENCY_FORMAT.format(dailyNetSavingsPortion));
        Log.d(TAG, "Obliczony Limit (Dziennie Oszczędności Netto + Rollover) = " + CURRENCY_FORMAT.format(calculatedAllowance));

        return calculatedAllowance;
        // --- KONIEC POPRAWIONEJ METODY ---
    }


    private double calculateTotalExpenses() {
        double totalExpenses = 0;
        if (scannedItemsList == null) return 0.0; // Na wszelki wypadek

        for (HashMap<String, String> item : scannedItemsList) {
            if (item == null) {
                Log.w(TAG,"Znaleziono null item podczas liczenia wydatków.");
                continue; // Pomijamy
            }
            String priceString = item.get("price"); // Cena zapisana jako sformatowany tekst (np. "12,34 zł")
            if (priceString != null && !priceString.isEmpty()) {
                try {
                    // Próbujemy sparsować sformatowaną cenę
                    Number parsed = CURRENCY_FORMAT.parse(priceString);
                    if (parsed != null) {
                        totalExpenses += parsed.doubleValue(); // Dodajemy do sumy
                    } else {
                        // Plan B, jeśli parsowanie sformatowanej ceny zawiedzie
                        try { String cleanedPrice = priceString.replaceAll("[^\\d,.]", "").replace(',', '.'); if(!cleanedPrice.isEmpty()){ totalExpenses += Double.parseDouble(cleanedPrice); Log.w(TAG,"Plan B parsowania OK dla '"+priceString+"'"); } }
                        catch (NumberFormatException e2) { Log.e(TAG, "Plan B parsowania NIE POWIÓDŁ SIĘ dla '"+priceString+"'", e2); }
                    }
                } catch (ParseException e) {
                    // Plan B, jeśli parsowanie sformatowanej ceny rzuciło wyjątek
                    try { String cleanedPrice = priceString.replaceAll("[^\\d,.]", "").replace(',', '.'); if(!cleanedPrice.isEmpty()){ totalExpenses += Double.parseDouble(cleanedPrice); Log.w(TAG,"Plan B parsowania OK po ParseException dla '"+priceString+"'"); } }
                    catch (NumberFormatException e2) { Log.e(TAG, "Plan B parsowania NIE POWIÓDŁ SIĘ po ParseException dla '"+priceString+"'", e2); }
                }
            }
        }
        Log.d(TAG, "Obliczono Sumę Wydatków dla listy ("+scannedItemsList.size()+" produktów): " + CURRENCY_FORMAT.format(totalExpenses));
        return totalExpenses;
    }

    // --- Metody Obsługi Interakcji UI ---
    private void toggleScannedItemsListVisibility() {
        if (isScannedItemsListVisible) {
            // Chowamy listę
            scannedItemsListView.setVisibility(View.GONE);
            viewExpensesButton.setText(getString(R.string.show_expenses)); // Zmieniamy tekst przycisku
            isScannedItemsListVisible = false;
        } else {
            // Pokazujemy listę
            if (addProductContainer.getVisibility() == View.VISIBLE) {
                addProductContainer.setVisibility(View.GONE); // Chowamy formularz dodawania, jak pokazujemy listę
            }
            scannedItemsListView.setVisibility(View.VISIBLE);
            viewExpensesButton.setText(getString(R.string.hide_expenses_button_text)); // Zmieniamy tekst przycisku
            isScannedItemsListVisible = true;
            updateUI(); // Przeliczamy wysokość i odświeżamy adapter
        }
    }

    private void toggleAddProductVisibility() {
        if (addProductContainer.getVisibility() == View.VISIBLE) {
            // Chowamy formularz
            addProductContainer.setVisibility(View.GONE);
        } else {
            // Pokazujemy formularz
            if (isScannedItemsListVisible) { // Chowamy listę, jeśli pokazujemy formularz
                scannedItemsListView.setVisibility(View.GONE);
                viewExpensesButton.setText(getString(R.string.show_expenses));
                isScannedItemsListVisible = false;
            }
            addProductContainer.setVisibility(View.VISIBLE);
            productNameEditText.requestFocus(); // Ustawiamy focus na pierwszym polu
            // Opcjonalnie pokazujemy klawiaturę
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(productNameEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void confirmAddProduct() {
        String productName = productNameEditText.getText().toString().trim(); // Nazwa produktu (bez białych znaków na końcach)
        String productPriceString = productPriceEditText.getText().toString().replace(',', '.').trim(); // Cena (zamieniamy przecinek na kropkę)
        String productCategory; // Kategoria

        // Walidacja (sprawdzanie poprawności)
        if (productName.isEmpty()) {
            productNameEditText.setError(getString(R.string.error_product_name_price_required)); // Ustawiamy błąd
            productNameEditText.requestFocus(); // Ustawiamy focus na tym polu
            return; // Przerywamy
        } else productNameEditText.setError(null); // Czyścimy błąd

        if (productPriceString.isEmpty()) {
            productPriceEditText.setError(getString(R.string.error_product_name_price_required));
            productPriceEditText.requestFocus();
            return;
        } else productPriceEditText.setError(null);

        // Sprawdzamy, czy wybrano kategorię (czy nie jest to pierwsza opcja - podpowiedź)
        int selectedCategoryPosition = productCategorySpinner.getSelectedItemPosition();
        if (selectedCategoryPosition <= 0 || selectedCategoryPosition == Spinner.INVALID_POSITION) {
            productCategory = getString(R.string.default_category_other); // Używamy domyślnej "Inne", jak nic nie wybrano
            Log.d(TAG, "Nie wybrano kategorii, ustawiam domyślną: " + productCategory);
            // Opcjonalnie: Wymuś wybór
            // showToast("Proszę wybrać kategorię"); return;
        } else {
            productCategory = (String) productCategorySpinner.getItemAtPosition(selectedCategoryPosition); // Bierzemy wybraną kategorię
        }

        try {
            // Próbujemy sparsować cenę
            double productPrice = Double.parseDouble(productPriceString);
            if (productPrice <= 0) {
                // Cena musi być dodatnia
                productPriceEditText.setError(getString(R.string.error_price_must_be_positive));
                productPriceEditText.requestFocus();
                return;
            } else productPriceEditText.setError(null);

            // Wszystko OK, dodajemy produkt, aktualizujemy UI, zapisujemy dane, czyścimy formularz
            addItemToListInternal(productName, productPrice, productCategory); // Dodajemy do listy
            updateUI();      // Aktualizujemy sumy, odświeżamy adapter listy (przeliczy wysokość, jeśli widoczna)
            saveData(false); // Zapisujemy zaktualizowaną listę i obecny znacznik daty
            clearInputFieldsAndHideContainer(); // Czyścimy pola i chowamy formularz
            showToast(getString(R.string.toast_product_added, productName)); // Pokazujemy potwierdzenie

        } catch (NumberFormatException e) {
            // Nie udało się sparsować ceny (np. wpisano tekst)
            Log.e(TAG, "Błąd parsowania ceny produktu: " + productPriceString, e);
            productPriceEditText.setError(getString(R.string.error_invalid_price_format));
            productPriceEditText.requestFocus();
        }
    }

    // Wewnętrzna metoda do dodawania produktu do listy
    private void addItemToListInternal(String itemName, double itemPrice, String itemCategory) {
        // Domyślne wartości, jakby coś było puste
        if (itemName == null || itemName.isEmpty()) itemName = getString(R.string.default_item_name);
        if (itemCategory == null || itemCategory.isEmpty()) itemCategory = getString(R.string.default_category_other);

        // Tworzymy mapę dla nowego produktu
        HashMap<String, String> newItem = new HashMap<>();
        newItem.put("name", itemName);
        newItem.put("price", CURRENCY_FORMAT.format(itemPrice)); // Zapisujemy sformatowaną cenę jako tekst
        newItem.put("category", itemCategory);
        scannedItemsList.add(newItem); // Dodajemy do listy
        Log.d(TAG, "Produkt dodany: " + newItem + ". Rozmiar listy teraz: " + scannedItemsList.size());
    }

    private void clearInputFieldsAndHideContainer() {
        // Czyścimy pola tekstowe
        productNameEditText.setText("");
        productPriceEditText.setText("");
        productCategorySpinner.setSelection(0); // Resetujemy spinner do pierwszej pozycji (podpowiedź)
        addProductContainer.setVisibility(View.GONE); // Chowamy kontener formularza
        // Chowamy klawiaturę
        View currentFocus = getCurrentFocus(); // Znajdujemy element, który ma teraz focus
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0); // Chowamy klawiaturę
            }
            currentFocus.clearFocus(); // Zabieramy focus z elementu
        }
    }

    // --- Metody Aktualizacji UI ---
    private void updateUI() {
        updateBudgetDisplay();      // Aktualizujemy pola tekstowe z budżetem
        updateTotalValueDisplay();  // Aktualizujemy pole tekstowe z sumą wydatków
        // Odświeżamy adapter ListView i przeliczamy wysokość, jeśli trzeba
        if (scannedItemsAdapter != null) {
            scannedItemsAdapter.notifyDataSetChanged(); // Informujemy adapter, że dane się zmieniły
            Log.d(TAG, "Adapter powiadomiony, rozmiar listy: " + scannedItemsList.size());
            if (scannedItemsListView.getVisibility() == View.VISIBLE) {
                // Odpalamy ustawienie wysokości po tym, jak layout zostanie przeliczony
                scannedItemsListView.post(() -> setListViewHeightBasedOnChildren(scannedItemsListView));
            } else {
                // Opcjonalnie upewniamy się, że wysokość jest 0, gdy lista jest ukryta
                ViewGroup.LayoutParams params = scannedItemsListView.getLayoutParams();
                if(params != null && params.height != 0) { params.height = 0; scannedItemsListView.setLayoutParams(params); }
            }
        } else {
            Log.w(TAG,"updateUI odpalone, ale scannedItemsAdapter jest null!");
        }
        Log.d(TAG, "UI Zaktualizowane. Obecny Limit: " + CURRENCY_FORMAT.format(dailyAllowance));
    }

    private void updateBudgetDisplay() {
        double todaysSpending = calculateTotalExpenses(); // Ile wydano dzisiaj
        double remainingToday = dailyAllowance - todaysSpending; // Ile zostało na dzisiaj

        // Ustawiamy teksty w odpowiednich TextView
        dailyBudgetTextView.setText(String.format(Locale.getDefault(), getString(R.string.daily_budget_remaining), remainingToday)); // Ile zostało dzisiaj
        monthlyBudgetTextView.setText(String.format(Locale.getDefault(), getString(R.string.monthly_income_display), monthlyIncome)); // Miesięczny przychód
        savingsTextView.setText(String.format(Locale.getDefault(), getString(R.string.previous_day_rollover_display), previousDayRollover)); // Ile zostało z wczoraj

        Log.d(TAG, "updateBudgetDisplay: Zostało="+CURRENCY_FORMAT.format(remainingToday)+", Rollover="+CURRENCY_FORMAT.format(previousDayRollover));
    }

    private void updateTotalValueDisplay() {
        double totalExpenses = calculateTotalExpenses(); // Suma wydatków
        int itemCount = scannedItemsList.size(); // Liczba produktów
        // Ustawiamy tekst z liczbą produktów i sumą wydatków
        totalScannedItemsValueTextView.setText(String.format(Locale.getDefault(),
                getString(R.string.total_expenses_display), itemCount, totalExpenses));
        Log.d(TAG, "updateTotalValueDisplay: Liczba="+itemCount+", Suma="+CURRENCY_FORMAT.format(totalExpenses));
    }

    // --- Aparat i Przetwarzanie Paragonów ---
    /** Sprawdza pozwolenie i odpala intencję aparatu */
    private void openCamera() {
        // Sprawdzamy, czy mamy pozwolenie na aparat
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Nie mamy, prosimy o nie
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101); // Używamy kodu 101
        } else {
            // Mamy pozwolenie, odpalamy aparat
            launchCameraIntent();
        }
    }

    /** Odpala właściwą intencję aparatu */
    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // Intencja zrobienia zdjęcia
        // Sprawdzamy, czy jest jakaś apka aparatu w telefonie
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                Log.d(TAG, "Odpalam intencję aparatu (wewnętrznie)...");
                cameraLauncher.launch(takePictureIntent); // Używamy launchera zdefiniowanego w tej aktywności
            } catch (Exception e) {
                Log.e(TAG, "Nie udało się odpalić intencji aparatu", e);
                showToast(getString(R.string.error_camera_launch));
            }
        } else {
            // Nie ma apki aparatu
            showToast(getString(R.string.error_no_camera_app));
            Log.w(TAG, "Brak dostępnej apki aparatu.");
        }
    }

    /** Przetwarza obraz bitmapy z aparatu używając ML Kit Text Recognition */
    private void processReceipt(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmapa przekazana do processReceipt była null.");
            showToast(getString(R.string.error_getting_image_bitmap));
            return;
        }
        Log.d(TAG, "Przetwarzam bitmapę do rozpoznawania tekstu...");
        // Tworzymy recognizer tekstu z domyślnymi opcjami
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        // Tworzymy obiekt InputImage z naszej bitmapy (0 - stopnie obrotu, jakby trzeba było)
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        // Odpalamy proces rozpoznawania
        recognizer.process(image)
                .addOnSuccessListener(visionText -> { // Jak się uda
                    String resultText = visionText.getText(); // Bierzemy cały rozpoznany tekst
                    Log.d(TAG, "ML Kit Vision Text: \n" + resultText);
                    if (resultText != null && !resultText.isEmpty()) {
                        // *** ODPALAMY PARSER ***
                        parseReceiptText(resultText); // Przekazujemy tekst do naszej funkcji parsującej
                        // *********************
                    } else {
                        // Nie rozpoznano żadnego tekstu
                        showToast(getString(R.string.error_no_text_recognized));
                    }
                })
                .addOnFailureListener(e -> { // Jak się nie uda
                    Log.e(TAG, "Rozpoznawanie tekstu nie powiodło się", e);
                    showToast(getString(R.string.error_text_recognition_failed) + e.getMessage());
                });
    }

    /**
     * Parsuje tekst z obrazu paragonu, próbując wyciągnąć nazwy produktów i ich KOŃCOWE ceny
     * dla popularnych polskich formatów paragonów. Dodaje pomyślnie sparsowane produkty do listy
     * z kategorią "Zeskanowane".
     * @param resultText Pełny tekst rozpoznany z obrazu paragonu.
     */
    private void parseReceiptText(String resultText) {
        int itemsAddedCount = 0; // Licznik dodanych produktów
        double itemsAddedValue = 0.0; // Suma cen dodanych produktów
        String scannedCategory = getString(R.string.default_category_scanned); // Kategoria dla zeskanowanych

        // Ogólny Regex: Przechwytuje całą linię, potencjalną część z nazwą i końcową cenę.
        // ^(.+)              - Grupa 1: Potencjalna Nazwa + pośrednie detale (zachłannie)
        // \\s+               - Wymagana spacja przed końcową ceną
        // (\\d+[,.]\\d{2})    - Grupa 2: Końcowa Cena (liczba z 2 miejscami po przecinku/kropce)
        // \\s*               - Opcjonalne spacje na końcu
        // [A-Z]?$            - Opcjonalna wielka litera na końcu linii (grupa VAT).
        String linePattern = "^(.+)\\s+(\\d+[,.]\\d{2})\\s*[A-Z]?$";
        // Używamy MULTILINE, żeby ^ i $ pasowały do początku/końca każdej linii
        Pattern pattern = Pattern.compile(linePattern, Pattern.MULTILINE);

        // Rozszerzony filtr dla popularnych linii nie będących produktami (ignoruje wielkość liter)
        // Filtruje m.in. nagłówki, stopki, sumy, NIP, płatności, rabaty, linie tylko z ceną, puste linie
        String filterPattern = "(?i:.*(PARAGON FISKALNY|NIP ?PL|\\d{10}|SUMA|PTU|OPUST|RABAT|ŁĄCZNIE|RAZEM|SPRZEDAŻ OPODATK|GOTÓWKA|KARTA|RESZTA|KASJER|WYDRUK NI|FISKALN|DO ZAPŁATY|ZWROT|www\\.|ul\\.|godz|transakcji|ZAPŁACONO).*)|(^\\s*$)|(^-?\\d+([.,]\\d{2})?\\s?(zł|PLN)?$)";

        Log.d(TAG, "--- Rozpoczynam Ogólne Parsowanie Polskiego Paragonu ---");

        // Dopasowujemy wzorzec do całego bloku tekstu
        Matcher matcher = pattern.matcher(resultText);

        while (matcher.find()) { // Szukamy kolejnych pasujących linii
            String fullMatch = matcher.group(0); // Cała dopasowana linia do kontekstu i filtrowania

            // Sprawdzamy, czy dopasowana linia powinna być odfiltrowana
            if (fullMatch != null && fullMatch.matches(filterPattern)) {
                Log.v(TAG, "--> Pomijam odfiltrowaną linię (pasowała do wzorca produktu, ale złapana przez filtr): " + fullMatch);
                continue; // Idziemy do następnej linii
            }

            // Wyciągamy potencjalną nazwę (grupa 1) i cenę (grupa 2)
            String rawItemName = matcher.group(1) != null ? matcher.group(1).trim() : "";
            // Normalizujemy separator dziesiętny na kropkę
            String priceStr = matcher.group(2) != null ? matcher.group(2).replace(',', '.') : null;

            // --- Czyszczenie Nazwy Produktu po Regexie ---
            String cleanedItemName = rawItemName;
            if (!cleanedItemName.isEmpty()) {
                Log.v(TAG, "Surowa wyciągnięta część nazwy: '" + cleanedItemName + "'");

                // 1. Usuwamy jawne "Ilość x CenaJedn" lub "Ilość * CenaJedn" (popularne formaty)
                cleanedItemName = cleanedItemName.replaceAll("\\s+\\d+([.,]\\d+)?\\s*[xX*]\\s*\\d+[,.]\\d{2}$", "").trim();

                // 2. Usuwamy końcową cenę jednostkową oznaczoną '*' (widziane w restauracjach)
                cleanedItemName = cleanedItemName.replaceAll("\\s+\\*\\s*\\d+[,.]\\d{2}$", "").trim();

                // 3. Usuwamy końcową prostą ilość/wagę/jednostkę (np. " 1 szt." lub " 0.5 kg." lub " 2 szt")
                cleanedItemName = cleanedItemName.replaceAll("\\s+\\d*[,.]?\\d+\\s*(szt|kg|g|l|ml|op|opak)[.]?$", "").trim();

                // 4. Usuwamy końcowy kod VAT (A/B/C/D), jeśli przypadkiem został złapany w nazwie
                cleanedItemName = cleanedItemName.replaceAll("\\s+[A-D]$", "").trim();

                // 5. Usuwamy początkowy kod VAT (czasem się zdarza)
                cleanedItemName = cleanedItemName.replaceAll("^[A-D]\\s+", "").trim();

                // 6. Usuwamy początkowe liczby ze spacją (potencjalne kody - UWAGA: może popsuć nazwy)
                // cleanedItemName = cleanedItemName.replaceAll("^\\d+\\s+", "").trim();

                // 7. Łączymy wielokrotne spacje, które mogły zostać
                cleanedItemName = cleanedItemName.replaceAll("\\s{2,}", " ").trim();

                Log.v(TAG, "Próba wyczyszczonej nazwy: '" + cleanedItemName + "'");
            }
            // --- Koniec Czyszczenia ---


            // Sprawdzamy wyciągnięte i wyczyszczone dane
            // Sprawdzamy, czy cena istnieje, czy nazwa nie jest pusta i ma więcej niż 1 znak
            if (priceStr != null && !cleanedItemName.isEmpty() && cleanedItemName.length() > 1) {
                try {
                    // Próbujemy sparsować cenę
                    double itemPrice = Double.parseDouble(priceStr);
                    // Dodajemy produkt, jeśli cena jest dodatnia
                    if (itemPrice > 0) {
                        Log.i(TAG, "--> Sparsowany Produkt: '" + cleanedItemName + "', Cena: " + itemPrice + ", Kategoria: " + scannedCategory);
                        // Dodajemy produkt używając naszej wewnętrznej metody z kategorią zeskanowanych
                        addItemToListInternal(cleanedItemName, itemPrice, scannedCategory);
                        itemsAddedCount++; // Zwiększamy licznik
                        itemsAddedValue += itemPrice; // Dodajemy do sumy
                    } else {
                        Log.d(TAG, "--> Pomijam zerową/ujemną cenę dla dopasowanej linii: " + fullMatch);
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "--> Nie udało się sparsować końcowej ceny: '" + priceStr + "' z linii: " + fullMatch);
                }
            } else {
                // Logujemy, dlaczego mogliśmy pominąć (np. pusta nazwa, brak ceny, krótka nazwa po czyszczeniu)
                Log.d(TAG, "--> Regex pasował, ale nie przeszedł walidacji po czyszczeniu: " + fullMatch + " (Wyczyszczona Nazwa: '" + cleanedItemName + "', Cena: '" + priceStr + "')");
            }
        }
        Log.d(TAG, "--- Zakończono Ogólne Parsowanie Polskiego Paragonu --- Dodano produktów: " + itemsAddedCount);

        // Aktualizujemy UI i zapisujemy, jeśli dodano jakieś produkty
        if (itemsAddedCount > 0) {
            updateUI();      // Odświeżamy listę i sumy
            saveData(false); // Zapisujemy zaktualizowaną listę
            // Pokazujemy toast z informacją ile dodano i za jaką kwotę
            showToast(String.format(Locale.getDefault(), getString(R.string.toast_items_added), itemsAddedCount, itemsAddedValue));
        } else {
            // Informujemy usera, że nic nie znaleziono po filtrowaniu i parsowaniu
            showToast(getString(R.string.error_receipt_items_not_found));
        }
    }

    // --- Obsługa Intencji (Intent Handling) ---
    // <<< DEFINICJA METODY JEST TUTAJ >>>
    private void handleIntent(Intent intent) {
        // Sprawdzamy, czy intencja nie jest null i czy zawiera nasz dodatek z bitmapą
        if (intent != null && intent.hasExtra(HomeActivity.EXTRA_BITMAP_FOR_PROCESSING)) {
            Log.d(TAG, "Intencja zawiera dodatek z bitmapą. Próbuję przetworzyć.");
            try {
                // Pobieramy bitmapę (uwzględniamy zmiany w API 33+)
                Bitmap bitmap;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Nowy sposób dla Androida 13+
                    bitmap = intent.getParcelableExtra(HomeActivity.EXTRA_BITMAP_FOR_PROCESSING, Bitmap.class);
                } else {
                    // Starszy (przestarzały) sposób dla starszych API
                    bitmap = intent.getParcelableExtra(HomeActivity.EXTRA_BITMAP_FOR_PROCESSING);
                }

                if (bitmap != null) {
                    // Mamy bitmapę, super!
                    Log.i(TAG, "Bitmapa otrzymana pomyślnie przez Intent. Przetwarzam paragon...");
                    processReceipt(bitmap); // Odpalamy przetwarzanie
                    // Usuwamy dodatek po przetworzeniu, żeby nie przetwarzać ponownie np. po zmianie orientacji ekranu
                    intent.removeExtra(HomeActivity.EXTRA_BITMAP_FOR_PROCESSING);
                } else {
                    // Coś poszło nie tak, dodatek był, ale bitmapa jest null
                    Log.w(TAG, "Dodatek z bitmapą znaleziony w Intencji, ale sama bitmapa była null.");
                    showToast(getString(R.string.error_getting_image_bitmap));
                }
            } catch (Exception e) {
                // Jakiś błąd przy wyciąganiu bitmapy z intencji
                Log.e(TAG, "Błąd podczas pobierania bitmapy z dodatku Intencji", e);
                showToast(getString(R.string.error_getting_image_bitmap));
            }
        } else {
            // Intencja nie miała naszego dodatku
            Log.d(TAG,"handleIntent: Nie znaleziono dodatku z bitmapą w intencji.");
        }
    }
    // <<< KONIEC DEFINICJI METODY >>>

    // --- Nawigacja ---
    private void configureBottomNavigationView() {
        // Upewnijmy się, że dolna nawigacja nie jest null
        if (bottomNavigationView == null) {
            Log.e(TAG, "BottomNavigationView jest null w configureBottomNavigationView!");
            return;
        }
        // Ustawiamy listenera na kliknięcia ikonek
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId(); // ID klikniętej ikonki
            Intent intent = null; // Intencja do odpalenia nowego ekranu
            // Sprawdzamy, co zostało kliknięte
            if (itemId == R.id.navigation_home) intent = new Intent(this, HomeActivity.class);
            else if (itemId == R.id.navigation_calendar) intent = new Intent(this, CalendarActivity.class);
            else if (itemId == R.id.navigation_menu) intent = new Intent(this, SettingsActivity.class);
            else if (itemId == R.id.navigation_camera) {
                // AKCJA PRZYCISKU APARATU będąc na ekranie ExpensesActivity
                Log.d(TAG, "Kliknięto przycisk aparatu w dolnej nawigacji (z ExpensesActivity).");
                openCamera(); // Wywołujemy wewnętrzną metodę do odpalenia aparatu
                return false; // <<< Nie zmieniamy zaznaczonej ikonki, zostajemy na Expenses
            }

            // Jeśli przygotowaliśmy jakąś intencję (czyli przechodzimy gdzieś indziej)
            if (intent != null) {
                // Standardowe flagi, żeby nie tworzyć wielu kopii tego samego ekranu + wyłączenie animacji
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent); // Odpalamy nowy ekran
                overridePendingTransition(0, 0); // Wyłączamy animację przejścia
                return true; // Zaznaczenie obsłużone
            }
            return false; // Zaznaczenie nieobsłużone (kliknięto coś nieznanego?)
        });
        // Opcjonalnie: Ustawiamy ikonkę Expenses jako zaznaczoną, jeśli ma własną ikonę w nawigacji
        // bottomNavigationView.setSelectedItemId(R.id.navigation_expenses); // Przykład
    }

    // --- Logika Powiadomień ---
    private void checkForMonthEndAndNotify(String previousDateStr, String currentDateStr) {
        try {
            // Parsujemy daty
            Date previousDate = DATE_FORMAT.parse(previousDateStr);
            Date currentDate = DATE_FORMAT.parse(currentDateStr);
            if (previousDate == null || currentDate == null) return; // Jak się nie uda, to nic nie robimy

            // Tworzymy obiekty Calendar
            Calendar prevCal = Calendar.getInstance(); prevCal.setTime(previousDate);
            Calendar currCal = Calendar.getInstance(); currCal.setTime(currentDate);

            // Sprawdzamy, czy zmienił się miesiąc LUB rok
            if (prevCal.get(Calendar.MONTH) != currCal.get(Calendar.MONTH) ||
                    prevCal.get(Calendar.YEAR) != currCal.get(Calendar.YEAR))
            {
                // Nowy Miesiąc - Obliczamy sumę dla *poprzedniego* miesiąca
                int targetMonth = prevCal.get(Calendar.MONTH); // Miesiąc (0-11)
                int targetYear = prevCal.get(Calendar.YEAR); // Rok
                // Pobieramy nazwę miesiąca po polsku
                String targetMonthName = prevCal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());

                Log.i(TAG, "Przekroczono granicę miesiąca. Obliczam sumę dla: " + targetMonthName + " " + targetYear);

                // Ładujemy mapę dziennych oszczędności
                String savingsJson = sharedPreferences.getString(DAILY_SAVINGS_KEY, "{}");
                Type type = new TypeToken<HashMap<String, Double>>() {}.getType();
                HashMap<String, Double> dailySavingsMap = new Gson().fromJson(savingsJson, type);

                if (dailySavingsMap == null || dailySavingsMap.isEmpty()) {
                    Log.w(TAG, "Mapa dziennych oszczędności pusta. Nie mogę obliczyć sumy miesięcznej.");
                    return; // Jak pusta, to nie ma co liczyć
                }

                double previousMonthTotal = 0.0; // Suma dla poprzedniego miesiąca
                // Przechodzimy przez wszystkie wpisy w mapie
                for (Map.Entry<String, Double> entry : dailySavingsMap.entrySet()) {
                    try {
                        // Parsujemy datę z klucza wpisu
                        Date entryDate = DATE_FORMAT.parse(entry.getKey());
                        if (entryDate != null) {
                            Calendar entryCal = Calendar.getInstance(); entryCal.setTime(entryDate);
                            // Sprawdzamy, czy wpis jest z miesiąca i roku, który nas interesuje
                            if (entryCal.get(Calendar.MONTH) == targetMonth && entryCal.get(Calendar.YEAR) == targetYear) {
                                previousMonthTotal += entry.getValue(); // Dodajemy wynik dzienny do sumy miesięcznej
                            }
                        }
                    } catch (ParseException e) { /* Ignorujemy wpis, jeśli klucz (data) jest zły */ }
                }
                Log.i(TAG, "Suma oszczędności dla " + targetMonthName + " " + targetYear + ": " + CURRENCY_FORMAT.format(previousMonthTotal));
                // Wysyłamy powiadomienie z podsumowaniem
                sendMonthlySummaryNotification(targetMonthName, targetYear, previousMonthTotal);
            }
        } catch (Exception e) { Log.e(TAG, "Błąd podczas sprawdzania końca miesiąca", e); }
    }

    // Tworzy kanał powiadomień (wymagane od Androida 8 Oreo)
    private void createNotificationChannel() {
        // Sprawdzamy, czy wersja Androida jest wystarczająco nowa
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name); // Nazwa kanału widoczna dla usera
            String description = getString(R.string.notification_channel_description); // Opis kanału
            int importance = NotificationManager.IMPORTANCE_DEFAULT; // Ważność powiadomień (domyślna)
            // Tworzymy obiekt kanału
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Pobieramy systemowy manager powiadomień
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                // Rejestrujemy kanał w systemie
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Kanał powiadomień stworzony: " + NOTIFICATION_CHANNEL_ID);
            } else { Log.e(TAG, "Nie udało się pobrać usługi NotificationManager."); }
        }
    }

    // Prosi o pozwolenie na wysyłanie powiadomień, jeśli jest potrzebne (Android 13+)
    private void requestNotificationPermissionIfNeeded() {
        // Sprawdzamy, czy wersja to Android 13 (TIRAMISU) lub nowsza
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Sprawdzamy, czy mamy już pozwolenie
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Nie mamy, prosimy o nie
                Log.w(TAG, "Pozwolenie na powiadomienia NIE jest przyznane. Proszę...");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                // Mamy już pozwolenie
                Log.d(TAG, "Pozwolenie na powiadomienia już jest.");
            }
        }
    }

    // Obsługuje wynik zapytania o pozwolenia
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Sprawdzamy, czy to odpowiedź na naszą prośbę o pozwolenie na powiadomienia
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            // Sprawdzamy, czy użytkownik się zgodził
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Pozwolenie na powiadomienia PRZYZNANE przez użytkownika.");
            } else {
                Log.w(TAG, "Pozwolenie na powiadomienia ODMÓWIONE przez użytkownika.");
                showToast(getString(R.string.notification_permission_denied)); // Informujemy usera
            }
        }
        // Obsługujemy wynik prośby o pozwolenie na aparat (chociaż launcher to ogarnia)
        else if (requestCode == 101) { // Kod użyty w openCamera()
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Pozwolenie na aparat PRZYZNANE przez użytkownika (przez bezpośrednią prośbę).");
                launchCameraIntent(); // Odpalamy aparat teraz
            } else {
                Log.w(TAG, "Pozwolenie na aparat ODMÓWIONE przez użytkownika (przez bezpośrednią prośbę).");
                showToast(getString(R.string.toast_camera_permission_denied));
            }
        }
    }

    // Wysyła powiadomienie z podsumowaniem miesięcznym
    private void sendMonthlySummaryNotification(String monthName, int year, double totalSaved) {
        // Sprawdzamy pozwolenie dla Androida 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Nie mogę wysłać powiadomienia - Brak pozwolenia.");
                return; // Jak nie ma, to nie wysyłamy
            }
        }
        // Bierzemy kontekst aplikacji
        Context context = getApplicationContext();
        if(context == null) { Log.e(TAG,"Nie mogę wysłać powiadomienia, kontekst jest null"); return; }

        // Tytuł i treść powiadomienia z zasobów string
        String title = getString(R.string.monthly_summary_notification_title, monthName, year);
        String text = getString(R.string.monthly_summary_notification_text, CURRENCY_FORMAT.format(totalSaved));

        // Budujemy powiadomienie
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_savings) // Mała ikonka (upewnij się, że ten obrazek istnieje!)
                .setContentTitle(title) // Tytuł
                .setContentText(text) // Treść
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Priorytet
                .setAutoCancel(true); // Zniknie po kliknięciu

        // Pobieramy manager powiadomień (wersja kompatybilna)
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            // Jeszcze raz upewniamy się o pozwoleniu tuż przed wysłaniem (Android 13+)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.e(TAG, "Sprawdzenie pozwolenia na powiadomienia nie powiodło się tuż przed wywołaniem notify.");
                return; // Wyjdź, jeśli pozwolenie jakoś zostało cofnięte lub sprawdzenie zawiodło
            }
            // Wysyłamy powiadomienie używając naszego ID
            notificationManager.notify(MONTHLY_SUMMARY_NOTIFICATION_ID, builder.build());
            Log.i(TAG, "Wysłano powiadomienie z podsumowaniem miesięcznym dla " + monthName + " " + year);
        } catch (SecurityException se) {
            // Błąd bezpieczeństwa (rzadkie, ale może się zdarzyć)
            Log.e(TAG, "SecurityException podczas wysyłania powiadomienia.", se);
            showToast(getString(R.string.error_sending_notification));
        } catch (Exception e) {
            // Inny błąd
            Log.e(TAG, "Nie udało się wysłać powiadomienia", e);
            showToast(getString(R.string.error_sending_notification));
        }
    }


    // --- Metody Pomocnicze ---
    // Pokazuje krótki toast (dymek)
    private void showToast(String message) {
        // Sprawdzamy, czy aktywność jest widoczna dla użytkownika
        if (isUserFacingActivity()) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        } else {
            // Jak nie jest widoczna (np. w tle), to nie pokazujemy toasta, tylko logujemy
            Log.w(TAG, "Toast pominięty (aktywność niewidoczna dla usera): " + message);
        }
    }

    // Sprawdza, czy aktywność jest "widoczna" (nie kończy się i nie jest zniszczona)
    private boolean isUserFacingActivity() {
        return !isFinishing() && !isDestroyed();
    }

    /** Ustawia wysokość ListView na podstawie jej dzieci; wywołaj po zmianach adaptera i gdy lista jest widoczna */
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter(); // Bierzemy adapter
        if (listAdapter == null) {
            Log.w(TAG, "setListViewHeight: Adapter jest null.");
            return; // Jak nie ma adaptera, to nic nie robimy
        }
        int totalHeight = 0; // Całkowita wysokość
        // Określamy, jak szeroki ma być element listy do zmierzenia
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST);
        if (listAdapter.getCount() == 0) {
            totalHeight = 0; // Jak lista pusta, to wysokość 0
        } else {
            // Sprawdzamy, czy szerokość listy jest > 0 (czy została już narysowana)
            if (listView.getWidth() > 0) {
                try {
                    // Bierzemy pierwszy element listy do zmierzenia
                    View listItem = listAdapter.getView(0, null, listView);
                    if (listItem != null) {
                        // Mierzymy go
                        listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
                        int itemHeight = listItem.getMeasuredHeight(); // Bierzemy jego wysokość
                        if (itemHeight > 0) {
                            // Obliczamy całkowitą wysokość: (wysokość_elementu * liczba_elementów) + (wysokość_dzielnika * (liczba_elementów - 1))
                            totalHeight = (itemHeight * listAdapter.getCount()) + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
                        } else return; // Nie da się obliczyć, jak wysokość elementu to 0
                    } else return; // Nie da się zmierzyć, jak widok jest null
                } catch (Exception e) {
                    Log.e(TAG,"Błąd podczas mierzenia elementu listy", e);
                    return; // Jak błąd, to nic nie robimy
                }
            } else { return; /* Szerokość 0, poczekaj na layout */ }
        }

        // Pobieramy obecne parametry layoutu listy
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        if (params == null) {
            // Jak nie ma, tworzymy nowe
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            listView.setLayoutParams(params);
        }
        // Ustawiamy nową wysokość tylko jeśli się różni i jest >= 0
        if (params.height != totalHeight && totalHeight >= 0) {
            params.height = totalHeight;
            listView.requestLayout(); // Prosimy o przerysowanie z nowymi parametrami
            Log.d(TAG, "Ustawiono wysokość ListView na " + params.height);
        }
    }

    // --- Wewnętrzna Klasa CustomAdapter ---
    // Nasz własny adapter do obsługi listy wydatków
    private class CustomAdapter extends BaseAdapter {
        private LayoutInflater inflater; // Do tworzenia widoków z XMLa

        public CustomAdapter() {
            // Pobieramy inflater z kontekstu naszej aktywności
            inflater = LayoutInflater.from(ExpensesActivity.this);
        }

        @Override public int getCount() { return scannedItemsList.size(); } // Ile elementów w liście?

        @Override public Object getItem(int position) { // Pobierz element na danej pozycji
            // Sprawdzamy, czy pozycja jest poprawna
            if (position >= 0 && position < scannedItemsList.size()) return scannedItemsList.get(position);
            return null; // Jak nie, to null
        }

        @Override public long getItemId(int position) { return position; } // ID elementu to po prostu jego pozycja

        // Tworzy lub aktualizuje widok dla elementu listy
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder; // Trzyma referencje do widoków w elemencie listy (optymalizacja)
            if (convertView == null) { // Jeśli widok jeszcze nie istnieje (pierwsze tworzenie lub recykling zawiódł)
                if (inflater == null) { // Sprawdzenie bezpieczeństwa
                    Log.e(TAG, "Inflater jest null w getView!");
                    return new TextView(parent.getContext()); // Zwróć pusty widok w razie błędu
                }
                try {
                    // Tworzymy widok z naszego pliku XML list_item_expense.xml
                    convertView = inflater.inflate(R.layout.list_item_expense, parent, false);
                    // Tworzymy ViewHolder i znajdujemy w nim nasze TextView i przycisk
                    holder = new ViewHolder();
                    holder.nameTextView = convertView.findViewById(R.id.itemName);
                    holder.categoryTextView = convertView.findViewById(R.id.itemCategory);
                    holder.priceTextView = convertView.findViewById(R.id.itemPrice);
                    holder.deleteButton = convertView.findViewById(R.id.deleteButton);
                    // Sprawdzamy, czy wszystko znaleziono, inaczej MASAKRA
                    if (holder.nameTextView == null || holder.categoryTextView == null || holder.priceTextView == null || holder.deleteButton == null) {
                        Log.e(TAG, "MASAKRA: Nie znaleziono widoku w list_item_expense.xml!");
                        return new TextView(parent.getContext()); // Zwracamy pusty widok
                    }
                    // Zapisujemy holder w tagu widoku, żeby go później odzyskać
                    convertView.setTag(holder);
                } catch (Exception e) {
                    Log.e(TAG, "Błąd podczas tworzenia widoku z list_item_expense.xml", e);
                    return new TextView(parent.getContext()); // Zwracamy pusty widok
                }
            } else { // Jeśli widok już istnieje (jest z recyklingu)
                holder = (ViewHolder) convertView.getTag(); // Odzyskujemy holder z tagu
                if (holder == null) { // Nie powinno się zdarzyć, ale na wszelki wypadek
                    Log.e(TAG, "ViewHolder tag jest null! Tworzę widok od nowa.");
                    return getView(position, null, parent); // Spróbuj utworzyć od nowa (uważaj na pętle)
                }
            }

            // Pobieramy dane dla tej pozycji
            HashMap<String, String> item = (HashMap<String, String>) getItem(position);
            if (item == null) {
                // Obsługujemy przypadek, gdyby element był null
                holder.nameTextView.setText(R.string.error_loading_item); // Tekst błędu
                holder.categoryTextView.setText("");
                holder.priceTextView.setText("");
                holder.deleteButton.setVisibility(View.GONE); // Chowamy przycisk usuwania
                return convertView;
            }

            // Ustawiamy teksty w TextView z danych produktu (z domyślnymi wartościami, jakby czegoś brakowało)
            holder.nameTextView.setText(item.getOrDefault("name", getString(R.string.default_item_name)));
            holder.categoryTextView.setText(item.getOrDefault("category", getString(R.string.default_category_other)));
            holder.priceTextView.setText(item.getOrDefault("price", CURRENCY_FORMAT.format(0))); // Pokazujemy sformatowaną cenę lub 0

            // Ustawiamy przycisk usuwania
            holder.deleteButton.setVisibility(View.VISIBLE); // Pokazujemy go
            holder.deleteButton.setOnClickListener(v -> { // Co ma się dziać po kliknięciu "usuń"
                // Sprawdzamy granice przed usunięciem
                if (position >= 0 && position < getCount()) { // Używamy getCount() dla aktualnego rozmiaru adaptera
                    try {
                        Log.d(TAG, "Usuwam element na pozycji " + position);
                        scannedItemsList.remove(position); // Usuwamy element z listy danych
                        notifyDataSetChanged(); // Informujemy adapter, że dane się zmieniły (odświeży widok)
                        updateUI(); // Aktualizujemy sumy i wysokość listy
                        saveData(false); // Zapisujemy zmianę (samą listę)
                    } catch (IndexOutOfBoundsException e) {
                        // Błąd, jeśli pozycja jest poza zakresem (nie powinno się zdarzyć przy tym sprawdzeniu, ale...)
                        Log.e(TAG, "Usuwanie nie powiodło się - IndexOutOfBounds dla pozycji: " + position, e);
                        showToast(getString(R.string.error_deleting_item));
                        notifyDataSetChanged(); // Spróbuj odświeżyć stan adaptera
                    } catch (Exception e) {
                        // Inny niespodziewany błąd
                        Log.e(TAG, "Usuwanie nie powiodło się - Nieoczekiwany błąd dla pozycji: " + position, e);
                        showToast(getString(R.string.error_deleting_item));
                    }
                } else {
                    // Kliknięto przycisk dla nieprawidłowej pozycji? Dziwne.
                    Log.e(TAG, "Kliknięto przycisk usuwania dla nieprawidłowej pozycji: " + position + " (Liczba elementów adaptera: " + getCount() + ")");
                    showToast(getString(R.string.error_deleting_item));
                }
            });

            return convertView; // Zwracamy gotowy widok elementu listy
        }

        // Wzorzec ViewHolder - przechowuje referencje do widoków w elemencie listy
        private class ViewHolder {
            TextView nameTextView;
            TextView categoryTextView;
            TextView priceTextView;
            ImageButton deleteButton;
        }
    } // Koniec Klasy CustomAdapter

} // --- Koniec klasy ExpensesActivity ---