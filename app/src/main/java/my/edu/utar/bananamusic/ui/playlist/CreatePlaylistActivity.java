package my.edu.utar.bananamusic.ui.playlist;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.utils.PlaylistManager;

public class CreatePlaylistActivity extends AppCompatActivity {
    private static final String TAG = "CreatePlaylistActivity";
    
    private EditText etPlaylistName;
    private EditText etPlaylistDescription;
    private SwitchMaterial switchCollaborative;
    private ChipGroup moodChipGroup;
    private Spinner spMood; // Keep as fallback
    private ImageView ivCoverImage;
    private Button btnCreate;
    private Button btnCancel;
    private Toolbar toolbar;
    private MaterialCardView coverImageCard;
    private TextInputLayout nameInputLayout;
    private TextInputLayout descriptionInputLayout;
    
    private Uri selectedImageUri;
    private PlaylistManager playlistManager;
    private Map<Integer, String> chipIdToMoodMap = new HashMap<>();
    
    // Activity result launcher for image selection
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        loadImage(selectedImageUri);
                    }
                }
            }
    );
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_playlist);
        
        playlistManager = PlaylistManager.getInstance(this);
        
        initViews();
        setupInputFields();
        setupMoodSelection();
        setupListeners();
        
        // Load default cover image
        loadDefaultCoverImage();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void initViews() {
        // Find and initialize all UI elements
        etPlaylistName = findViewById(R.id.et_playlist_name);
        etPlaylistDescription = findViewById(R.id.et_playlist_description);
        switchCollaborative = findViewById(R.id.switch_collaborative);
        moodChipGroup = findViewById(R.id.mood_chip_group);
        spMood = findViewById(R.id.sp_mood); // Keep as fallback
        ivCoverImage = findViewById(R.id.iv_cover_image);
        btnCreate = findViewById(R.id.btn_create);
        btnCancel = findViewById(R.id.btn_cancel);
        coverImageCard = findViewById(R.id.cover_image_card);
        nameInputLayout = findViewById(R.id.name_input_layout);
        descriptionInputLayout = findViewById(R.id.description_input_layout);
        
        // Setup toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }
    
    private void setupInputFields() {
        // Set up text input fields with appropriate error handling
        nameInputLayout.setErrorEnabled(true);
        descriptionInputLayout.setErrorEnabled(true);
        
        // Clear errors when user starts typing
        etPlaylistName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                nameInputLayout.setError(null);
            }
        });
    }
    
    private void setupMoodSelection() {
        // Set up mood chips with their values
        setupMoodChips();
        
        // Also set up the spinner as fallback
        setupMoodSpinner();
    }
    
    private void setupMoodChips() {
        // Map chip IDs to mood values
        chipIdToMoodMap.put(R.id.chip_mood_happy, "Happy");
        chipIdToMoodMap.put(R.id.chip_mood_sad, "Sad");
        chipIdToMoodMap.put(R.id.chip_mood_energetic, "Energetic");
        chipIdToMoodMap.put(R.id.chip_mood_relaxed, "Relaxed");
        chipIdToMoodMap.put(R.id.chip_mood_romantic, "Romantic");
        
        // Handle chip selection
        moodChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Log.d(TAG, "Selected mood chip ID: " + checkedId);
        });
    }
    
    private void setupMoodSpinner() {
        // Use the string array resource instead of hardcoded values
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.mood_options, R.layout.item_spinner_mood);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spMood.setAdapter(adapter);
    }
    
    private void setupListeners() {
        // Make the entire card clickable for better UX
        coverImageCard.setOnClickListener(v -> openImagePicker());
        ivCoverImage.setOnClickListener(v -> openImagePicker());
        
        btnCreate.setOnClickListener(v -> createPlaylist());
        
        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void loadDefaultCoverImage() {
        // Load default album art from resources with rounded corners
        Glide.with(this)
                .load(R.drawable.ic_playlist_placeholder)
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                .centerCrop()
                .into(ivCoverImage);
                
        // Clear any previously selected image
        selectedImageUri = null;
    }
    
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }
    
    private void loadImage(Uri imageUri) {
        // Add rounded corners to the selected image
        Glide.with(this)
                .load(imageUri)
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                .placeholder(R.drawable.ic_playlist_placeholder)
                .error(R.drawable.ic_playlist_placeholder)
                .centerCrop()
                .into(ivCoverImage);
    }
    
    private void createPlaylist() {
        String name = etPlaylistName.getText().toString().trim();
        String description = etPlaylistDescription.getText().toString().trim();
        boolean isCollaborative = switchCollaborative.isChecked();
        
        // Get mood from selected chip or fallback to spinner
        String mood = getSelectedMood();
        
        if (TextUtils.isEmpty(name)) {
            nameInputLayout.setError("Playlist name is required");
            etPlaylistName.requestFocus();
            return;
        }
        
        // Show loading state
        setLoading(true);
        
        // Create playlist - passing selectedImageUri for custom cover or null for default
        playlistManager.createPlaylist(name, description, isCollaborative, mood, selectedImageUri, 
            new PlaylistManager.PlaylistCallback() {
                @Override
                public void onSuccess(my.edu.utar.bananamusic.models.Playlist playlist) {
                    // Hide loading
                    setLoading(false);
                    
                    // Inform the user with a toast
                    Toast.makeText(CreatePlaylistActivity.this, 
                        "Playlist '" + name + "' created successfully", Toast.LENGTH_SHORT).show();
                    
                    // Set result and finish activity
                    setResult(RESULT_OK);
                    finish();
                }
                
                @Override
                public void onError(String errorMessage) {
                    // Hide loading
                    setLoading(false);
                    
                    // Show error to user
                    Toast.makeText(CreatePlaylistActivity.this, 
                        "Error creating playlist: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
    }
    
    private String getSelectedMood() {
        // First check if any chip is selected
        int selectedChipId = moodChipGroup.getCheckedChipId();
        if (selectedChipId != -1 && chipIdToMoodMap.containsKey(selectedChipId)) {
            return chipIdToMoodMap.get(selectedChipId);
        }
        
        // Fallback to spinner selection
        return spMood.getSelectedItemPosition() > 0 ? spMood.getSelectedItem().toString() : null;
    }
    
    private void setLoading(boolean isLoading) {
        // Update UI components based on loading state
        btnCreate.setEnabled(!isLoading);
        btnCancel.setEnabled(!isLoading);
        
        // Show appropriate progress indicator (could be implemented more fully)
        if (isLoading) {
            btnCreate.setText(R.string.creating);
        } else {
            btnCreate.setText(R.string.create);
        }
    }
} 