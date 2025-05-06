package com.example.cashmeinv3;

// Importy potrzebne do Androida, pozwoleń, aparatu itd.
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

// Import do dolnej nawigacji
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Locale;

// Upewnij się, że BaseActivity istnieje i jest dobrze ustawione
public class HomeActivity extends BaseActivity {

    private static final String TAG = "HomeActivity"; // Do logów, żeby wiedzieć co się dzieje
    private static final String SHARED_PREFS = "BudgetData"; // Wspólna nazwa pliku SharedPreferences

    // Klucze do SharedPreferences - MUSZĄ być takie same jak w ExpensesActivity
    private static final String MONTHLY_INCOME_KEY = "monthlyIncome"; // Przychód
    private static final String MONTHLY_EXPENSE_TARGET_KEY = "monthlyExpenseTarget"; // Cel wydatków
    // Stała dla flagi aktualizacji - MUSI być taka sama jak w ExpensesActivity
    private static final String BUDGET_UPDATED_FLAG_KEY = "budget_updated_flag"; // boolean - czy budżet zmieniony?

    // Klucz do przekazywania bitmapy (zdjęcia) do ExpensesActivity
    public static final String EXTRA_BITMAP_FOR_PROCESSING = "bitmap_for_processing";

    // Elementy UI (widoki)
    private EditText incomeInput, expenseTargetInput; // Pola do wpisania przychodu i celu wydatków
    private Button saveBudgetButton; // Przycisk do zapisania budżetu
    private Button showExpensesButton; // Przycisk do przejścia do ekranu Wydatków
    private BottomNavigationView bottomNavigationView; // Dolny pasek nawigacji

    private SharedPreferences sharedPreferences; // Do zapisywania/odczytywania danych

