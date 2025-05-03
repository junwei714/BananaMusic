package my.edu.utar.bananamusic;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import my.edu.utar.bananamusic.adapters.SearchResultAdapter;
import my.edu.utar.bananamusic.models.SearchResult;
import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.utils.AudioPlayerHelper;
import my.edu.utar.bananamusic.utils.SearchHelper;

public class SearchActivity extends AppCompatActivity implements SearchResultAdapter.OnSearchResultListener {

    private EditText editTextSearch;
    private RecyclerView recyclerViewResults;
    private ProgressBar progressBarSearch;
    private TextView textViewNoResults;
    private ImageButton buttonClearSearch;
    
    private SearchResultAdapter adapter;
    private SearchHelper searchHelper;
    private AudioPlayerHelper audioPlayerHelper;
    
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final PublishSubject<String> searchSubject = PublishSubject.create();
    private Disposable searchDisposable;
    
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        
        setupToolbar();
        initViews();
        setupSearchFeatures();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.search);
    }

    private void initViews() {
        editTextSearch = findViewById(R.id.edit_text_search);
        recyclerViewResults = findViewById(R.id.recycler_view_results);
        progressBarSearch = findViewById(R.id.progress_bar_search);
        textViewNoResults = findViewById(R.id.text_view_no_results);
        buttonClearSearch = findViewById(R.id.button_clear_search);
        
        // Setup RecyclerView
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultAdapter(this, this);
        recyclerViewResults.setAdapter(adapter);
        
        // Initialize helpers
        searchHelper = new SearchHelper(this);
        audioPlayerHelper = AudioPlayerHelper.getInstance(this);
        
        // Setup clear button
        buttonClearSearch.setOnClickListener(v -> {
            editTextSearch.setText("");
            hideResults();
        });
    }

    private void setupSearchFeatures() {
        // Setup search edit text
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                buttonClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                searchSubject.onNext(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        
        // Setup RxJava for search
        Disposable searchDisposable = searchSubject
                .debounce(300, TimeUnit.MILLISECONDS)
                .filter(query -> query.length() >= 2)
                .distinctUntilChanged()
                .switchMap(query -> 
                    Observable.create((ObservableOnSubscribe<List<SearchResult>>) emitter -> {
                        try {
                            showLoading();
                            // For now, create a dummy list of search results
                            List<SearchResult> results = getDummySearchResults(query);
                            emitter.onNext(results);
                            emitter.onComplete();
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    }).subscribeOn(Schedulers.io())
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleSearchResults,
                        error -> {
                            hideLoading();
                            Toast.makeText(this, getString(R.string.search_error) + error.getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                            showNoResults();
                        }
                );
        
        compositeDisposable.add(searchDisposable);
    }

    private List<SearchResult> getDummySearchResults(String query) {
        // Create dummy search results for demonstration
        List<SearchResult> results = new ArrayList<>();
        
        if (query != null && !query.isEmpty()) {
            // Create a few sample search results
            results.add(new SearchResult(
                    "1", 
                    "Sample Song 1", 
                    "Artist 1", 
                    "Album 1", 
                    null, 
                    180000,
                    SearchResult.Source.LOCAL,
                    null,
                    null,
                    true
            ));
            
            results.add(new SearchResult(
                    "2", 
                    "Sample Song 2", 
                    "Artist 2", 
                    "Album 2", 
                    null, 
                    240000,
                    SearchResult.Source.SPOTIFY,
                    "https://example.com/preview.mp3",
                    "https://spotify.com/track/123",
                    true
            ));
        }
        
        return results;
    }

    private void handleSearchResults(List<SearchResult> results) {
        hideLoading();
        if (results.isEmpty()) {
            showNoResults();
        } else {
            showResults(results);
        }
    }

    private void showLoading() {
        progressBarSearch.setVisibility(View.VISIBLE);
        textViewNoResults.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBarSearch.setVisibility(View.GONE);
    }

    private void showResults(List<SearchResult> searchResults) {
        textViewNoResults.setVisibility(View.GONE);
        recyclerViewResults.setVisibility(View.VISIBLE);
        adapter.updateResults(searchResults);
    }

    private void showNoResults() {
        recyclerViewResults.setVisibility(View.GONE);
        textViewNoResults.setVisibility(View.VISIBLE);
    }

    private void hideResults() {
        recyclerViewResults.setVisibility(View.GONE);
        textViewNoResults.setVisibility(View.GONE);
        adapter.clearResults();
    }

    @Override
    public void onItemClick(SearchResult searchResult) {
        // Handle click on search result item - delegate to the other method
        onItemClick(searchResult, -1);
    }
    
    @Override
    public void onItemClick(SearchResult searchResult, int position) {
        // Handle click on search result item
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("track_id", searchResult.getId());
        intent.putExtra("track_title", searchResult.getTitle());
        intent.putExtra("track_artist", searchResult.getArtist());
        intent.putExtra("track_album", searchResult.getAlbum());
        intent.putExtra("track_duration", searchResult.getDuration());
        intent.putExtra("track_artwork", searchResult.getAlbumArtUrl());
        intent.putExtra("track_preview_url", searchResult.getPreviewUrl());
        intent.putExtra("track_is_playable", searchResult.isPlayable());
        intent.putExtra("show_track_detail", true);
        startActivity(intent);
    }

    @Override
    public void onPlayButtonClick(SearchResult searchResult, int position) {
        // Handle play button click
        if (searchResult.isPlayable() && searchResult.getPreviewUrl() != null) {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                );
                
                mediaPlayer.setDataSource(searchResult.getPreviewUrl());
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    adapter.setPlayingState(position, true);
                });
                
                mediaPlayer.setOnCompletionListener(mp -> {
                    adapter.stopPlayback();
                });
                
            } catch (IOException e) {
                Log.e("SearchActivity", "Error playing preview: " + e.getMessage());
                Toast.makeText(this, "Failed to play preview", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No preview available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
} 