package com.example.cashmeinv3;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate; // Import potrzebny do trybu nocnego

public class BaseActivity extends AppCompatActivity {

    public static final String PREFERENCES_FILE = "com.example.cashmeinv3.PREFERENCES";
    public static final String KEY_THEME = "theme";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Zastosuj ustawienia motywu NAJPIERW
        applyAppThemeSettings(); // Zmieniono nazwę, aby uniknąć pomyłki z metodą setTheme() Androida

        // 2. Wywołaj super.onCreate() PO zastosowaniu ustawień motywu
        super.onCreate(savedInstanceState);

        // Uwaga: setContentView() zostanie wywołane w metodzie onCreate() aktywności potomnej
    }

    // Zastosuj motyw używając AppCompatDelegate (preferowane nad setTheme dla trybu dzień/noc)
    // lub kontynuuj używanie setTheme, jeśli masz tylko odrębne style niezależne od trybu nocnego.
    private void applyAppThemeSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);
        String selectedTheme = sharedPreferences.getString(KEY_THEME, "dark"); // Domyślnie ustaw na ciemny

        // Opcja A: Użycie AppCompatDelegate (Dobrze obsługuje przełączanie Dzień/Noc)
        switch (selectedTheme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "system":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            // Dodaj przypadek dla "high_contrast", jeśli jest potrzebny, przypisz go do MODE_NIGHT_NO lub YES
            // w zależności od tego, czy twój motyw wysokiego kontrastu jest oparty na jasnym czy ciemnym tle.
            // Lub użyj Opcji B, jeśli Wysoki Kontrast to całkowicie osobny styl.
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); // Domyślny
                break;
        }

       /* // Opcja B: Użycie setTheme() (Jeśli style są całkowicie oddzielne)
          // Upewnij się, że to wykonuje się *przed* super.onCreate()
       switch (selectedTheme) {
            case "light":
                setTheme(R.style.Theme_CASHMEINV3_Light);
                break;
            case "dark":
                setTheme(R.style.Theme_CASHMEINV3_Dark);
                break;
            case "system":
                 // setTheme() nie wspiera bezpośrednio motywu systemowego w łatwy sposób.
                 // Może być konieczne ręczne sprawdzenie stanu systemu, jeśli używasz tej metody.
                 // Lepiej użyć AppCompatDelegate (Opcja A) dla motywu systemowego.
                setTheme(R.style.Theme_CASHMEINV3_System); // Lub twój motyw bazowy
                break;
            case "high_contrast":
                setTheme(R.style.Theme_CASHMEINV3_HighContrast);
                break;
            default:
                setTheme(R.style.Theme_CASHMEINV3_Dark); // Motyw domyślny
                break;
        } */
    }
}