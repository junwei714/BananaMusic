package my.edu.utar.bananamusic.callbacks;

/**
 * Generic callback interface for operations that can succeed or fail
 */
public interface OperationCallback {
    /**
     * Called when the operation is successful
     */
    void onSuccess();

    /**
     * Called when the operation fails
     * @param e The exception that occurred
     */
    void onFailure(Exception e);
} 