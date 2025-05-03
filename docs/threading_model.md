# BananaMusic Threading Model

## Overview

BananaMusic follows a consistent threading model to ensure UI responsiveness and prevent Application Not Responding (ANR) errors. This document outlines the patterns and practices used throughout the codebase.

## Key Components

### UIThreadHelper

A centralized utility class (`UIThreadHelper.java`) that provides methods for ensuring operations run on the UI thread:

- `runOnMainThread`: Executes a Runnable on the main thread
- `runOnMainThreadDelayed`: Executes a Runnable on the main thread after a delay
- `isOnMainThread`: Checks if the current execution is on the main thread
- `ensureMainThread`: Throws an exception if not called on the main thread

### ApiDataProvider Threading

The `ApiDataProvider` class implements a thread-safe pattern for network operations:

1. All network operations use OkHttp's asynchronous `enqueue` method to avoid blocking the UI thread
2. The `runOnUiThread` helper method ensures all callbacks execute on the main thread
3. This method checks if the current thread is already the main thread, and if not, posts the operation to the main thread

```java
private void runOnUiThread(Runnable runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        // Already on main thread, run immediately
        try {
            runnable.run();
        } catch (Exception e) {
            Log.e(TAG, "Error executing runnable on main thread: " + e.getMessage(), e);
        }
    } else {
        // Post to main thread handler
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                Log.e(TAG, "Error executing posted runnable on main thread: " + e.getMessage(), e);
            }
        });
    }
}
```

## Best Practices

When implementing features in BananaMusic, follow these threading best practices:

1. **Never block the UI thread**: All long-running operations (network calls, database operations, file I/O) should be performed on background threads
2. **Always update UI on the main thread**: Use the provided helpers (UIThreadHelper or runOnUiThread) to ensure UI updates happen on the main thread
3. **Be mindful of thread transitions**: When transitioning from background to UI thread, use the appropriate helper methods
4. **Handle exceptions within background operations**: Prevent uncaught exceptions from crashing background threads
5. **Consider using higher-level abstractions**: Kotlin coroutines or RxJava can simplify complex threading scenarios

## Common Patterns

### Network Requests Pattern

```java
// Make network request on background thread
client.newCall(request).enqueue(new Callback() {
    @Override
    public void onFailure(Call call, IOException e) {
        // Use helper to get back to UI thread
        runOnUiThread(() -> {
            // Safe to update UI or call UI-thread-only methods
            callback.onError("Network error: " + e.getMessage());
        });
    }
    
    @Override
    public void onResponse(Call call, Response response) {
        // Process response on background thread
        try {
            String data = response.body().string();
            List<Item> items = parseItems(data);
            
            // Use helper to get back to UI thread
            runOnUiThread(() -> {
                // Safe to update UI
                callback.onSuccess(items);
            });
        } catch (Exception e) {
            runOnUiThread(() -> callback.onError("Error: " + e.getMessage()));
        }
    }
});
```

## Troubleshooting

- **ANR Issues**: If you encounter ANR warnings, check for operations blocking the UI thread
- **CalledFromWrongThreadException**: This indicates you're trying to modify UI elements from a non-UI thread
- **Thread Pool Exhaustion**: Be careful about creating too many threads; use existing thread pools when possible 