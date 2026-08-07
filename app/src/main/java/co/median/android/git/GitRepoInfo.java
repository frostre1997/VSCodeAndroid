package co.median.android.git;

import java.io.File;
import java.util.List;

/**
 * High-level description of a repository that is open (or was just cloned).
 */
public class GitRepoInfo {
    public File directory;              // the repository directory
    public String currentBranch;        // null when detached
    public boolean detachedHead;
    public String headId;               // short HEAD commit id, or null
    public String upstream;             // upstream tracking ref for the current branch
    public List<GitBranchInfo> branches;
}
