package my.edu.utar.bananamusic.utils.callbacks;

/**
 * Generic callback interface for operations that can succeed or fail
 */
public interface OperationCallback {
    /**
     * Called when the operation is successful
     * @param message Success message
     */
    void onSuccess(String message);

    /**
     * Called when the operation fails
     * @param message Error message
     */
    void onError(String message);
} 