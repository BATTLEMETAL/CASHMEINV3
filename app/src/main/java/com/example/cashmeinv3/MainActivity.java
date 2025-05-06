package com.example.cashmeinv3;

// Importy Androida, sieci, logowania itd.
import androidx.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;

// Importy do obsługi wyników aktywności (np. z logowania Google)
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; // Zwykle BaseActivity

// Importy do Facebooka
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

// Importy do Google Sign-In
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

// Importy do Firebase Authentication (logowanie Google, Facebook, Email)
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Arrays; // Do listy uprawnień Facebooka

// Główny ekran logowania
public class MainActivity extends BaseActivity { // Dziedziczymy po BaseActivity dla spójności (np. motywu)

    private static final String TAG = "MainActivity"; // Do logów, żeby łatwiej debugować

    // Firebase Authentication - główny obiekt do logowania/rejestracji
    private FirebaseAuth mAuth;

    // CallbackManager dla Facebooka - do obsługi odpowiedzi z logowania FB
    private CallbackManager mCallbackManager;

    // Klient Google Sign-In - do logowania przez Google
    private GoogleSignInClient mGoogleSignInClient;

    // Launcher do obsługi wyniku logowania Google (nowy sposób)
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Sprawdzamy, czy logowanie Google się udało i czy są dane
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // Pobieramy zadanie (Task) z wynikiem logowania
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    // Przekazujemy zadanie do obsługi
                    handleGoogleSignInResult(task);
                } else {
                    // Coś poszło nie tak z logowaniem Google
                    Log.w(TAG, "Logowanie przez Google nie powiodło się: resultCode=" + result.getResultCode());
                    showToast("Logowanie przez Google nie powiodło się");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Standardowe rzeczy na start
        setContentView(R.layout.activity_main); // Ustawiamy layout z pliku activity_main.xml

        // Inicjalizacja Firebase Auth - pobieramy instancję
        mAuth = FirebaseAuth.getInstance();

        // Inicjalizacja CallbackManagera dla Facebooka
        mCallbackManager = CallbackManager.Factory.create();

        // Konfiguracja Google Sign-In - ustawiamy, co chcemy od Google
        configureGoogleSignIn();

        // Podpinamy przyciski logowania i ich akcje
        configureLoginButtons();
    }

    // Ustawia opcje logowania przez Google (potrzebuje ID klienta webowego z Firebase/Google Cloud)
    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                // Pobieramy ID token potrzebny do autentykacji Firebase
                .requestIdToken(getString(R.string.default_web_client_id))
                // Prosimy o dostęp do emaila użytkownika
                .requestEmail()
                .build();
        // Tworzymy klienta Google Sign-In
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    // Znajduje przyciski w layoucie i ustawia im listenery (co mają robić po kliknięciu)
    private void configureLoginButtons() {
        // Logowanie Google
        ImageView googleSignInButton = findViewById(R.id.googleIcon);
        googleSignInButton.setOnClickListener(v -> {
            if (isNetworkAvailable()) { // Sprawdzamy neta
                signInWithGoogle();     // Jak jest, odpalamy logowanie Google
            } else {
                showToast("Brak połączenia z internetem"); // Jak nie ma, to mówimy userowi
            }
        });

        // Logowanie Facebook
        ImageView facebookSignInButton = findViewById(R.id.facebookIcon);
        facebookSignInButton.setOnClickListener(v -> {
            if (isNetworkAvailable()) { // Sprawdzamy neta
                signInWithFacebook();   // Jak jest, odpalamy logowanie FB
            } else {
                showToast("Brak połączenia z internetem"); // Jak nie ma, to mówimy userowi
            }
        });

        // Pola do wpisania emaila i hasła
        EditText emailEditText = findViewById(R.id.emailEditText);
        EditText passwordEditText = findViewById(R.id.passwordEditText);

        // Przycisk logowania standardowego (email/hasło)
        findViewById(R.id.loginButton).setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim(); // Bierzemy email, usuwamy białe znaki
            String password = passwordEditText.getText().toString().trim(); // Bierzemy hasło
            if (!email.isEmpty() && !password.isEmpty()) { // Sprawdzamy, czy nie są puste
                loginUserWithEmail(email, password);       // Jak nie są, próbujemy zalogować
            } else {
                showToast("Proszę podać email i hasło");     // Jak są, to prosimy o wypełnienie
            }
        });

        // Przycisk rejestracji (email/hasło)
        findViewById(R.id.registerButton).setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            if (!email.isEmpty() && !password.isEmpty()) {
                registerUserWithEmail(email, password);    // Jak nie są puste, próbujemy zarejestrować
            } else {
                showToast("Proszę podać email i hasło");     // Jak są, to prosimy o wypełnienie
            }
        });

        // Tekst "Zapomniałem hasła"
        TextView forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);
        forgotPasswordTextView.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim(); // Bierzemy email
            if (!email.isEmpty()) { // Sprawdzamy, czy nie jest pusty
                resetPassword(email); // Jak nie jest, wysyłamy maila resetującego
            } else {
                showToast("Proszę podać email"); // Jak jest pusty, prosimy o podanie
            }
        });
    }

    // Wysyła email z linkiem do zresetowania hasła przez Firebase
    private void resetPassword(String email) {
        mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { // Jak się udało wysłać
                showToast("Instrukcje resetowania hasła zostały wysłane na email");
            } else { // Jak się nie udało
                Log.e(TAG, "Resetowanie hasła nie powiodło się", task.getException());
                // Pokazujemy błąd userowi (lepiej go jakoś uprościć)
                showToast("Wystąpił błąd podczas resetowania hasła: " + task.getException().getMessage());
            }
        });
    }

    // Odpala proces logowania przez Google (pokazuje okienko wyboru konta)
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent(); // Tworzymy intencję logowania
        googleSignInLauncher.launch(signInIntent); // Odpalamy ją przez nasz launcher
    }

    // Obsługuje wynik logowania Google (po powrocie z okienka wyboru konta)
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            // Pobieramy konto Google z wyniku zadania
            GoogleSignInAccount account = completedTask.getResult(Exception.class);
            if (account != null) {
                // Mamy konto Google, teraz logujemy się nim do Firebase
                firebaseAuthWithGoogle(account);
            }
        } catch (Exception e) {
            // Jakiś błąd przy pobieraniu konta Google
            Log.e(TAG, "Logowanie przez Google nie powiodło się", e);
            showToast("Logowanie przez Google nieudane: " + e.getMessage());
        }
    }

    // Loguje użytkownika do Firebase przy użyciu danych z konta Google
    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        // Tworzymy dane uwierzytelniające (credential) dla Firebase z tokena ID Google
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        // Logujemy się do Firebase tymi danymi
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { // Jak się udało zalogować do Firebase
                updateUI(mAuth.getCurrentUser()); // Przechodzimy do głównej części apki
            } else { // Jak się nie udało zalogować do Firebase
                Log.e(TAG, "Autoryzacja Google nie powiodła się", task.getException());
                showToast("Błąd autoryzacji Google: " + task.getException().getMessage());
            }
        });
    }

    // Odpala proces logowania przez Facebook
    private void signInWithFacebook() {
        // Prosimy o pozwolenie na odczyt emaila i profilu publicznego
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
        // Rejestrujemy callback do obsługi wyniku logowania FB
        LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) { // Jak się udało zalogować do FB
                // Przekazujemy token dostępowy FB do dalszej obsługi (logowania do Firebase)
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() { // Jak user anulował
                showToast("Logowanie przez Facebook anulowane");
            }

            @Override
            public void onError(FacebookException error) { // Jak wystąpił błąd FB
                Log.e(TAG, "Logowanie przez Facebook nie powiodło się", error);
                showToast("Błąd logowania przez Facebook: " + error.getMessage());
            }
        });
    }

    // Loguje użytkownika do Firebase przy użyciu tokena dostępowego Facebooka
    private void handleFacebookAccessToken(AccessToken token) {
        // Tworzymy dane uwierzytelniające (credential) dla Firebase z tokena FB
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        // Logujemy się do Firebase tymi danymi
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { // Jak się udało zalogować do Firebase
                updateUI(mAuth.getCurrentUser()); // Przechodzimy do głównej części apki
            } else { // Jak się nie udało zalogować do Firebase
                Log.e(TAG, "Autoryzacja Facebook nie powiodła się", task.getException());
                showToast("Błąd autoryzacji Facebook: " + task.getException().getMessage());
            }
        });
    }

    // Loguje użytkownika przy użyciu emaila i hasła
    private void loginUserWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { // Jak się udało zalogować
                updateUI(mAuth.getCurrentUser()); // Przechodzimy do apki
            } else { // Jak się nie udało
                Log.e(TAG, "Logowanie przez email nie powiodło się", task.getException());
                showToast("Logowanie nieudane: " + task.getException().getMessage());
            }
        });
    }

    // Rejestruje nowego użytkownika przy użyciu emaila i hasła
    private void registerUserWithEmail(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) { // Jak się udało zarejestrować
                updateUI(mAuth.getCurrentUser()); // Przechodzimy do apki
            } else { // Jak się nie udało
                Log.e(TAG, "Rejestracja nie powiodła się", task.getException());
                showToast("Rejestracja nieudana: " + task.getException().getMessage());
            }
        });
    }

    // Aktualizuje interfejs użytkownika po zalogowaniu (przechodzi do HomeActivity)
    private void updateUI(FirebaseUser user) {
        if (user != null) { // Sprawdzamy, czy na pewno jesteśmy zalogowani
            // Odpalamy HomeActivity
            startActivity(new Intent(this, HomeActivity.class));
            // Zamykamy obecną aktywność (MainActivity), żeby user nie mógł do niej wrócić przyciskiem "wstecz"
            finish();
        } else {
            // To nie powinno się zdarzyć, ale na wszelki wypadek logujemy
            Log.w(TAG, "Użytkownik jest null w updateUI");
        }
    }

    // Sprawdza, czy jest dostępne połączenie z internetem
    private boolean isNetworkAvailable() {
        // Pobieramy manager łączności systemowej
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // Pobieramy informacje o aktywnej sieci
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        // Sprawdzamy, czy mamy możliwości sieciowe i czy jedną z nich jest dostęp do internetu
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // Pokazuje dłuższy toast (dymek)
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Przekazuje wynik aktywności (np. z logowania FB) do CallbackManagera Facebooka
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Ważne, żeby Facebook SDK mogło przetworzyć odpowiedź z logowania
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }
}