    // Launchery do aparatu (nowy sposób obsługi wyników)
    // Ten czeka na wynik zrobienia zdjęcia
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Sprawdzamy, czy user zrobił zdjęcie (OK) i czy są dane
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bundle extras = result.getData().getExtras(); // Wyciągamy dodatki
                    if (extras != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data"); // Wyciągamy bitmapę (zdjęcie)
                        if (imageBitmap != null) {
                            // Mamy zdjęcie, odpalamy ExpensesActivity i przekazujemy je
                            launchExpensesActivityWithBitmap(imageBitmap);
                        } else {
                            // Bitmpa jest null? Dziwne.
                            Log.w(TAG, "Dane bitmapy były null w wyniku z aparatu.");
                            showToast(getString(R.string.error_getting_image_bitmap));
                        }
                    } else {
                        // Nie ma dodatków? Jeszcze dziwniejsze.
                        Log.w(TAG, "Paczka 'extras' była null w wyniku z aparatu.");
                        showToast(getString(R.string.error_camera_data_extras));
                    }
                } else {
                    // Użytkownik anulował albo coś innego poszło nie tak
                    Log.w(TAG, "Wynik z aparatu nie był OK albo dane były null. Kod wyniku: " + result.getResultCode());
                }
            });

    // Ten launcher odpala się, jak pytamy o pozwolenie na aparat
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    // User się zgodził, odpalamy aparat
                    openCamera();
                } else {
                    // User odmówił :(
                    showToast(getString(R.string.toast_camera_permission_denied));
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home); // Upewnij się, że nazwa pliku XML się zgadza

        try {
            initializeUI(); // Podpinamy widoki
            loadAndDisplaySavedBudgetInputs(); // Ładujemy i pokazujemy zapisany budżet
            setupButtonListeners(); // Ustawiamy co robią przyciski
            configureBottomNavigationView(); // Konfigurujemy dolną nawigację
        } catch (Exception e) {
            // Jakiś ogólny błąd przy starcie aktywności
            Log.e(TAG, "Błąd podczas inicjalizacji HomeActivity", e);
            showToast(getString(R.string.error_initialization_home));
            // Można by tu zamknąć aktywność albo pokazać jakiś bardziej konkretny błąd
            // finish();
        }
    }

    private void initializeUI() {
        // Łączymy zmienne z ID elementów w pliku activity_home.xml
        incomeInput = findViewById(R.id.budgetInput);
        expenseTargetInput = findViewById(R.id.expensesInput);
        saveBudgetButton = findViewById(R.id.calculateBudgetButton);
        showExpensesButton = findViewById(R.id.showExpensesButton);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Sprawdzamy, czy znaleźliśmy najważniejsze elementy, bo inaczej apka może nie działać
        if (incomeInput == null || expenseTargetInput == null || saveBudgetButton == null ||
                showExpensesButton == null || bottomNavigationView == null) {
            Log.e(TAG, "Nie znaleziono jednego lub więcej kluczowych elementów UI w activity_home.xml!");
            showToast("Błąd interfejsu użytkownika."); // Ogólny błąd
            // Można by zamknąć (finish()), jeśli ekran jest bezużyteczny
            return; // Przerywamy, jeśli brakuje widoków
        }

        // Ustawiamy teksty przycisków używając zasobów string (żeby było łatwo tłumaczyć)
        saveBudgetButton.setText(R.string.calculate_budget); // "Zapisz budżet"
        showExpensesButton.setText(R.string.home_expenses_button_label); // <<<< ZAKTUALIZOWANE, żeby używało "Wydatki" >>>>

        // Inicjalizujemy SharedPreferences
        sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
    }

    // Ładuje zapisane wartości budżetu i wyświetla je w polach EditText
    private void loadAndDisplaySavedBudgetInputs() {
        if (sharedPreferences == null) {
            Log.e(TAG, "SharedPreferences jest null w loadAndDisplaySavedBudgetInputs");
            // Próbujemy zainicjalizować ponownie
            sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
            if (sharedPreferences == null) return; // Nadal null, nie możemy kontynuować
        }
        // Używamy Locale.US, żeby mieć pewność, że kropka '.' jest separatorem dziesiętnym (dla spójności wewnętrznej)
        float savedIncome = sharedPreferences.getFloat(MONTHLY_INCOME_KEY, 0.0f); // Domyślnie 0.0
        float savedExpenseTarget = sharedPreferences.getFloat(MONTHLY_EXPENSE_TARGET_KEY, 0.0f);

        // Ustawiamy tekst w polu tylko jeśli wartość jest sensownie większa od 0
        if (savedIncome > 0.001f) { // Używamy tolerancji przy porównywaniu floatów
            // Formatujemy na 2 miejsca po kropce
            incomeInput.setText(String.format(Locale.US, "%.2f", savedIncome));
        } else {
            incomeInput.setText(""); // Czyścimy pole, jak jest zero albo nie ustawione
        }
        if (savedExpenseTarget > 0.001f) {
            expenseTargetInput.setText(String.format(Locale.US, "%.2f", savedExpenseTarget));
        } else {
            expenseTargetInput.setText(""); // Czyścimy pole
        }
        Log.d(TAG, "Załadowano i wyświetlono wpisany budżet - Przychód: " + savedIncome + ", Cel Wydatków: " + savedExpenseTarget);
    }

    // Ustawia listenery dla przycisków
    private void setupButtonListeners() {
        // Co robi przycisk "Zapisz budżet"
        if(saveBudgetButton != null) {
            saveBudgetButton.setOnClickListener(v -> saveBudgetInputs());
        }
        // Co robi przycisk "Wydatki"
        if (showExpensesButton != null) {
            showExpensesButton.setOnClickListener(v -> {
                Log.d(TAG, "Kliknięto przycisk 'Wydatki', odpalam ExpensesActivity."); // <<<< ZAKTUALIZOWANY log >>>>
                Intent intent = new Intent(HomeActivity.this, ExpensesActivity.class);
                // Opcjonalnie: Dodaj flagi jeśli potrzebne, np. żeby przenieść na wierzch jak już działa
                // intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent); // Odpalamy ekran Wydatków
            });
        }
    }

    // Zapisuje wpisane wartości budżetu do SharedPreferences
    private void saveBudgetInputs() {
        // Zamieniamy przecinek na kropkę dla spójnego parsowania, usuwamy białe znaki z początku/końca
        String incomeStr = incomeInput.getText().toString().replace(',', '.').trim();
        String expenseTargetStr = expenseTargetInput.getText().toString().replace(',', '.').trim();

        // Sprawdzamy, czy oba pola są wypełnione
        if (incomeStr.isEmpty() || expenseTargetStr.isEmpty()) {
            showToast(getString(R.string.toast_input_required)); // "Wypełnij oba pola"
            return; // Przerywamy
        }

        try {
            // Próbujemy sparsować wpisane wartości na float
            float newIncome = Float.parseFloat(incomeStr);
            float newExpenseTarget = Float.parseFloat(expenseTargetStr);

            // Walidacja wartości
            if (newIncome <= 0 || newExpenseTarget < 0) { // Przychód musi być > 0, cel może być 0
                showToast(getString(R.string.toast_income_positive)); // "Przychód musi być dodatni"
                return; // Przerywamy
            }

            // Pobieramy obecne zapisane wartości, żeby sprawdzić, czy faktycznie się zmieniły
            float savedIncome = sharedPreferences.getFloat(MONTHLY_INCOME_KEY, 0.0f);
            float savedExpenseTarget = sharedPreferences.getFloat(MONTHLY_EXPENSE_TARGET_KEY, 0.0f);

            // Sprawdzamy, czy wartości faktycznie się zmieniły (z małą tolerancją dla floatów)
            boolean incomeChanged = Math.abs(newIncome - savedIncome) > 0.001f;
            boolean targetChanged = Math.abs(newExpenseTarget - savedExpenseTarget) > 0.001f;
            boolean budgetChanged = incomeChanged || targetChanged; // Czy cokolwiek się zmieniło?

            // Zapisujemy do SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat(MONTHLY_INCOME_KEY, newIncome);
            editor.putFloat(MONTHLY_EXPENSE_TARGET_KEY, newExpenseTarget);

            // Ustawiamy flagę aktualizacji TYLKO jeśli budżet faktycznie się zmienił
            // To zapewnia, że ExpensesActivity przeliczy limit tylko wtedy, gdy to konieczne
            editor.putBoolean(BUDGET_UPDATED_FLAG_KEY, budgetChanged);
            if (budgetChanged) {
                Log.i(TAG, "Wartości budżetu zmienione. Przychód: " + newIncome + ", Cel: " + newExpenseTarget + ". Ustawiam flagę aktualizacji.");
            } else {
                Log.i(TAG, "Wartości budżetu się nie zmieniły. Flaga aktualizacji ustawiona na false.");
            }

            editor.apply(); // Używamy apply() do asynchronicznego zapisu

            Log.i(TAG, "Wpisany budżet pomyślnie zapisany.");
            showToast(getString(R.string.toast_save_budget_success)); // "Budżet zapisany!"

            // Opcjonalnie: Schowaj klawiaturę lub wyczyść focus po zapisaniu
            // InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            // imm.hideSoftInputFromWindow(incomeInput.getWindowToken(), 0);
            // incomeInput.clearFocus();
            // expenseTargetInput.clearFocus();

        } catch (NumberFormatException e) {
            // Błąd parsowania (np. wpisano tekst zamiast liczby)
            showToast(getString(R.string.toast_invalid_number_format)); // "Nieprawidłowy format liczby"
            Log.e(TAG, "Błąd parsowania wpisanych wartości budżetu na float. Wpisany Przychód: '" + incomeStr + "', Wpisany Cel: '" + expenseTargetStr + "'", e);
        } catch (Exception e) {
            // Inny, niespodziewany błąd zapisu
            showToast(getString(R.string.error_saving_settings)); // Bardziej ogólny błąd zapisu
            Log.e(TAG, "Nieoczekiwany błąd podczas zapisywania wpisanego budżetu.", e);
        }
    }

    // --- Logika Uruchamiania Aparatu ---
    // Sprawdza pozwolenie i odpala aparat
    private void checkCameraPermissionAndOpenCamera() {
        // Sprawdzamy, czy mamy pozwolenie
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(); // Mamy, odpalamy aparat
        } else {
            // Można by tu pokazać wyjaśnienie, dlaczego potrzebujemy pozwolenia?
            Log.i(TAG, "Proszę o pozwolenie na aparat.");
            permissionLauncher.launch(Manifest.permission.CAMERA); // Prosimy o pozwolenie
        }
    }

    // Odpala intencję aparatu
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // Intencja zrobienia zdjęcia
        // Sprawdzamy, czy jest apka aparatu
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                Log.d(TAG, "Odpalam intencję aparatu...");
                cameraLauncher.launch(takePictureIntent); // Używamy naszego launchera
            } catch (Exception e) {
                Log.e(TAG, "Nie udało się odpalić intencji aparatu", e);
                showToast(getString(R.string.error_camera_launch));
            }
        } else {
            // Nie ma apki aparatu
            Log.w(TAG, "Brak dostępnej apki aparatu.");
            showToast(getString(R.string.error_no_camera_app));
        }
    }

    // Odpala ExpensesActivity i przekazuje jej zrobione zdjęcie (bitmapę)
    private void launchExpensesActivityWithBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Próbowano odpalić ExpensesActivity z bitmapą null.");
            showToast(getString(R.string.error_processing_image));
            return;
        }
        Log.d(TAG, "Odpalam ExpensesActivity ze zrobioną bitmapą.");
        Intent intent = new Intent(this, ExpensesActivity.class);
        // Dodajemy bitmapę jako dodatek (extra) do intencji, używając naszego klucza
        intent.putExtra(EXTRA_BITMAP_FOR_PROCESSING, bitmap);
        // Flaga, żeby przenieść ExpensesActivity na wierzch, jeśli już działa
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent); // Odpalamy!
    }
    // --- Koniec Logiki Aparatu ---

    // Konfiguruje dolny pasek nawigacji
    private void configureBottomNavigationView() {
        if (bottomNavigationView == null) {
            Log.e(TAG, "BottomNavigationView jest null, nie mogę skonfigurować.");
            return;
        }
        // Co się dzieje po kliknięciu ikonki
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId(); // ID klikniętej ikonki
            Intent intent = null; // Intencja do odpalenia nowego ekranu

            // Sprawdzamy, którą ikonkę kliknięto
            if (itemId == R.id.navigation_home) {
                // Już tu jesteśmy, nic nie rób, ale potwierdź obsłużenie
                return true;
            } else if (itemId == R.id.navigation_calendar) {
                intent = new Intent(HomeActivity.this, CalendarActivity.class);
            } else if (itemId == R.id.navigation_menu) { // Zakładamy, że to Ustawienia
                intent = new Intent(HomeActivity.this, SettingsActivity.class);
            } else if (itemId == R.id.navigation_camera) {
                // Kliknięto ikonkę aparatu
                Log.d(TAG, "Kliknięto przycisk aparatu w dolnej nawigacji.");
                checkCameraPermissionAndOpenCamera(); // Odpalamy logikę aparatu
                return false; // Zwracamy false == nie zaznaczaj ikonki aparatu wizualnie
            }

            // Odpal nową aktywność, jeśli stworzyliśmy intencję
            if (intent != null) {
                // Flagi, żeby użyć istniejącej instancji aktywności, jeśli już działa
                // i uniknąć animacji przejścia dla płynniejszego działania paska nawigacji
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0); // Wyłącz animacje
                return true; // Potwierdź obsłużenie zdarzenia
            }

            return false; // Zdarzenie nieobsłużone
        });

        // Upewnij się, że ikonka Home jest zaznaczona, gdy aktywność startuje/wraca
        bottomNavigationView.setSelectedItemId(R.id.navigation_home);
    }

    // Pokazuje prosty toast (dymek)
    private void showToast(String message) {
        // Podstawowe pokazanie toasta
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else {
            // Nie pokazuj pustego toasta
            Log.w(TAG, "Próbowano pokazać pusty lub null toast.");
        }
    }
}