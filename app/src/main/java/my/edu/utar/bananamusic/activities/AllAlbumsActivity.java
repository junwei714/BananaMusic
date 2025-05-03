package my.edu.utar.bananamusic.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import my.edu.utar.bananamusic.R;
import my.edu.utar.bananamusic.adapters.AlbumAdapter;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.ApiDataProvider;
import my.edu.utar.bananamusic.utils.callbacks.TracksCallback;

public class AllAlbumsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AlbumAdapter adapter;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean isLoading = false;
    private int currentPage = 0;
    private static final int PAGE_SIZE = 20;
    private boolean hasMoreData = true;
    private static final String TAG = "AllAlbumsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_albums);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Featured Albums");
        }

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Set up RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new AlbumAdapter(this, new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // Set up pull to refresh
        swipeRefreshLayout.setOnRefreshListener(this::refreshAlbums);

        // Set up pagination
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                if (!isLoading && hasMoreData) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                        loadMoreAlbums();
                    }
                }
            }
        });

        // Load initial data
        loadAlbums();
    }

    private void loadAlbums() {
        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);

        loadFeaturedAlbums();
    }

    private void loadFeaturedAlbums() {
        ApiDataProvider.getInstance(this).getFeaturedAlbums(new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                progressBar.setVisibility(View.GONE);
                if (tracks != null && !tracks.isEmpty()) {
                    updateAlbumsList(tracks);
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error loading featured albums: " + message);
                Toast.makeText(AllAlbumsActivity.this, "Failed to load albums: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMoreAlbums() {
        if (isLoading) return;
        
        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);

        ApiDataProvider.getInstance(this).getFeaturedAlbums(new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                progressBar.setVisibility(View.GONE);
                if (tracks != null && !tracks.isEmpty()) {
                    appendToAlbumsList(tracks);
                } else {
                    hasMoreData = false;
                }
                isLoading = false;
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error loading more albums: " + message);
                Toast.makeText(AllAlbumsActivity.this, "Failed to load more albums: " + message, Toast.LENGTH_SHORT).show();
                isLoading = false;
            }
        });
    }

    private void refreshAlbums() {
        currentPage = 0;
        hasMoreData = true;
        
        ApiDataProvider.getInstance(this).getFeaturedAlbums(new TracksCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                swipeRefreshLayout.setRefreshing(false);
                if (tracks != null && !tracks.isEmpty()) {
                    updateAlbumsList(tracks);
                }
            }

            @Override
            public void onError(String message) {
                swipeRefreshLayout.setRefreshing(false);
                Log.e(TAG, "Error refreshing albums: " + message);
                Toast.makeText(AllAlbumsActivity.this, "Failed to refresh albums: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateAlbumsList(List<Track> tracks) {
        adapter.updateData(tracks);
        isLoading = false;
        hasMoreData = tracks.size() >= PAGE_SIZE;
        currentPage = 1;
    }

    private void appendToAlbumsList(List<Track> tracks) {
        adapter.addData(tracks);
        currentPage++;
        hasMoreData = tracks.size() >= PAGE_SIZE;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 