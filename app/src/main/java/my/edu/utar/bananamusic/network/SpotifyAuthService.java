package my.edu.utar.bananamusic.network;

import my.edu.utar.bananamusic.models.SpotifyToken;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface SpotifyAuthService {
    @FormUrlEncoded
    @POST("token")
    Call<SpotifyToken> getToken(
        @Header("Authorization") String authorization,
        @Field("grant_type") String grantType
    );
} 
 