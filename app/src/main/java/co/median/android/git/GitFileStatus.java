package co.median.android.git;

/**
 * Status of a single working-tree file, mirroring the model VS Code's Source
 * Control view uses: an {@code indexStatus} (staged state vs HEAD) and a
 * {@code worktreeStatus} (working tree state vs index).
 *
 * Status letter codes:
 *   A = added, M = modified, D = deleted, R = renamed, C = conflict, U = untracked
 */
public class GitFileStatus {
    public String path;
    public String indexStatus;    // null when the file has no staged changes
    public String worktreeStatus; // null when the working tree matches the index
    public boolean untracked;
    public boolean conflicted;

    public GitFileStatus() {
    }

    public GitFileStatus(String path) {
        this.path = path;
    }
}
