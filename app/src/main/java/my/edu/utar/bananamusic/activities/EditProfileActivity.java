package my.edu.utar.bananamusic.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import my.edu.utar.bananamusic.R;

public class EditProfileActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private FirebaseStorage mStorage;
    private StorageReference mStorageRef;
    private FirebaseUser currentUser;

    private ShapeableImageView ivProfilePicture;
    private TextInputEditText etDisplayName;
    private TextInputEditText etBio;
    private AutoCompleteTextView spinnerMood;
    private AutoCompleteTextView spinnerGenre;
    private MaterialButton btnSave, btnCancel, btnChangePicture;
    private View loadingOverlay;

    private Uri selectedImageUri = null;
    private boolean imageChanged = false;
    private static final int REQUEST_PERMISSION_CODE = 1001;

    private static final String[] MOODS = {
        "Happy", "Sad", "Energetic", "Relaxed", "Romantic", "Focused"
    };

    private static final String[] GENRES = {
        "Pop", "Rock", "Hip Hop", "R&B", "Jazz", "Classical", "Electronic", "Country", 
        "Folk", "Indie", "Metal", "Blues", "Reggae", "K-pop", "J-pop"
    };

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initFirebase();
        initViews();
        setupToolbar();
        setupDropdowns();
        registerImagePicker();
        loadUserData();
        setClickListeners();
    }

    private void registerImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        // Show a progress indicator while loading the image
                        ivProfilePicture.setImageResource(R.drawable.default_profile_picture);
                        
                        // Load the image with Glide
                        Glide.with(this)
                            .load(selectedImageUri)
                            .placeholder(R.drawable.default_profile_picture)
                            .error(R.drawable.default_profile_picture)
                            .circleCrop()
                            .into(ivProfilePicture);
                        
                        imageChanged = true;
                        
                        // Show feedback to user
                        Snackbar.make(ivProfilePicture, "Profile picture selected! Don't forget to save your changes.", 
                                Snackbar.LENGTH_LONG).show();
                        
                        // Make the save button more prominent
                        btnSave.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
                        btnSave.setStrokeWidth(0);
                    }
                }
            }
        );
    }

    private void initFirebase() {
        try {
            // Initialize Firebase instances
            mAuth = FirebaseAuth.getInstance();
            mFirestore = FirebaseFirestore.getInstance();
            mStorage = FirebaseStorage.getInstance();
            
            // Get current user
            currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Initialize storage reference with user-specific path
            mStorageRef = mStorage.getReference().child("users").child(currentUser.getUid()).child("profile_images");
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing Firebase: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        etDisplayName = findViewById(R.id.etDisplayName);
        etBio = findViewById(R.id.etBio);
        spinnerMood = findViewById(R.id.spinnerMood);
        spinnerGenre = findViewById(R.id.spinnerGenre);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        btnChangePicture = findViewById(R.id.btnChangePicture);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Profile");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupDropdowns() {
        // Setup Mood dropdown
        ArrayAdapter<String> moodAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_dropdown,
            MOODS
        );
        spinnerMood.setAdapter(moodAdapter);

        // Setup Genre dropdown
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(
            this,
            R.layout.item_dropdown,
            GENRES
        );
        spinnerGenre.setAdapter(genreAdapter);
    }

    private void loadUserData() {
        if (currentUser != null) {
            setLoading(true);
            mFirestore.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        setLoading(false);
                        if (documentSnapshot.exists()) {
                            String displayName = documentSnapshot.getString("displayName");
                            String bio = documentSnapshot.getString("bio");
                            String mood = documentSnapshot.getString("mood");
                            String genre = documentSnapshot.getString("favoriteGenre");
                            String profilePicUrl = documentSnapshot.getString("profilePicUrl");

                            if (displayName != null) etDisplayName.setText(displayName);
                            if (bio != null) etBio.setText(bio);
                            if (mood != null && !mood.isEmpty()) {
                                spinnerMood.setText(mood, false);
                            }
                            if (genre != null && !genre.isEmpty()) {
                                spinnerGenre.setText(genre, false);
                            }

                            if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(profilePicUrl)
                                        .placeholder(R.drawable.default_profile_picture)
                                        .circleCrop()
                                        .into(ivProfilePicture);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Snackbar.make(btnSave, "Error loading profile: " + e.getMessage(), 
                                Snackbar.LENGTH_LONG).show();
                    });
        }
    }

    private void setClickListeners() {
        btnChangePicture.setOnClickListener(v -> checkPermissionAndOpenPicker());
        
        btnSave.setOnClickListener(v -> saveProfile());
        
        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void checkPermissionAndOpenPicker() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 and above, check READ_MEDIA_IMAGES permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
                        REQUEST_PERMISSION_CODE);
            } else {
                openImagePicker();
            }
        } else {
            // For Android 12 and below, check READ_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                        REQUEST_PERMISSION_CODE);
            } else {
                openImagePicker();
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Snackbar.make(btnSave, "Permission denied. Cannot select profile picture. Please enable in Settings.", 
                        Snackbar.LENGTH_LONG)
                        .setAction("Settings", v -> {
                            // Open app settings
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        })
                        .show();
            }
        }
    }
    
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void saveProfile() {
        if (currentUser == null) return;

        String displayName = etDisplayName.getText().toString().trim();
        String bio = etBio.getText().toString().trim();
        String mood = spinnerMood.getText().toString().trim();
        String genre = spinnerGenre.getText().toString().trim();

        // Validate input
        if (displayName.isEmpty()) {
            etDisplayName.setError("Display name is required");
            etDisplayName.requestFocus();
            return;
        }

        setLoading(true);

        // If image was changed, upload it first
        if (imageChanged && selectedImageUri != null) {
            uploadImageAndSaveProfile(displayName, bio, mood, genre);
        } else {
            // No image change, just update profile data
            updateProfileInfo(displayName, bio, mood, genre, null);
        }
    }
    
    private void uploadImageAndSaveProfile(String displayName, String bio, String mood, String genre) {
        if (selectedImageUri == null) {
            // If no new image is selected, just update the profile info
            updateProfileInfo(displayName, bio, mood, genre, null);
            return;
        }

        // Show loading state
        setLoading(true);
        showLoadingMessage("Uploading image...");

        // Create a simple storage reference
        StorageReference imageRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images")
                .child(currentUser.getUid() + "_" + System.currentTimeMillis() + ".jpg");

        try {
            // Convert Uri to byte array
            byte[] imageData = getBytes(selectedImageUri);
            
            // Upload the image
            UploadTask uploadTask = imageRef.putBytes(imageData);
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                // Get the download URL
                imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    updateProfileInfo(displayName, bio, mood, genre, imageUrl);
                }).addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Failed to get download URL: " + e.getMessage());
                });
            }).addOnFailureListener(e -> {
                setLoading(false);
                showError("Failed to upload image: " + e.getMessage());
            });
        } catch (IOException e) {
            setLoading(false);
            showError("Failed to prepare image: " + e.getMessage());
        }
    }

    private byte[] getBytes(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(EditProfileActivity.this, message, Toast.LENGTH_LONG).show();
            setLoading(false);
        });
    }

    private void showLoadingMessage(String message) {
        TextView loadingText = findViewById(R.id.loadingText);
        if (loadingText != null) {
            loadingText.setText(message);
        }
    }

    private void updateProfileInfo(String displayName, String bio, String mood, String genre, String profilePicUrl) {
        // Update display name in Firebase Auth
        UserProfileChangeRequest.Builder profileBuilder = new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName);
                
        if (profilePicUrl != null) {
            profileBuilder.setPhotoUri(Uri.parse(profilePicUrl));
        }
                
        UserProfileChangeRequest profileUpdates = profileBuilder.build();

        // Show a more descriptive loading message
        TextView loadingText = findViewById(R.id.loadingText);
        if (loadingText != null) {
            loadingText.setText("Updating profile data...");
        }

        currentUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Update additional data in Firestore
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("displayName", displayName);
                        updates.put("bio", bio);
                        updates.put("mood", mood);
                        updates.put("favoriteGenre", genre);
                        
                        if (profilePicUrl != null) {
                            updates.put("profilePicUrl", profilePicUrl);
                        }

                        mFirestore.collection("users").document(currentUser.getUid())
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    setLoading(false);
                                    Snackbar.make(btnSave, "Profile updated successfully", 
                                            Snackbar.LENGTH_SHORT).show();
                                    
                                    // Give time for the snackbar to be shown before finishing
                                    btnSave.postDelayed(this::finish, 1500);
                                })
                                .addOnFailureListener(e -> {
                                    setLoading(false);
                                    Snackbar.make(btnSave, "Error updating profile: " + e.getMessage(),
                                            Snackbar.LENGTH_LONG).show();
                                });
                    } else {
                        setLoading(false);
                        Snackbar.make(btnSave, "Error updating profile",
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        loadingOverlay.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!isLoading);
        btnCancel.setEnabled(!isLoading);
        etDisplayName.setEnabled(!isLoading);
        etBio.setEnabled(!isLoading);
        spinnerMood.setEnabled(!isLoading);
        spinnerGenre.setEnabled(!isLoading);
        btnChangePicture.setEnabled(!isLoading);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 