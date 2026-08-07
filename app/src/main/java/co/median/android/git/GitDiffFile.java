package co.median.android.git;

/**
 * A per-file unified diff result for the diff viewer.
 */
public class GitDiffFile {
    public String path;         // file path (new path)
    public String oldPath;      // previous path (for renames)
    public String changeType;   // "ADD" | "MODIFY" | "DELETE" | "RENAME" | "COPY"
    public int additions;
    public int deletions;
    public String diff;         // unified diff text for this file
}
