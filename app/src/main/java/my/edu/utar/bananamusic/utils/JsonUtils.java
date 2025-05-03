package my.edu.utar.bananamusic.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Utility class for JSON operations using Gson
 */
public class JsonUtils {
    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * Convert an object to JSON string
     */
    public static String toJson(Object obj) {
        try {
            return gson.toJson(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert JSON string to object of specified class
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
} 