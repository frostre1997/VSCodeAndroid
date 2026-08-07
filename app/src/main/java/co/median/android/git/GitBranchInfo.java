package co.median.android.git;

/**
 * Description of a single branch (local or remote).
 */
public class GitBranchInfo {
    public String name;          // short name, e.g. "main"
    public String fullName;      // full ref name, e.g. "refs/heads/main"
    public String remoteName;    // short name of a remote-tracking branch, e.g. "origin/main"
    public String tracking;      // upstream short name for local branches, e.g. "origin/main"
    public boolean current;      // currently checked out
    public boolean remote;       // true for refs/remotes/* branches
}
