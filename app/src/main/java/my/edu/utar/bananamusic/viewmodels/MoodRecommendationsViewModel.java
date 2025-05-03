package my.edu.utar.bananamusic.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import my.edu.utar.bananamusic.models.Track;
import my.edu.utar.bananamusic.services.MoodClassifierService;

/**
 * ViewModel for handling mood-based music recommendations.
 */
public class MoodRecommendationsViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Track>> recommendedTracks = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    
    private final MoodClassifierService moodClassifierService;
    
    public MoodRecommendationsViewModel(@NonNull Application application) {
        super(application);
        moodClassifierService = MoodClassifierService.getInstance(application);
    }

    public LiveData<List<Track>> getRecommendedTracks() {
        return recommendedTracks;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public List<String> getSupportedMoods() {
        return moodClassifierService.getSupportedMoods();
    }

    public void loadRecommendationsForMood(String mood) {
        // Reset error state
        errorMessage.setValue(null);
        
        // Show loading state
        isLoading.setValue(true);
        
        // Get recommendations from the service
        moodClassifierService.getRecommendationsForMood(mood, new MoodClassifierService.RecommendationsCallback() {
            @Override
            public void onRecommendationsReady(List<Track> recommendations) {
                isLoading.postValue(false);
                recommendedTracks.postValue(recommendations);
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
                errorMessage.postValue(error);
                recommendedTracks.postValue(new ArrayList<>());
            }
        });
    }

    public void retry() {
        // Clear any previous error
        errorMessage.setValue(null);
        
        // Clear cache to force a fresh load
        moodClassifierService.clearCache();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // Perform any cleanup if needed
    }
} 