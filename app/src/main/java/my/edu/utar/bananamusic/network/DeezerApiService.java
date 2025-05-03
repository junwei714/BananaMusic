package my.edu.utar.bananamusic.network;

import my.edu.utar.bananamusic.models.DeezerSearchResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DeezerApiService {
    @GET("search/track")
    Call<DeezerSearchResponse> searchTrack(
        @Query("q") String query
    );
} 
 