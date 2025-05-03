package my.edu.utar.bananamusic.auth;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.utils.FirebaseApiChecker;
import my.edu.utar.bananamusic.utils.FirebaseAuthHelper;
import my.edu.utar.bananamusic.utils.FirebaseConfig;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int MIN_PASSWORD_LENGTH = 6;
    
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private TextView tvForgotPassword, tvSignup;
    private SignInButton googleSignInButton;
    private ProgressBar progressBar;
    private FirebaseAuthHelper authHelper;
    private View rootView;
    private GoogleSignInClient googleSignInClient;
    
    // Activity result launcher for Google sign-in
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // Handle Google sign-in result
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleGoogleSignIn(task);
                } else {
                    // Failed to start activity or user canceled
                    setLoading(false);
                    showError("Google sign-in failed or was canceled");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        rootView = findViewById(R.id.loginLayout);

        initializeFirebase();
        initViews();
        setClickListeners();
        setupGoogleSignIn();
    }

    private void initializeFirebase() {
        try {
            authHelper = FirebaseAuthHelper.getInstance();
            
            // Check if Firebase APIs are enabled
            // Remove this line to prevent the Firestore API not enabled message
            // FirebaseApiChecker.checkFirestoreAvailability(this, FirebaseConfig.PROJECT_ID);
            
            // Check if user is already logged in
            if (authHelper.isUserLoggedIn()) {
                navigateToMainActivity();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error: " + e.getMessage());
            showError("Firebase error: " + e.getMessage());
        }
    }

    private void initViews() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.emailEditText);
        etPassword = findViewById(R.id.passwordEditText);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignup = findViewById(R.id.signupButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        progressBar = findViewById(R.id.progressBar);

        // Set error text appearance
        tilEmail.setErrorTextAppearance(R.style.ErrorText);
        tilPassword.setErrorTextAppearance(R.style.ErrorText);
    }

    private void setupGoogleSignIn() {
        try {
            // Web client ID from Google Cloud Console (replace with your actual client ID)
            String webClientId = getString(R.string.web_client_id);
            
            // Initialize Google Sign-In client
            googleSignInClient = authHelper.initGoogleSignIn(this, webClientId);
            
            // Customize the Google Sign-In button
            googleSignInButton.setSize(SignInButton.SIZE_WIDE);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Google Sign-In: " + e.getMessage());
            googleSignInButton.setEnabled(false);
            showError("Google Sign-In setup failed");
        }
    }

    private void setClickListeners() {
        btnLogin.setOnClickListener(v -> loginUser());

        tvForgotPassword.setOnClickListener(v -> handleForgotPassword());

        tvSignup.setOnClickListener(v -> navigateToSignup());
        
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());

        // Add text change listeners to clear errors
        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) tilEmail.setError(null);
        });

        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) tilPassword.setError(null);
        });
    }
    
    private void signInWithGoogle() {
        try {
            setLoading(true);
            Intent signInIntent = authHelper.getGoogleSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        } catch (Exception e) {
            setLoading(false);
            Log.e(TAG, "Error starting Google Sign-In: " + e.getMessage());
            showError("Google Sign-In failed to start: " + e.getMessage());
        }
    }
    
    private void handleGoogleSignIn(Task<GoogleSignInAccount> completedTask) {
        authHelper.handleGoogleSignInResult(completedTask, new FirebaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                navigateToMainActivity();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                showError(errorMessage);
            }
        });
    }

    private void handleForgotPassword() {
        String email = etEmail.getText().toString().trim();
        
        if (!isValidEmail(email)) {
            tilEmail.setError("Please enter a valid email address");
            return;
        }

        setLoading(true);
        authHelper.resetPassword(email, new FirebaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                showSuccess("Password reset email sent to " + email);
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                showError(errorMessage);
            }
        });
    }

    private void loginUser() {
        // Clear previous errors
        clearErrors();

        // Get and validate input
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!validateLoginInput(email, password)) {
            return;
        }

        // Attempt login
        setLoading(true);
        authHelper.signIn(email, password, new FirebaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                navigateToMainActivity();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                handleLoginError(errorMessage);
            }
        });
    }

    private boolean validateLoginInput(String email, String password) {
        boolean isValid = true;

        if (!isValidEmail(email)) {
            tilEmail.setError("Please enter a valid email address");
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Please enter your password");
            isValid = false;
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            tilPassword.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            isValid = false;
        }

        return isValid;
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void handleLoginError(String errorMessage) {
        if (errorMessage.contains("password")) {
            tilPassword.setError(errorMessage);
        } else if (errorMessage.contains("email")) {
            tilEmail.setError(errorMessage);
        } else {
            showError(errorMessage);
        }
    }

    private void navigateToMainActivity() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    private void navigateToSignup() {
        startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        // Don't finish this activity so users can come back
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        tvForgotPassword.setEnabled(!isLoading);
        tvSignup.setEnabled(!isLoading);
        googleSignInButton.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
    }

    private void showError(String message) {
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.colorError));
        snackbar.show();
    }

    private void showSuccess(String message) {
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.colorSuccess));
        snackbar.show();
    }
} 