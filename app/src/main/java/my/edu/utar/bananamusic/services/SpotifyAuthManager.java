package my.edu.utar.bananamusic.services;

import android.util.Base64;
import android.util.Log;
import my.edu.utar.bananamusic.models.SpotifyToken;
import my.edu.utar.bananamusic.network.SpotifyAuthService;
import my.edu.utar.bananamusic.utils.SpotifyConfig;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SpotifyAuthManager {
    private static final String TAG = "SpotifyAuthManager";
    private static SpotifyAuthManager instance;
    private final SpotifyAuthService authService;
    private String currentToken;
    private long tokenExpirationTime;
    private static final Object lock = new Object();

    public interface AuthCallback {
        void onSuccess(String token);
        void onError(String error);
    }

    private SpotifyAuthManager() {
        // Ensure base URL ends with a forward slash for Retrofit
        String baseUrl = SpotifyConfig.AUTH_URL;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        authService = retrofit.create(SpotifyAuthService.class);
    }

    public static SpotifyAuthManager getInstance() {
        if (instance == null) {
            synchronized (SpotifyAuthManager.class) {
                if (instance == null) {
                    instance = new SpotifyAuthManager();
                }
            }
        }
        return instance;
    }

    public void getToken(AuthCallback callback) {
        synchronized (lock) {
            // Check if we have a valid token
            if (currentToken != null && System.currentTimeMillis() < tokenExpirationTime) {
                Log.d(TAG, "Using existing valid token");
                callback.onSuccess(currentToken);
                return;
            }

            Log.d(TAG, "Requesting new token from Spotify");

            // Create credentials string
            String credentials = SpotifyConfig.CLIENT_ID + ":" + SpotifyConfig.CLIENT_SECRET;
            String base64Credentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

            // Make token request
            authService.getToken(
                "Basic " + base64Credentials,
                "client_credentials"
            ).enqueue(new Callback<SpotifyToken>() {
                @Override
                public void onResponse(Call<SpotifyToken> call, Response<SpotifyToken> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        SpotifyToken token = response.body();
                        currentToken = token.getAccessToken();
                        // Set expiration time (subtract 60 seconds for safety margin)
                        tokenExpirationTime = System.currentTimeMillis() + (token.getExpiresIn() - 60) * 1000L;
                        Log.d(TAG, "Successfully obtained new Spotify token");
                        callback.onSuccess(currentToken);
                    } else {
                        String error = "Failed to get token: " + response.code();
                        Log.e(TAG, error);
                        callback.onError(error);
                    }
                }

                @Override
                public void onFailure(Call<SpotifyToken> call, Throwable t) {
                    String error = "Network error while getting token: " + t.getMessage();
                    Log.e(TAG, error, t);
                    callback.onError(error);
                }
            });
        }
    }

    public void clearToken() {
        synchronized (lock) {
            currentToken = null;
            tokenExpirationTime = 0;
        }
    }
} 