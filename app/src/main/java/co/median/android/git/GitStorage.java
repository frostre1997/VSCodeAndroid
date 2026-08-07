package co.median.android.git;

import android.content.Context;

import java.io.File;

/**
 * Filesystem layout for Git data inside the app:
 *
 *   {storage}/repos   – cloned / initialized repositories
 *   {storage}/ssh     – SSH keys (app-private)
 *
 * App-specific external storage is used when available so repositories are
 * visible in file managers, falling back to internal storage otherwise.
 */
public final class GitStorage {

    public static final String DEFAULT_REPOS_DIR = "repos";
    public static final String DEFAULT_SSH_DIR = "ssh";

    private final Context context;

    public GitStorage(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getBaseDir() {
        File external = context.getExternalFilesDir(null);
        if (external != null) return external;
        return context.getFilesDir();
    }

    public File getReposDir() {
        File dir = new File(getBaseDir(), DEFAULT_REPOS_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getSshDir() {
        File dir = new File(getBaseDir(), DEFAULT_SSH_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}
