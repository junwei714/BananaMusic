package my.edu.utar.bananamusic.ui.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.PlaylistTrackAdapter;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.models.TrackHistory;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.ImageLoadHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;

public class PlaylistDetailFragment extends Fragment {
    private static final String TAG = "PlaylistDetailFragment";

    private ImageView ivPlaylistCover;
    private TextView tvPlaylistName;
    private TextView tvPlaylistInfo;
    private MaterialButton btnPlayAll;
    private RecyclerView rvTracks;
    private PlaylistTrackAdapter trackAdapter;
    private ImageButton btnClose;

    private PlaylistManager playlistManager;
    private AudioPlayerHelper audioPlayerHelper;
    private String playlistId;
    private Playlist currentPlaylist;
    private boolean isPlaying = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistManager = PlaylistManager.getInstance(requireContext());
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        
        // Initialize TrackHistory
        TrackHistory.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_detail, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        ivPlaylistCover = view.findViewById(R.id.ivPlaylistCover);
        tvPlaylistName = view.findViewById(R.id.tvPlaylistName);
        tvPlaylistInfo = view.findViewById(R.id.tvPlaylistInfo);
        btnPlayAll = view.findViewById(R.id.btnPlayAll);
        rvTracks = view.findViewById(R.id.rvTracks);
        btnClose = view.findViewById(R.id.btnClose);

        // Set up close button click listener
        btnClose.setOnClickListener(v -> {
            // Navigate back to the previous screen
            Navigation.findNavController(requireView()).navigateUp();
        });

        // Set up RecyclerView
        rvTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        trackAdapter = new PlaylistTrackAdapter(requireContext(), new PlaylistTrackAdapter.OnTrackClickListener() {
            @Override
            public void onTrackClick(Track track, int position) {
                playTrackFromPlaylist(track, position);
            }

            @Override
            public void onPlayButtonClick(Track track, int position) {
                playTrackFromPlaylist(track, position);
            }
        });
        rvTracks.setAdapter(trackAdapter);

        // Get playlist ID from arguments
        if (getArguments() != null) {
            playlistId = getArguments().getString("playlistId");
            String playlistName = getArguments().getString("playlistName");
            String coverUrl = getArguments().getString("playlistCoverUrl");

            // Set initial data
            if (playlistName != null) {
                tvPlaylistName.setText(playlistName);
            }
            if (coverUrl != null) {
                ImageLoadHelper.loadRoundedImage(requireContext(), coverUrl, ivPlaylistCover, 
                    R.drawable.default_playlist_cover, 8);
            }

            // Load playlist details and tracks
            loadPlaylistDetails();
        }

        // Set up play all button
        btnPlayAll.setOnClickListener(v -> {
            if (currentPlaylist != null && currentPlaylist.getTracks() != null && !currentPlaylist.getTracks().isEmpty()) {
                if (!isPlaying) {
                    startPlaylist();
                } else {
                    pausePlaylist();
                }
            }
        });

        // Set up completion listener
        audioPlayerHelper.setOnPlaybackChangedListener(new AudioPlayerHelper.OnPlaybackChangedListener() {
            @Override
            public void onTrackPlay(Track track) {
                isPlaying = true;
                trackAdapter.setLoading(false);
                btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_pause, requireContext().getTheme()));
                btnPlayAll.setText(R.string.pause_all);
            }

