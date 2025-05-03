package my.edu.utar.bananamusic.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.utils.FirebaseConfig;

public class SignupActivity extends AppCompatActivity {
    private static final String TAG = "SignupActivity";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MIN_DISPLAY_NAME_LENGTH = 3;
    private static final Pattern DISPLAY_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\- ]+$");
    
    private static final List<String> MOODS = Arrays.asList(
        "Happy", "Sad", "Energetic", "Relaxed", "Romantic", "Focused"
    );
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    
    private TextInputLayout tilDisplayName, tilEmail, tilPassword, tilConfirmPassword, tilMood;
    private TextInputEditText etDisplayName, etEmail, etPassword, etConfirmPassword;
    private AutoCompleteTextView spinnerMood;
    private MaterialButton btnSignup;
    private TextView tvLoginLink;
    private ProgressBar progressBar;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        rootView = findViewById(R.id.signupLayout);
        
        initializeFirebase();
        initViews();
        setupMoodSpinner();
        setClickListeners();
    }
    
    private void initializeFirebase() {
        try {
            mAuth = FirebaseAuth.getInstance();
            mFirestore = FirebaseFirestore.getInstance();
            
            // Check if user is already logged in
            if (mAuth.getCurrentUser() != null) {
                navigateToMainActivity();
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error: " + e.getMessage());
            showError("Firebase error: " + e.getMessage());
        }
    }

    private void initViews() {
        tilDisplayName = findViewById(R.id.tilDisplayName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilMood = findViewById(R.id.tilMood);
        
        etDisplayName = findViewById(R.id.etDisplayName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        spinnerMood = findViewById(R.id.spinnerMood);
        
        btnSignup = findViewById(R.id.btnSignup);
        tvLoginLink = findViewById(R.id.tvLoginLink);
        progressBar = findViewById(R.id.progressBar);

        // Set error text appearance
        tilDisplayName.setErrorTextAppearance(R.style.ErrorText);
        tilEmail.setErrorTextAppearance(R.style.ErrorText);
        tilPassword.setErrorTextAppearance(R.style.ErrorText);
        tilConfirmPassword.setErrorTextAppearance(R.style.ErrorText);
        tilMood.setErrorTextAppearance(R.style.ErrorText);
    }

    private void setupMoodSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            R.layout.item_dropdown,
            MOODS
        );
        spinnerMood.setAdapter(adapter);
        
        // Set default mood to Happy
        spinnerMood.setText(MOODS.get(0), false);
    }

    private void setClickListeners() {
        btnSignup.setOnClickListener(v -> signUp());
        tvLoginLink.setOnClickListener(v -> finish());

        // Add focus change listeners to clear errors
        etDisplayName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) tilDisplayName.setError(null);
        });
        
        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) tilEmail.setError(null);
        });
        
        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) tilPassword.setError(null);
        });
        
        etConfirmPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) tilConfirmPassword.setError(null);
        });

        // Clear errors on text change
        etDisplayName.addTextChangedListener(new TextClearErrorWatcher(tilDisplayName));
        etEmail.addTextChangedListener(new TextClearErrorWatcher(tilEmail));
        etPassword.addTextChangedListener(new TextClearErrorWatcher(tilPassword));
        etConfirmPassword.addTextChangedListener(new TextClearErrorWatcher(tilConfirmPassword));
    }

    private void signUp() {
        if (!validateInputs()) {
            return;
        }

        setLoading(true);
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String displayName = etDisplayName.getText().toString().trim();
        String selectedMood = spinnerMood.getText().toString();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        saveUserToFirestore(displayName, email, selectedMood);
                    } else {
                        setLoading(false);
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        handleSignupError(task.getException());
                    }
                });
    }

    private boolean validateInputs() {
        boolean isValid = true;
        String displayName = etDisplayName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Display Name validation
        if (displayName.isEmpty()) {
            tilDisplayName.setError("Display name is required");
            isValid = false;
        } else if (displayName.length() < MIN_DISPLAY_NAME_LENGTH) {
            tilDisplayName.setError("Display name must be at least " + MIN_DISPLAY_NAME_LENGTH + " characters");
            isValid = false;
        } else if (!DISPLAY_NAME_PATTERN.matcher(displayName).matches()) {
            tilDisplayName.setError("Display name can only contain letters, numbers, spaces, and _ -");
            isValid = false;
        } else {
            tilDisplayName.setError(null);
        }

        // Email validation
        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        // Password validation
        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            isValid = false;
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            tilPassword.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        // Confirm Password validation
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError("Please confirm your password");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords do not match");
            isValid = false;
        } else {
            tilConfirmPassword.setError(null);
        }

        return isValid;
    }

    private void saveUserToFirestore(String displayName, String email, String mood) {
        String userId = mAuth.getCurrentUser().getUid();
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("displayName", displayName);
        userData.put("email", email);
        userData.put("mood", mood);
        userData.put("createdAt", System.currentTimeMillis());
        
        mFirestore.collection(FirebaseConfig.COLLECTION_USERS)
                .document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User data saved to Firestore");
                    
                    // Update display name in Firebase Auth
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build();
                            
                    mAuth.getCurrentUser().updateProfile(profileUpdates)
                            .addOnCompleteListener(task -> {
                                setLoading(false);
                                if (task.isSuccessful()) {
                                    Log.d(TAG, "Display name updated in Firebase Auth");
                                    showSuccess("Account created successfully!");
                                    navigateToMainActivity();
                                } else {
                                    Log.w(TAG, "Error updating display name", task.getException());
                                    // Still proceed to main activity since account is created
                                    navigateToMainActivity();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user data to Firestore", e);
                    setLoading(false);
                    showError("Failed to save user data. Please update your profile later.");
                    navigateToMainActivity();
                });
    }

    private void handleSignupError(Exception exception) {
        String errorMessage = "Failed to create account";
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            errorMessage = "Password is too weak";
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Invalid email format";
        } else if (exception instanceof FirebaseAuthUserCollisionException) {
            errorMessage = "This email is already registered";
        }
        showError(errorMessage);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSignup.setEnabled(!isLoading);
        tvLoginLink.setEnabled(!isLoading);
        etDisplayName.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
        etConfirmPassword.setEnabled(!isLoading);
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

    private static class TextClearErrorWatcher implements TextWatcher {
        private final TextInputLayout textInputLayout;

        TextClearErrorWatcher(TextInputLayout textInputLayout) {
            this.textInputLayout = textInputLayout;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            textInputLayout.setError(null);
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }
} 