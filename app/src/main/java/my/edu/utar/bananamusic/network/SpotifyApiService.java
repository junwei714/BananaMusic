package my.edu.utar.bananamusic.network;

import my.edu.utar.bananamusic.models.SpotifySearchResponse;
import my.edu.utar.bananamusic.models.SpotifyToken;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;

public interface SpotifyApiService {
    @FormUrlEncoded
    @POST("api/token")
    Call<SpotifyToken> getAccessToken(
        @Field("grant_type") String grantType,
        @Header("Authorization") String authorization
    );

    @GET("v1/search")
    Call<SpotifySearchResponse> searchTracks(
        @Header("Authorization") String authorization,
        @Query("q") String query,
        @Query("type") String type,
        @Query("limit") int limit
    );
} 
 