package my.edu.utar.bananamusic.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.DialogPlaylistAdapter;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.ui.playlist.CreatePlaylistActivity;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistCallback;
import my.edu.utar.bananamusic.utils.callbacks.PlaylistsCallback;

public class AddToPlaylistDialog extends DialogFragment {
    private static final String ARG_TRACK = "track";
    private Track track;
    private PlaylistManager playlistManager;
    private DialogPlaylistAdapter adapter;
    private CircularProgressIndicator progressBar;
    private View tvEmptyState;
    private RecyclerView rvPlaylists;

    public static AddToPlaylistDialog newInstance(Track track) {
        AddToPlaylistDialog fragment = new AddToPlaylistDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TRACK, track);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            track = getArguments().getParcelable(ARG_TRACK);
        }
        playlistManager = PlaylistManager.getInstance(requireContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext(), R.style.Theme_BananaMusic_Dialog);
        dialog.setContentView(R.layout.dialog_add_to_playlist);
        
        // Remove any window padding
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        }

        // Initialize views
        rvPlaylists = dialog.findViewById(R.id.rvPlaylists);
        MaterialButton btnCreatePlaylist = dialog.findViewById(R.id.btnCreatePlaylist);
        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);
        progressBar = dialog.findViewById(R.id.progressBar);
        tvEmptyState = dialog.findViewById(R.id.tvEmptyState);

        // Set up RecyclerView
        rvPlaylists.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DialogPlaylistAdapter(getContext(), playlist -> {
            // Add track to selected playlist
            addTrackToPlaylist(playlist);
        });
        rvPlaylists.setAdapter(adapter);

        // Show loading state
        showLoading();

        // Load playlists
        loadPlaylists();

        // Set up button click listeners
        btnCreatePlaylist.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), CreatePlaylistActivity.class);
            startActivity(intent);
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());

        return dialog;
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        rvPlaylists.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        progressBar.setVisibility(View.GONE);
        rvPlaylists.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.VISIBLE);
    }

    private void showContent() {
        progressBar.setVisibility(View.GONE);
        rvPlaylists.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
    }

    private void loadPlaylists() {
        playlistManager.getUserPlaylists(new PlaylistsCallback() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (playlists == null || playlists.isEmpty()) {
                    showEmptyState();
                } else {
                    adapter.updatePlaylists(playlists);
                    showContent();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(getContext(), 
                    "Error loading playlists: " + message, 
                    Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });
    }

    private void addTrackToPlaylist(Playlist playlist) {
        // Show loading state while adding track
        showLoading();
        
        playlistManager.saveTrackAndAddToPlaylist(playlist.getPlaylistId(), track, new PlaylistCallback() {
            @Override
            public void onSuccess(Playlist updatedPlaylist) {
                Toast.makeText(getContext(), 
                    "Added to " + playlist.getName(), 
                    Toast.LENGTH_SHORT).show();
                dismiss();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(getContext(), 
                    "Error adding track: " + message, 
                    Toast.LENGTH_SHORT).show();
                showContent(); // Show content again if error occurs
            }
        });
    }
} 