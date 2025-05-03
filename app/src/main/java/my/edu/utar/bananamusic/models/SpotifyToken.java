package my.edu.utar.bananamusic.models;

import com.google.gson.annotations.SerializedName;

public class SpotifyToken {
    @SerializedName("access_token")
    private String accessToken;
    
    @SerializedName("token_type")
    private String tokenType;
    
    @SerializedName("expires_in")
    private int expiresIn;
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public int getExpiresIn() {
        return expiresIn;
    }

    public String getAuthorizationHeader() {
        return tokenType + " " + accessToken;
    }
} 
 