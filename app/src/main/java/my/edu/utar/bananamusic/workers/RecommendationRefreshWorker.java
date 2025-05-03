package my.edu.utar.bananamusic.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkRequest;
import androidx.work.WorkManager;
import androidx.work.PeriodicWorkRequest;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;

import java.util.concurrent.TimeUnit;

import my.edu.utar.bananamusic.services.AIRecommendationService;

public class RecommendationRefreshWorker extends Worker {
    private static final String TAG = "RecommendationRefresh";
    private static final String WORK_NAME = "recommendation_refresh";

    public RecommendationRefreshWorker(
        @NonNull Context context,
        @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting recommendation refresh work");
        
        try {
            AIRecommendationService recommendationService = 
                AIRecommendationService.getInstance(getApplicationContext());

            // Check if refresh is needed
            if (recommendationService.shouldRefreshRecommendations()) {
                // Pre-fetch recommendations for next time
                recommendationService.prefetchRecommendations(20);
                
                // Refresh the recommendation model
                recommendationService.refreshRecommendationModel();
                
                Log.d(TAG, "Recommendation refresh completed successfully");
                return Result.success();
            } else {
                Log.d(TAG, "Recommendation refresh not needed at this time");
                return Result.success();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during recommendation refresh", e);
            return Result.retry();
        }
    }

    /**
     * Schedule periodic recommendation refresh
     */
    public static void schedulePeriodicRefresh(Context context) {
        // Create network constraint - only run with network connection
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        // Create periodic work request - run every 6 hours
        PeriodicWorkRequest refreshWork = new PeriodicWorkRequest.Builder(
            RecommendationRefreshWorker.class, 
            6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();

        // Enqueue unique periodic work, replacing any existing one
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                refreshWork);
        
        Log.d(TAG, "Scheduled periodic recommendation refresh");
    }

    /**
     * Cancel scheduled periodic refresh
     */
    public static void cancelPeriodicRefresh(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "Cancelled periodic recommendation refresh");
    }
}