package my.edu.utar.bananamusic.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.AutoCompleteTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import my.edu.utar.bananamusic.MainActivity;
import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.QueueAdapter;
import my.edu.utar.bananamusic.models.Playlist;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.PlaylistManager;
import my.edu.utar.bananamusic.ui.base.SafeBottomSheetDialogFragment;
import my.edu.utar.bananamusic.utils.SafeRecyclerViewLayoutManager;
import my.edu.utar.bananamusic.ui.dialogs.AddToPlaylistDialog;

/**
 * Fragment that displays the current queue or play history
 */
public class QueueFragment extends SafeBottomSheetDialogFragment implements QueueAdapter.QueueAdapterListener {
    
    private static final String TAG = "QueueFragment";
    
    private RecyclerView rvQueue;
    private TextView tvQueueCount;
    private TextView tvEmptyQueue;
    private Button btnClearQueue;
    private Button btnSaveAsPlaylist;
    private TabLayout tabLayout;
    private FloatingActionButton fabAddToQueue;
    
    private QueueAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private AudioPlayerHelper audioPlayerHelper;
    private PlaylistManager playlistManager;
    
    private boolean isHistoryMode = false;
    
    private static final int TAB_QUEUE = 0;
    private static final int TAB_HISTORY = 1;

    public static QueueFragment newInstance() {
        return new QueueFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize helpers
        Context context = requireContext();
        audioPlayerHelper = AudioPlayerHelper.getInstance(context);
        playlistManager = PlaylistManager.getInstance(context);
        
        // Find views
        rvQueue = view.findViewById(R.id.rvQueue);
        tvQueueCount = view.findViewById(R.id.tvQueueCount);
        tvEmptyQueue = view.findViewById(R.id.tvEmptyQueue);
        btnClearQueue = view.findViewById(R.id.btnClearQueue);
        btnSaveAsPlaylist = view.findViewById(R.id.btnSaveAsPlaylist);
        tabLayout = view.findViewById(R.id.tabLayout);
        fabAddToQueue = view.findViewById(R.id.fabAddToQueue);
        
        // Setup UI
        setupRecyclerView();
        setupTabLayout();
        setupButtons();
        
        // Load initial data
        updateQueueData();
    }

    private void setupRecyclerView() {
        // Set up ItemTouchHelper for drag & drop and swipe to dismiss
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, 
                                 @NonNull RecyclerView.ViewHolder viewHolder, 
                                 @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                if (adapter != null) {
                    adapter.onItemMove(fromPosition, toPosition);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (adapter != null) {
                    adapter.onItemDismiss(position);
                }
            }
            
            @Override
            public boolean isLongPressDragEnabled() {
                // Disable long press drag (we use the handle instead)
                return false;
            }
            
            @Override
            public boolean isItemViewSwipeEnabled() {
                // Disable swipe in history mode
                return !isHistoryMode;
            }
        };
        
        itemTouchHelper = new ItemTouchHelper(callback);
        
        // Apply safe layout manager from parent class
        useSafeLayoutManager(rvQueue);
        
        // Disable state saving to prevent crashes
        rvQueue.setSaveEnabled(false);
        rvQueue.setItemAnimator(null);
        
        // Create adapter with empty list (will be updated later)
        adapter = new QueueAdapter(requireContext(), new ArrayList<>(), this, itemTouchHelper);
        
