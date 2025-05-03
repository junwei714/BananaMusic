package my.edu.utar.bananamusic.utils;

import android.util.Log;
import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Interceptor;

import com.google.common.util.concurrent.RateLimiter;

public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static NetworkManager instance;
    private final OkHttpClient client;
    private final RateLimiter spotifyRateLimiter;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private NetworkManager() {
        client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(new RetryInterceptor(MAX_RETRIES, RETRY_DELAY_MS))
            .build();
        
        // Rate limit to 10 requests per second for Spotify API
        spotifyRateLimiter = RateLimiter.create(10.0);
    }

    public static synchronized NetworkManager getInstance() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    public void get(String url, String accessToken, NetworkCallback successCallback, NetworkErrorCallback errorCallback) {
        if (url.contains("api.spotify.com") && !spotifyRateLimiter.tryAcquire()) {
            errorCallback.onError(new NetworkError("Rate limit exceeded", 429));
            return;
        }

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .get();

        if (accessToken != null && !accessToken.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + accessToken);
        }

        client.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network request failed", e);
                errorCallback.onError(new NetworkError(e.getMessage(), -1));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, errorCallback);
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    successCallback.onSuccess(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    errorCallback.onError(new NetworkError(e.getMessage(), -1));
                } finally {
                    response.close();
                }
            }
        });
    }

    public void put(String url, String accessToken, String body, NetworkCallback successCallback, NetworkErrorCallback errorCallback) {
        if (url.contains("api.spotify.com") && !spotifyRateLimiter.tryAcquire()) {
            errorCallback.onError(new NetworkError("Rate limit exceeded", 429));
            return;
        }

        RequestBody requestBody = RequestBody.create(
            body,
            MediaType.parse("application/json")
        );

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .put(requestBody);

        if (accessToken != null && !accessToken.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + accessToken);
        }

        executeRequest(requestBuilder.build(), successCallback, errorCallback);
    }

    private void handleErrorResponse(Response response, NetworkErrorCallback errorCallback) {
        String message;
        try {
            String responseBody = response.body() != null ? response.body().string() : "";
            JSONObject error = new JSONObject(responseBody);
            message = error.optString("error", "Unknown error");
        } catch (Exception e) {
            message = "Error " + response.code();
        }
        errorCallback.onError(new NetworkError(message, response.code()));
    }

    private void executeRequest(Request request, NetworkCallback successCallback, NetworkErrorCallback errorCallback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network request failed", e);
                errorCallback.onError(new NetworkError(e.getMessage(), -1));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, errorCallback);
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    successCallback.onSuccess(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    errorCallback.onError(new NetworkError(e.getMessage(), -1));
                } finally {
                    response.close();
                }
            }
        });
    }

    public static class NetworkError {
        private final String message;
        private final int code;

        public NetworkError(String message, int code) {
            this.message = message;
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public int getCode() {
            return code;
        }
    }

    public interface NetworkCallback {
        void onSuccess(String response);
    }

    public interface NetworkErrorCallback {
        void onError(NetworkError error);
    }

    public static class RetryInterceptor implements Interceptor {
        private final int maxRetries;
        private final long retryDelayMs;

        RetryInterceptor(int maxRetries, long retryDelayMs) {
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        @Override
        public Response intercept(okhttp3.Interceptor.Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            int retryCount = 0;

            while (retryCount < maxRetries) {
                try {
                    response = chain.proceed(request);
                    if (response.isSuccessful() || !isRetryableError(response.code())) {
                        return response;
                    }
                } catch (IOException e) {
                    if (retryCount == maxRetries - 1) throw e;
                }

                if (response != null) {
                    response.close();
                }

                retryCount++;
                try {
                    Thread.sleep(retryDelayMs * retryCount);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }

            return response != null ? response : chain.proceed(request);
        }

        private boolean isRetryableError(int code) {
            return code == 429 || // Too Many Requests
                   code == 500 || // Internal Server Error
                   code == 502 || // Bad Gateway
                   code == 503 || // Service Unavailable
                   code == 504;   // Gateway Timeout
        }
    }
}