package my.edu.utar.bananamusic.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.utils.PlaylistManager;

public class CreatePlaylistActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    private EditText etPlaylistName;
    private EditText etDescription;
    private SwitchMaterial cbCollaborative;
    private Spinner etMood;
    private ImageView ivCoverImage;
    private Button btnCreate;
    private Button btnCreateBottom;
    private Button btnCancel;
    private ProgressBar progressBar;

    private PlaylistManager playlistManager;
    private Uri coverImageUri;
    private boolean isEditMode = false;
    private String playlistId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_playlist);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize views
        etPlaylistName = findViewById(R.id.et_playlist_name);
        etDescription = findViewById(R.id.et_playlist_description);
        cbCollaborative = findViewById(R.id.switch_collaborative);
        etMood = findViewById(R.id.sp_mood);
        ivCoverImage = findViewById(R.id.iv_cover_image);
        btnCreate = findViewById(R.id.btn_create);
        btnCreateBottom = findViewById(R.id.btn_create_bottom);
        btnCancel = findViewById(R.id.btn_cancel);
        progressBar = findViewById(R.id.progress_bar);

        // Initialize PlaylistManager
        playlistManager = PlaylistManager.getInstance(this);

        // Check if in edit mode
        if (getIntent() != null) {
            isEditMode = getIntent().getBooleanExtra("EDIT_MODE", false);
            playlistId = getIntent().getStringExtra("PLAYLIST_ID");

            if (isEditMode && playlistId != null) {
                setTitle("Edit Playlist");
                btnCreate.setText("Save Changes");
                btnCreateBottom.setText("Save Changes");
                loadPlaylistDetails(playlistId);
            } else {
                setTitle("Create Playlist");
            }
        }

        // Set up click listeners
        ivCoverImage.setOnClickListener(v -> openImagePicker());
        btnCreate.setOnClickListener(v -> savePlaylist());
        btnCreateBottom.setOnClickListener(v -> savePlaylist());
        btnCancel.setOnClickListener(v -> onBackPressed());

        // Set up mood spinner with predefined options
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.mood_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        etMood.setAdapter(adapter);
    }

    private void loadPlaylistDetails(String playlistId) {
        showProgressBar();

        playlistManager.getPlaylist(playlistId, new PlaylistManager.PlaylistCallback() {
            @Override
            public void onSuccess(Playlist playlist) {
                hideProgressBar();
                etPlaylistName.setText(playlist.getName());
                etDescription.setText(playlist.getDescription());
                cbCollaborative.setChecked(playlist.isCollaborative());
                
                // Set the spinner to the correct mood
                if (playlist.getMood() != null && !playlist.getMood().isEmpty()) {
                    ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) etMood.getAdapter();
                    for (int i = 0; i < adapter.getCount(); i++) {
                        if (adapter.getItem(i).toString().equalsIgnoreCase(playlist.getMood())) {
                            etMood.setSelection(i);
                            break;
                        }
                    }
                }

                if (playlist.getCoverImageUrl() != null && !playlist.getCoverImageUrl().isEmpty()) {
                    Glide.with(CreatePlaylistActivity.this)
                            .load(playlist.getCoverImageUrl())
                            .placeholder(R.drawable.default_playlist_cover)
                            .into(ivCoverImage);
                }
            }

            @Override
            public void onError(String errorMessage) {
                hideProgressBar();
                Toast.makeText(CreatePlaylistActivity.this, "Error loading playlist: " + errorMessage, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            coverImageUri = data.getData();
            Glide.with(this)
                    .load(coverImageUri)
                    .placeholder(R.drawable.default_playlist_cover)
                    .into(ivCoverImage);
        }
    }

    private void savePlaylist() {
        String name = etPlaylistName.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        boolean collaborative = cbCollaborative.isChecked();
        String mood = etMood.getSelectedItem().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etPlaylistName.setError("Please enter a playlist name");
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to create a playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressBar();

        if (isEditMode && playlistId != null) {
            // Update existing playlist
            playlistManager.updatePlaylist(
                    playlistId,
                    name,
                    description,
                    collaborative,
                    mood,
                    coverImageUri,
                    new PlaylistManager.PlaylistCallback() {
                        @Override
                        public void onSuccess(Playlist playlist) {
                            hideProgressBar();
                            Toast.makeText(CreatePlaylistActivity.this, "Playlist updated", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            hideProgressBar();
                            Toast.makeText(CreatePlaylistActivity.this, "Error updating playlist: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        } else {
            // Create new playlist
            playlistManager.createPlaylist(
                    name,
                    description,
                    collaborative,
                    mood,
                    coverImageUri,
                    new PlaylistManager.PlaylistCallback() {
                        @Override
                        public void onSuccess(Playlist playlist) {
                            hideProgressBar();
                            Toast.makeText(CreatePlaylistActivity.this, "Playlist created", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            hideProgressBar();
                            Toast.makeText(CreatePlaylistActivity.this, "Error creating playlist: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        }
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        btnCreate.setEnabled(false);
        btnCreateBottom.setEnabled(false);
        btnCancel.setEnabled(false);
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.GONE);
        btnCreate.setEnabled(true);
        btnCreateBottom.setEnabled(true);
        btnCancel.setEnabled(true);
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