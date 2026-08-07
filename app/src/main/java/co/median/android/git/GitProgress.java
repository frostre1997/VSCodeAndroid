package co.median.android.git;

/**
 * Progress reporting for long-running Git operations (clone, push, pull, fetch).
 * Implementations forward to the UI (e.g. the JavaScript bridge).
 */
public interface GitProgress {
    void onProgress(String task, int work, int total);
    void onMessage(String message);
}
