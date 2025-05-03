package my.edu.utar.bananamusic.utils.callbacks;

public interface BaseCallback<T> {
    void onSuccess(T result);
    void onError(String message);
} 