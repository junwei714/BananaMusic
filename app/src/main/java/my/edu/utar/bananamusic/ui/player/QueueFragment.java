package my.edu.utar.bananamusic.ui.player;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.QueueAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.NoSaveStateLinearLayoutManager;

public class QueueFragment extends DialogFragment {
    private RecyclerView recyclerView;
    private QueueAdapter adapter;
    private LinearLayout emptyQueueContainer;
    private TextView emptyQueueText;
    private AudioPlayerHelper audioPlayerHelper;
    private ItemTouchHelper touchHelper;
    private boolean isCleanedUp = false;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    
    // New UI elements
    private ImageButton backButton;
    private Button addToQueueButton;
    private ImageView currentTrackArt;
    private TextView currentTrackTitle;
    private TextView currentTrackArtist;
    private ImageView currentTrackOptions;
    
    // Preload and cache reference to avoid repeated lookups
    private List<Track> queueTracks = new ArrayList<>();

    public static QueueFragment newInstance() {
        return new QueueFragment();
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialog);
        
        // Initialize AudioPlayerHelper early
        audioPlayerHelper = AudioPlayerHelper.getInstance(requireContext());
        
        // Preload queue data
        backgroundExecutor.execute(() -> {
            queueTracks = audioPlayerHelper.getQueue();
        });
    }
    
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.getAttributes().windowAnimations = R.style.FullScreenDialog;
                window.setWindowAnimations(R.style.FullScreenDialog);
                
                // Disable state saving
                window.getDecorView().setSaveEnabled(false);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        
        // Performance improvements for the dialog window
        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().windowAnimations = R.style.FullScreenDialog;
            window.setWindowAnimations(R.style.FullScreenDialog);
            
            // Disable state saving
            window.getDecorView().setSaveEnabled(false);
        }
        
        // Prevent recreate on orientation change
        if (savedInstanceState == null) {
            // Only set this flag when first creating, not on recreation
            dialog.setCanceledOnTouchOutside(true);
        }
        
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_queue, container, false);
        
        initializeViews(rootView);
        setupListeners();
        
        // Optimize RecyclerView for performance
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        
        // Disable state saving
        rootView.setSaveEnabled(false);
        if (container != null) {
            container.setSaveEnabled(false);
        }
        
        setupRecyclerView();
        updateCurrentTrackUI();
        
        return rootView;
    }
    
    private void initializeViews(View rootView) {
        recyclerView = rootView.findViewById(R.id.queue_recycler_view);
        emptyQueueContainer = rootView.findViewById(R.id.empty_queue_container);
        emptyQueueText = rootView.findViewById(R.id.empty_queue_text);
        
        // Initialize new UI elements
        backButton = rootView.findViewById(R.id.btn_back);
        addToQueueButton = rootView.findViewById(R.id.btn_add_to_queue);
        currentTrackArt = rootView.findViewById(R.id.current_track_art);
        currentTrackTitle = rootView.findViewById(R.id.current_track_title);
        currentTrackArtist = rootView.findViewById(R.id.current_track_artist);
        currentTrackOptions = rootView.findViewById(R.id.current_track_options);
    }
    
    private void setupListeners() {
        backButton.setOnClickListener(v -> dismiss());
        
        addToQueueButton.setOnClickListener(v -> {
            // Add to queue functionality
            Toast.makeText(requireContext(), "Add to queue feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        currentTrackOptions.setOnClickListener(v -> {
            // Show options for current track
            Track currentTrack = audioPlayerHelper.getCurrentTrack();
            if (currentTrack != null) {
                showTrackOptions(currentTrack);
            }
        });
    }
    
    private void showTrackOptions(Track track) {
        // Show a bottom sheet or popup with track options
        Toast.makeText(requireContext(), "Options for: " + track.getTitle(), Toast.LENGTH_SHORT).show();
    }
    
    private void updateCurrentTrackUI() {
        Track currentTrack = audioPlayerHelper.getCurrentTrack();
        if (currentTrack != null) {
            currentTrackTitle.setText(currentTrack.getTitle());
            currentTrackArtist.setText(currentTrack.getArtist());
            
            // Load album art with rounded corners
            if (currentTrack.getAlbumArt() != null && !currentTrack.getAlbumArt().isEmpty()) {
                Glide.with(requireContext())
                     .load(currentTrack.getAlbumArt())
                     .transform(new RoundedCorners(8))
                     .placeholder(R.drawable.ic_album_placeholder)
                     .error(R.drawable.ic_album_placeholder)
                     .into(currentTrackArt);
            } else {
                currentTrackArt.setImageResource(R.drawable.ic_album_placeholder);
            }
        }
    }
    
    private void setupRecyclerView() {
        // Create custom layout manager with optimizations
        NoSaveStateLinearLayoutManager layoutManager = new NoSaveStateLinearLayoutManager(requireContext());
        layoutManager.setItemPrefetchEnabled(true);
        layoutManager.setInitialPrefetchItemCount(15); // Prefetch more items
        recyclerView.setLayoutManager(layoutManager);
        
        // Create ItemTouchHelper for drag and drop
        touchHelper = new ItemTouchHelper(createItemTouchCallback());
        
        // Use cached tracks if available, otherwise load them
        final List<Track> tracks = queueTracks != null && !queueTracks.isEmpty() 
                ? queueTracks 
                : audioPlayerHelper.getQueue();
        
        // Setup adapter with the touch helper
        QueueAdapter.QueueAdapterListener listener = new QueueAdapter.QueueAdapterListener() {
            @Override
            public void onItemClick(Track track, int position) {
                if (position >= 0 && position < tracks.size()) {
                    // Play the selected track from queue
                    audioPlayerHelper.skipToQueueIndex(position);
                    updateCurrentTrackUI();
                }
            }
            
            @Override
            public void onItemMove(int fromPosition, int toPosition) {
                if (fromPosition >= 0 && fromPosition < tracks.size() && 
                    toPosition >= 0 && toPosition < tracks.size()) {
                    // Move item in the queue
                    audioPlayerHelper.moveQueueItem(fromPosition, toPosition);
                }
            }
            
            @Override
            public void onItemRemoved(int position) {
                if (position >= 0 && position < tracks.size()) {
                    // Remove from queue
                    audioPlayerHelper.removeFromQueue(position);
                    
                    // Update empty view
                    updateEmptyView(audioPlayerHelper.getQueue());
                }
            }
            
            @Override
            public void onOptionsClick(Track track, int position, View view) {
                // Show options menu if needed
                showTrackOptions(track);
            }
        };
        
        // Create adapter with efficient change handling
        adapter = new QueueAdapter(requireContext(), tracks, listener, touchHelper);
        
        // Set current playing position
        int currentIndex = audioPlayerHelper.getCurrentQueueIndex();
        if (currentIndex >= 0 && currentIndex < tracks.size()) {
            adapter.setCurrentPlayingPosition(currentIndex);
        }
        
        recyclerView.setAdapter(adapter);
        
        // Attach the touch helper after setting the adapter
        touchHelper.attachToRecyclerView(recyclerView);
        
        // Show/hide empty view
        updateEmptyView(tracks);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Disable state saving
        view.setSaveEnabled(false);
    }
    
    @Override
    public void onDestroyView() {
        cleanup();
        super.onDestroyView();
    }
    
    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }
    
    @Override
    public void onPause() {
        cleanup();
        super.onPause();
    }
    
    @Override
    public void onStop() {
        cleanup();
        super.onStop();
    }
    
    private void cleanup() {
        if (isCleanedUp) return;
        isCleanedUp = true;
        
        // Detach recycler view before destroying view to prevent NullPointerException
        if (recyclerView != null) {
            try {
                recyclerView.setItemAnimator(null); // Remove animations during cleanup
                recyclerView.setAdapter(null);
                
                if (touchHelper != null) {
                    touchHelper.attachToRecyclerView(null);
                }
                
                recyclerView.setLayoutManager(null);
                recyclerView.clearOnScrollListeners();
                recyclerView.clearOnChildAttachStateChangeListeners();
            } catch (Exception e) {
                // Ignore any exceptions during cleanup
            }
        }
        
        touchHelper = null;
        adapter = null;
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // Don't call super to avoid saving RecyclerView state
        // This prevents NPE in RecyclerView.LayoutManager.getPosition()
    }
    
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        // Don't restore state
        // Only call super without the savedInstanceState
        super.onViewStateRestored(null);
    }
    
    private ItemTouchHelper.Callback createItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, 
                                 @NonNull RecyclerView.ViewHolder viewHolder, 
                                 @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false;
                }
                
                // Move handled by adapter
                adapter.onItemMove(fromPos, toPos);
                return true;
            }
            
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                
                // Remove handled by adapter
                adapter.onItemDismiss(position);
            }
            
            // Disable animations to improve performance
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
            
            @Override
            public boolean isItemViewSwipeEnabled() {
                return true;
            }
        };
    }
    
    private void updateEmptyView(List<Track> tracks) {
        if (emptyQueueContainer == null || recyclerView == null) return;
        
        if (tracks == null || tracks.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyQueueContainer.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyQueueContainer.setVisibility(View.GONE);
        }
    }
} 