        // Attach the adapter and item touch helper
        rvQueue.setAdapter(adapter);
        itemTouchHelper.attachToRecyclerView(rvQueue);
    }
    
    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == TAB_QUEUE) {
                    isHistoryMode = false;
                    fabAddToQueue.setVisibility(View.VISIBLE);
                    btnClearQueue.setVisibility(View.VISIBLE);
                    btnSaveAsPlaylist.setVisibility(View.VISIBLE);
                } else {
                    isHistoryMode = true;
                    fabAddToQueue.setVisibility(View.GONE);
                    btnClearQueue.setVisibility(View.GONE);
                    btnSaveAsPlaylist.setVisibility(View.GONE);
                }
                
                adapter.setHistoryMode(isHistoryMode);
                updateQueueData();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not needed
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not needed
            }
        });
    }
    
    private void setupButtons() {
        // Clear queue button
        btnClearQueue.setOnClickListener(v -> {
            if (audioPlayerHelper != null) {
                // Show confirmation dialog
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear Queue")
                        .setMessage("Are you sure you want to clear the current queue?")
                        .setPositiveButton("Clear", (dialog, which) -> {
                            // Clear the queue and update UI
                            audioPlayerHelper.clearQueue();
                            updateQueueData();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        
        // Save as playlist button
        btnSaveAsPlaylist.setOnClickListener(v -> {
            showSavePlaylistDialog();
        });
        
        // Add to queue button
        fabAddToQueue.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showTrackSelector(tracks -> {
                    // Add selected tracks to queue
                    audioPlayerHelper.addAllToQueue(tracks);
                    updateQueueData();
                });
            }
        });
    }
    
    private void updateQueueData() {
        List<Track> tracks;
        
        if (isHistoryMode) {
            // Show history
            tracks = audioPlayerHelper.getHistory();
            adapter.setCurrentPlayingPosition(-1); // No currently playing track in history
        } else {
            // Show queue
            tracks = audioPlayerHelper.getQueue();
            adapter.setCurrentPlayingPosition(audioPlayerHelper.getCurrentQueueIndex());
        }
        
        // Update adapter data
        adapter.updateData(tracks);
        
        // Update empty state visibility
        if (tracks.isEmpty()) {
            tvEmptyQueue.setVisibility(View.VISIBLE);
            tvEmptyQueue.setText(isHistoryMode ? "No play history" : "Your queue is empty");
            rvQueue.setVisibility(View.GONE);
        } else {
            tvEmptyQueue.setVisibility(View.GONE);
            rvQueue.setVisibility(View.VISIBLE);
        }
        
        // Update queue count
        updateQueueCount();
    }
    
    private void updateQueueCount() {
        if (isHistoryMode) {
            List<Track> history = audioPlayerHelper.getHistory();
            tvQueueCount.setText(history.size() + " tracks in history");
        } else {
            List<Track> queue = audioPlayerHelper.getQueue();
            tvQueueCount.setText(queue.size() + " tracks in queue");
        }
    }
    
    private void showTrackOptionsMenu(Track track, int position, View view) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.menu_queue_track_options, popup.getMenu());
        
        // Show/hide menu items based on mode
        popup.getMenu().findItem(R.id.action_remove_from_queue).setVisible(!isHistoryMode);
        popup.getMenu().findItem(R.id.action_play_next).setVisible(isHistoryMode);
        popup.getMenu().findItem(R.id.action_add_to_queue).setVisible(isHistoryMode);
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            
            if (id == R.id.action_remove_from_queue) {
                audioPlayerHelper.removeFromQueue(position);
                updateQueueData();
                return true;
            } else if (id == R.id.action_play_next) {
                audioPlayerHelper.addToQueueNext(track);
                Toast.makeText(requireContext(), "Added to play next", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_add_to_queue) {
                audioPlayerHelper.addToQueue(track);
                Toast.makeText(requireContext(), "Added to queue", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.action_add_to_playlist) {
                AddToPlaylistDialog dialog = AddToPlaylistDialog.newInstance(track);
                dialog.show(getChildFragmentManager(), "add_to_playlist");
                return true;
            }
            
            return false;
        });
        
        popup.show();
    }
    
    private void showSavePlaylistDialog() {
        // Create dialog with name/description inputs
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_playlist, null);
        
        TextInputEditText etPlaylistName = dialogView.findViewById(R.id.etPlaylistName);
        TextInputEditText etPlaylistDescription = dialogView.findViewById(R.id.etPlaylistDescription);
        AutoCompleteTextView spinnerMood = dialogView.findViewById(R.id.spinnerMood);
        
        // Pre-populate with default name
        etPlaylistName.setText("My Queue Playlist");
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save Queue as Playlist")
                .setView(dialogView)
                .setPositiveButton("Save", null) // Set up below to prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();
        
        dialog.show();
        
        // Override click listener to validate before dismissing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etPlaylistName.getText().toString().trim();
            String description = etPlaylistDescription.getText().toString().trim();
            
            if (name.isEmpty()) {
                etPlaylistName.setError("Please enter a playlist name");
                return;
            }
            
            // Create playlist from queue
            Playlist playlist = audioPlayerHelper.createPlaylistFromQueue(name, description);
            
            if (playlist == null) {
                Toast.makeText(requireContext(), "Cannot create playlist from empty queue", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Save playlist to database
            playlistManager.createPlaylist(
                playlist.getName(),
                playlist.getDescription(),
                false,  // Not collaborative
                null,   // No specific mood
                null,   // No cover image URI
                new PlaylistManager.PlaylistCallback() {
                    @Override
                    public void onSuccess(Playlist createdPlaylist) {
                        Toast.makeText(requireContext(), "Playlist created", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(requireContext(), "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            );
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when returning to the fragment
        updateQueueData();
    }

    @Override
    public void onItemClick(Track track, int position) {
        // Skip to this track in queue
        if (!isHistoryMode) {
            audioPlayerHelper.skipToQueueIndex(position);
        } else {
            // In history mode, add to queue and play
            audioPlayerHelper.addToQueueNext(track);
            audioPlayerHelper.skipToQueueIndex(0);
        }
        
        // Set current playing position in adapter
        adapter.setCurrentPlayingPosition(audioPlayerHelper.getCurrentQueueIndex());
    }

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        // Update queue in AudioPlayerHelper
        if (!isHistoryMode) {
            audioPlayerHelper.moveQueueItem(fromPosition, toPosition);
        }
    }

    @Override
    public void onItemRemoved(int position) {
        // Remove from queue in AudioPlayerHelper
        if (!isHistoryMode) {
            audioPlayerHelper.removeFromQueue(position);
            updateQueueCount();
        }
    }

    @Override
    public void onOptionsClick(Track track, int position, View view) {
        showTrackOptionsMenu(track, position, view);
    }

    @Override
    public void onDestroyView() {
        try {
            // Clean up RecyclerView to prevent memory leaks
            if (rvQueue != null) {
                rvQueue.setAdapter(null);
                itemTouchHelper.attachToRecyclerView(null);
            }
        } catch (Exception e) {
            Log.e("QueueFragment", "Error cleaning up RecyclerView: " + e.getMessage());
        }
        
        super.onDestroyView();
    }
} 