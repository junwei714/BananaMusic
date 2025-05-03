package my.edu.utar.bananamusic.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import my.edu.utar.bananamusic.R;

public class CreatePlaylistDialogFragment extends DialogFragment {
    private static final String TAG = "CreatePlaylistDialog";

    private TextInputEditText etPlaylistName;
    private TextInputEditText etPlaylistDescription;
    private AutoCompleteTextView spinnerMood;
    private SwitchMaterial switchCollaborative;
    private MaterialButton btnCancel;
    private MaterialButton btnCreate;
    private Dialog.OnDismissListener onDismissListener;

    public interface PlaylistCreationListener {
        void onPlaylistCreated();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FullScreenDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_create_playlist, container, false);

        // Initialize views
        etPlaylistName = view.findViewById(R.id.etPlaylistName);
        etPlaylistDescription = view.findViewById(R.id.etPlaylistDescription);
        spinnerMood = view.findViewById(R.id.spinnerMood);
        switchCollaborative = view.findViewById(R.id.switchCollaborative);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnCreate = view.findViewById(R.id.btnCreate);

        // Setup mood spinner
        String[] moods = {"Happy", "Sad", "Chill", "Party", "Focus", "Romantic"};
        ArrayAdapter<String> moodAdapter = new ArrayAdapter<>(
            requireContext(),
            R.layout.item_dropdown,
            moods
        );
        spinnerMood.setAdapter(moodAdapter);

        // Handle button clicks
        btnCancel.setOnClickListener(v -> dismiss());

        btnCreate.setOnClickListener(v -> createPlaylist());

        return view;
    }

    private void createPlaylist() {
        String name = etPlaylistName.getText().toString().trim();
        String description = etPlaylistDescription.getText().toString().trim();
        String mood = spinnerMood.getText().toString();
        boolean isCollaborative = switchCollaborative.isChecked();

        if (name.isEmpty()) {
            etPlaylistName.setError("Please enter a playlist name");
            return;
        }

        // Get current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please sign in to create a playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create playlist data
        String playlistId = UUID.randomUUID().toString();
        Map<String, Object> playlistData = new HashMap<>();
        playlistData.put("playlistId", playlistId);
        playlistData.put("name", name);
        playlistData.put("description", description);
        playlistData.put("mood", mood.isEmpty() ? null : mood);
        playlistData.put("creatorId", currentUser.getUid());
        playlistData.put("creatorName", currentUser.getDisplayName());
        playlistData.put("isCollaborative", isCollaborative);
        playlistData.put("createdAt", FieldValue.serverTimestamp());
        playlistData.put("coverImageUrl", ""); // Will be updated when first track is added
        playlistData.put("trackData", new HashMap<>());
        playlistData.put("trackIds", new ArrayList<>());
        playlistData.put("collected", false);

        // Initialize collaborators array with creator
        List<String> collaborators = new ArrayList<>();
        collaborators.add(currentUser.getUid());
        playlistData.put("collaborators", collaborators);

        // Save to Firebase
        FirebaseFirestore.getInstance()
            .collection("playlists")
            .document(playlistId)
            .set(playlistData)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(requireContext(), "Playlist created successfully", Toast.LENGTH_SHORT).show();
                
                // Notify listener
                if (getActivity() instanceof PlaylistCreationListener) {
                    ((PlaylistCreationListener) getActivity()).onPlaylistCreated();
                }
                
                // Notify dismiss listener
                if (onDismissListener != null) {
                    onDismissListener.onDismiss(getDialog());
                }
                
                dismiss();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error creating playlist", e);
                Toast.makeText(requireContext(), "Failed to create playlist: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    public void setOnDismissListener(Dialog.OnDismissListener listener) {
        this.onDismissListener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }
        }
    }
} 