            @Override
            public void onTrackPause() {
                isPlaying = false;
                trackAdapter.setLoading(false);
                btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_play, requireContext().getTheme()));
                btnPlayAll.setText(R.string.play_all);
            }

            @Override
            public void onTrackStopped() {
                isPlaying = false;
                trackAdapter.setLoading(false);
                btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_play, requireContext().getTheme()));
                btnPlayAll.setText(R.string.play_all);
                trackAdapter.setPlayingTrackPosition(-1);
            }

            @Override
            public void onTrackChanged(Track track) {
                // Update UI for track change if needed
            }

            @Override
            public void onPlaybackError(String errorMessage) {
                if (!isAdded()) return;
                trackAdapter.setLoading(false);
                Toast.makeText(requireContext(), "Playback error: " + errorMessage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBufferingStart() {
                trackAdapter.setLoading(true);
            }

            @Override
            public void onBufferingEnd() {
                trackAdapter.setLoading(false);
            }

            @Override
            public void onTrackComplete() {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        // Update UI for track completion
                        updatePlaybackControls(false);
                    });
                }
            }

            @Override
            public void onLoadingStateChanged(boolean isLoading) {
                trackAdapter.setLoading(isLoading);
            }

            @Override
            public void onBufferingUpdate(int percent) {
                // Handle buffering update if needed
            }

            @Override
            public void onPlaybackStateChanged(boolean isPlaying) {
                PlaylistDetailFragment.this.isPlaying = isPlaying;
                trackAdapter.setLoading(false);
                btnPlayAll.setIcon(getResources().getDrawable(
                    isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, 
                    requireContext().getTheme()));
                btnPlayAll.setText(isPlaying ? R.string.pause_all : R.string.play_all);
            }
        });
    }

    private void startPlaylist() {
        if (currentPlaylist != null && currentPlaylist.getTracks() != null && !currentPlaylist.getTracks().isEmpty()) {
            isPlaying = true;
            btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_pause, requireContext().getTheme()));
            btnPlayAll.setText(R.string.pause_all);
            
            // Start playing from the first track
            Track firstTrack = currentPlaylist.getTracks().get(0);
            playTrackFromPlaylist(firstTrack, 0);
            
            Toast.makeText(requireContext(), 
                "Playing playlist: " + currentPlaylist.getName(), 
                Toast.LENGTH_SHORT).show();
        }
    }

    private void pausePlaylist() {
        isPlaying = false;
        btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_play, requireContext().getTheme()));
        btnPlayAll.setText(R.string.play_all);
        audioPlayerHelper.pausePlayback();
    }

    private void playTrackFromPlaylist(Track track, int position) {
        if (currentPlaylist != null) {
            trackAdapter.setLoading(true);
            audioPlayerHelper.playTrackInPlaylist(currentPlaylist, track);
            isPlaying = true;
            btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_pause, requireContext().getTheme()));
            btnPlayAll.setText(R.string.pause_all);
            trackAdapter.setPlayingTrackPosition(position);
        }
    }

    private void playNextTrack() {
        if (currentPlaylist != null && currentPlaylist.getTracks() != null) {
            int currentPosition = trackAdapter.getPlayingTrackPosition();
            int nextPosition = currentPosition + 1;
            
            if (nextPosition < currentPlaylist.getTracks().size()) {
                // Play next track
                Track nextTrack = currentPlaylist.getTracks().get(nextPosition);
                playTrackFromPlaylist(nextTrack, nextPosition);
            } else {
                // End of playlist reached
                isPlaying = false;
                btnPlayAll.setIcon(getResources().getDrawable(R.drawable.ic_play, requireContext().getTheme()));
                btnPlayAll.setText(R.string.play_all);
                trackAdapter.setPlayingTrackPosition(-1);
            }
        }
    }

    private void loadPlaylistDetails() {
        if (playlistId != null) {
            playlistManager.getPlaylistTracks(playlistId, new TracksCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (!isAdded()) return;
                    
                    // Get the first track's album art for the playlist cover if no cover is set
                    if (tracks != null && !tracks.isEmpty()) {
                        Track firstTrack = tracks.get(0);
                        String coverUrl = firstTrack.getAlbumArtUrl();
                        if (coverUrl != null && !coverUrl.isEmpty()) {
                            ImageLoadHelper.loadRoundedImage(requireContext(), coverUrl, ivPlaylistCover, 
                                R.drawable.default_playlist_cover, 8);
                        }
                    }
                    
                    // Update track list
                    trackAdapter.updateTracks(tracks);
                    
                    // Update playlist info
                    String info = tracks.size() + " tracks";
                    tvPlaylistInfo.setText(info);
                    
                    // Enable/disable play all button
                    btnPlayAll.setEnabled(!tracks.isEmpty());
                }

                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Error loading tracks: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updatePlaybackControls(boolean isPlaying) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                // Update play/pause button state
                if (btnPlayAll != null) {
                    btnPlayAll.setIconResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                }
                
                // Update adapter if needed
                if (trackAdapter != null) {
                    trackAdapter.setPlayingState(isPlaying);
                }
                
                // Update local state
                this.isPlaying = isPlaying;
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up AudioPlayerHelper listener
        if (audioPlayerHelper != null) {
            audioPlayerHelper.setOnPlaybackChangedListener(null);
        }
    }
} 