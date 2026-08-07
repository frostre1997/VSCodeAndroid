package co.median.android.git;

/**
 * A single commit from the repository history.
 */
public class GitCommitInfo {
    public String id;           // full object id
    public String shortId;      // abbreviated id
    public String authorName;
    public String authorEmail;
    public String message;      // full message
    public String subject;      // first line
    public long commitTime;     // epoch seconds
    public int parentCount;
}
