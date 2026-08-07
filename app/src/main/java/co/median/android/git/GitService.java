package co.median.android.git;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteListCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Android-free wrapper around JGit providing the Git operations the app needs:
 * open/clone, status, stage/unstage, commit, push, pull, fetch, branch
 * management, diff, log, config and discard.
 *
 * Being free of Android dependencies it can be compiled and unit-tested on a
 * plain JVM. The bridge layer (GitBridge) handles JSON serialization and runs
 * operations off the UI thread.
 */
public final class GitService {

    /** Default commit identity used when repo config has no user.name/email. */
    public static final String DEFAULT_AUTHOR_NAME = "VSCodeAndroid";
    public static final String DEFAULT_AUTHOR_EMAIL = "vscode@android.local";

    private static final Lock SSH_LOCK = new ReentrantLock();
    private static volatile GitSshSessionFactory sshFactory;

    private GitService() {
    }

    // ------------------------------------------------------------------
    // Repository discovery
    // ------------------------------------------------------------------

    /**
     * Resolve the .git directory for a working-tree directory, following the
     * "gitdir:" indirection used by linked worktrees and submodules. Returns
     * null when the directory is not inside a repository.
     */
    public static File resolveGitDir(File workTree) {
        if (workTree == null) return null;
        File dotGit = new File(workTree, Constants.DOT_GIT);
        if (dotGit.isDirectory()) return dotGit;
        if (dotGit.isFile()) {
            try {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(dotGit), StandardCharsets.UTF_8));
                String line;
                try {
                    line = reader.readLine();
                } finally {
                    reader.close();
                }
                if (line != null && line.startsWith("gitdir:")) {
                    String path = line.substring("gitdir:".length()).trim();
                    File gitDir = new File(path);
                    if (!gitDir.isAbsolute()) gitDir = new File(workTree, path);
                    return gitDir.isDirectory() ? gitDir : null;
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return null;
    }

    /**
     * Walk up from {@code start} looking for a repository. Returns the working
     * tree root or null.
     */
    public static File findRepositoryRoot(File start) {
        if (start == null) return null;
        File dir = start.isFile() ? start.getParentFile() : start;
        while (dir != null) {
            if (resolveGitDir(dir) != null) return dir;
            dir = dir.getParentFile();
        }
        return null;
    }

    public static boolean isRepository(File dir) {
        return findRepositoryRoot(dir) != null;
    }

    /** Open the repository containing {@code dir}. */
    public static Repository open(File dir) throws GitServiceException {
        File root = findRepositoryRoot(dir);
        if (root == null) {
            throw new GitServiceException("Not a Git repository: " + dir.getAbsolutePath());
        }
        File gitDir = resolveGitDir(root);
        try {
            return new FileRepositoryBuilder()
                    .setWorkTree(root)
                    .setGitDir(gitDir)
                    .setMustExist(true)
                    .build();
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to open repository", e);
        }
    }

    /**
     * Derive the directory a clone will be placed in: {@code base / repoName}
     * where repoName comes from the last path segment of the URL.
     */
    public static File defaultCloneDir(File base, String url) {
        String clean = url.trim();
        int q = clean.indexOf('?');
        if (q >= 0) clean = clean.substring(0, q);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : clean;
        if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
        name = name.replaceAll("[^A-Za-z0-9._-]", "-");
        if (name.isEmpty()) name = "repository";
        return new File(base, name);
    }

    // ------------------------------------------------------------------
    // High-level info
    // ------------------------------------------------------------------

    public static GitRepoInfo getRepoInfo(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            GitRepoInfo info = new GitRepoInfo();
            info.directory = repo.getWorkTree();
            String branch = safe(() -> repo.getBranch());
            if (branch == null || branch.equals("HEAD")) {
                info.detachedHead = true;
            } else {
                info.currentBranch = branch;
            }
            ObjectId head = safe(() -> repo.resolve(Constants.HEAD));
            if (head != null) info.headId = abbreviate(head.name());
            info.upstream = upstreamName(repo, info.currentBranch);
            info.branches = listBranches(repo);
            return info;
        }
    }

    public static List<GitBranchInfo> listBranches(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            return listBranches(repo);
        }
    }

    public static List<String> listRemotes(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            List<String> names = new ArrayList<>();
            RemoteListCommand cmd = Git.wrap(repo).remoteList();
            try {
                for (RemoteConfig rc : cmd.call()) {
                    names.add(rc.getName());
                }
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to list remotes", e);
            }
            return names;
        }
    }

    /**
     * Commits the current branch is ahead of / behind its upstream tracking
     * branch. Returns {ahead, behind}. When there is no upstream or no commits,
     * returns {0, 0}.
     */
    public static int[] aheadBehind(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            String branch = safe(() -> repo.getBranch());
            String upstream = upstreamName(repo, branch);
            if (branch == null || upstream == null) return new int[]{0, 0};

            ObjectId head = safe(() -> repo.resolve(Constants.HEAD));
            ObjectId up = safe(() -> repo.resolve(Constants.R_REMOTES + upstream));
            if (head == null || up == null) return new int[]{0, 0};

            try {
                 long behind = Git.wrap(repo).log().addRange(head, up).call().asList().size();
                 long ahead = Git.wrap(repo).log().addRange(up, head).call().asList().size();
                return new int[]{(int) ahead, (int) behind};
            } catch (GitAPIException e) {
                return new int[]{0, 0};
            }
        }
    }

    public static String getConfig(File dir, String key) throws GitServiceException {
        try (Repository repo = open(dir)) {
            String[] parts = splitConfigKey(key);
            String value = null;
            if (parts.length == 2) {
                value = repo.getConfig().getString(parts[0], null, parts[1]);
            } else if (parts.length == 3) {
                value = repo.getConfig().getString(parts[0], parts[1], parts[2]);
            }
            return value;
        }
    }

    public static void setConfig(File dir, String key, String value) throws GitServiceException {
        try (Repository repo = open(dir)) {
            String[] parts = splitConfigKey(key);
            StoredConfig cfg = repo.getConfig();
            if (parts.length == 2) {
                cfg.setString(parts[0], null, parts[1], value);
            } else if (parts.length == 3) {
                cfg.setString(parts[0], parts[1], parts[2], value);
            } else {
                throw new GitServiceException("Invalid config key: " + key);
            }
            try {
                cfg.save();
            } catch (IOException e) {
                throw GitServiceException.wrap("Failed to save config", e);
            }
        }
    }

    public static void init(File dir) throws GitServiceException {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                throw new GitServiceException("Could not create directory " + dir.getAbsolutePath());
            }
            Git.init().setDirectory(dir).call().close();
            try (Repository repo = open(dir)) {
                configureRepository(repo);
            }
        } catch (GitAPIException e) {
            throw GitServiceException.wrap("Failed to initialize repository", e);
        }
    }

    // ------------------------------------------------------------------
    // Status / staging / committing
    // ------------------------------------------------------------------

    public static List<GitFileStatus> status(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            Status status;
            try {
                status = Git.wrap(repo).status().setIgnoreSubmodules(org.eclipse.jgit.submodule.SubmoduleWalk.IgnoreSubmoduleMode.ALL).call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to read status", e);
            }

            Map<String, GitFileStatus> byPath = new LinkedHashMap<>();

            for (String p : status.getAdded()) byPath.put(p, with(byPath, p, "A", null));
            for (String p : status.getChanged()) byPath.put(p, with(byPath, p, "M", null));
            for (String p : status.getRemoved()) byPath.put(p, with(byPath, p, "D", null));
            for (String p : status.getModified()) byPath.put(p, with(byPath, p, null, "M"));
            for (String p : status.getMissing()) byPath.put(p, with(byPath, p, null, "D"));
            for (String p : status.getUntracked()) {
                GitFileStatus s = with(byPath, p, null, "U");
                s.untracked = true;
                byPath.put(p, s);
            }
            for (String p : status.getConflicting()) {
                GitFileStatus s = with(byPath, p, "C", "C");
                s.conflicted = true;
                byPath.put(p, s);
            }

            List<GitFileStatus> result = new ArrayList<>(byPath.values());
            result.sort(Comparator.comparing(a -> a.path));
            return result;
        }
    }

    private static GitFileStatus with(Map<String, GitFileStatus> byPath, String path, String index, String worktree) {
        GitFileStatus s = byPath.get(path);
        if (s == null) s = new GitFileStatus(path);
        if (index != null) s.indexStatus = index;
        if (worktree != null) s.worktreeStatus = worktree;
        return s;
    }

    public static void stage(File dir, List<String> paths, boolean stageAll) throws GitServiceException {
        try (Repository repo = open(dir)) {
            org.eclipse.jgit.api.AddCommand cmd = Git.wrap(repo).add();
            if (stageAll || paths == null || paths.isEmpty()) {
                cmd.addFilepattern(".");
            } else {
                for (String p : paths) cmd.addFilepattern(normalizePattern(p));
            }
            try {
                cmd.call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to stage changes", e);
            }
        }
    }

    public static void unstage(File dir, List<String> paths, boolean unstageAll) throws GitServiceException {
        try (Repository repo = open(dir)) {
            // Reset the index for the given paths (keep working tree untouched).
            List<String> targets;
            if (unstageAll || paths == null || paths.isEmpty()) {
                targets = Collections.singletonList(".");
            } else {
                targets = paths;
            }
            try {
                for (String p : targets) {
                    org.eclipse.jgit.api.ResetCommand reset = Git.wrap(repo).reset();
                    reset.addPath(normalizePattern(p));
                    reset.call();
                }
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to unstage changes", e);
            }
        }
    }

    public static void commit(File dir, String message, boolean amend, String authorName, String authorEmail)
            throws GitServiceException {
        if (message == null || message.trim().isEmpty()) {
            throw new GitServiceException("Commit message cannot be empty");
        }
        try (Repository repo = open(dir)) {
            StoredConfig cfg = repo.getConfig();
            String name = firstNonBlank(authorName, cfg.getString(ConfigConstants.CONFIG_USER_SECTION, null,
                    ConfigConstants.CONFIG_KEY_NAME), DEFAULT_AUTHOR_NAME);
            String email = firstNonBlank(authorEmail, cfg.getString(ConfigConstants.CONFIG_USER_SECTION, null,
                    ConfigConstants.CONFIG_KEY_EMAIL), DEFAULT_AUTHOR_EMAIL);

            org.eclipse.jgit.api.CommitCommand cmd = Git.wrap(repo).commit();
            cmd.setMessage(message);
            cmd.setAmend(amend);
            cmd.setAuthor(name, email);
            cmd.setCommitter(name, email);
            try {
                RevCommit result = cmd.call();
                if (result == null && !amend) {
                    throw new GitServiceException("Nothing to commit — no staged changes");
                }
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Commit failed", e);
            }
        }
    }

    public static void discardChanges(File dir, List<String> paths) throws GitServiceException {
        try (Repository repo = open(dir)) {
            if (paths == null || paths.isEmpty()) {
                // Discard everything: hard reset tracked files and remove untracked.
                try {
                    Git.wrap(repo).clean().setForce(true).call();
                    Git.wrap(repo).checkout().call();
                } catch (GitAPIException e) {
                    throw GitServiceException.wrap("Failed to discard all changes", e);
                }
                return;
            }

            Status status;
            try {
                status = Git.wrap(repo).status().call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to read status", e);
            }

            for (String p : paths) {
                String clean = normalizePattern(p);
                if (status.getUntracked().contains(clean)) {
                    org.eclipse.jgit.api.CleanCommand cmd = Git.wrap(repo).clean();
                    cmd.setForce(true);
                    cmd.setPaths(Collections.singleton(clean));
                    try {
                        cmd.call();
                    } catch (GitAPIException e) {
                        throw GitServiceException.wrap("Failed to discard " + p, e);
                    }
                } else {
                    org.eclipse.jgit.api.CheckoutCommand cmd = Git.wrap(repo).checkout();
                    cmd.addPath(clean);
                    try {
                        cmd.call();
                    } catch (GitAPIException e) {
                        throw GitServiceException.wrap("Failed to discard " + p, e);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Diff
    // ------------------------------------------------------------------

    /**
     * Produce per-file unified diffs. When {@code staged} is true the diff is
     * index vs HEAD, otherwise it is working tree vs index.
     */
    public static List<GitDiffFile> diff(File dir, String path, boolean staged) throws GitServiceException {
        try (Repository repo = open(dir)) {
            DiffCommand cmd = Git.wrap(repo).diff();
            cmd.setCached(staged);
            if (path != null && !path.isEmpty()) {
                cmd.setPathFilter(PathFilter.create(normalizePattern(path)));
            }

            List<DiffEntry> entries;
            try {
                entries = cmd.call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to compute diff", e);
            }

            // Detect renames across the raw add/delete pairs.
            if (entries != null && !entries.isEmpty()) {
                RenameDetector detector = new RenameDetector(repo);
                detector.addAll(entries);
                try {
                    entries = detector.compute();
                } catch (IOException e) {
                    // fall back to the undetected entries
                }
            }

            List<GitDiffFile> result = new ArrayList<>();
            try {
                for (DiffEntry entry : entries) {
                    GitDiffFile df = new GitDiffFile();
                    df.path = entry.getNewPath();
                    df.oldPath = entry.getOldPath();
                    df.changeType = entry.getChangeType().name();

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    DiffFormatter fileFormatter = new DiffFormatter(out);
                    fileFormatter.setRepository(repo);
                    fileFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                    fileFormatter.setContext(3);
                    fileFormatter.format(entry);
                    fileFormatter.close();

                    String text = out.toString(StandardCharsets.UTF_8.name());
                    df.diff = text;
                    df.additions = countLines(text, '+');
                    df.deletions = countLines(text, '-');
                    result.add(df);
                }
            } catch (IOException e) {
                throw GitServiceException.wrap("Failed to format diff", e);
            }

            result.sort(Comparator.comparing(a -> a.path));
            return result;
        }
    }

    private static int countLines(String text, char marker) {
        int count = 0;
        String[] lines = text.split("\n", -1);
        String header = marker == '+' ? "+++" : "---";
        for (String line : lines) {
            if (line.startsWith(String.valueOf(marker)) && !line.startsWith(header)) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    /**
     * Show the diff introduced by a single commit (commit vs its first parent).
     */
    public static List<GitDiffFile> show(File dir, String commitId) throws GitServiceException {
        try (Repository repo = open(dir);
             org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader();
             org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(reader)) {
            RevCommit commit = rw.parseCommit(repo.resolve(commitId));
            RevCommit parent = commit.getParentCount() > 0
                    ? rw.parseCommit(commit.getParent(0).getId()) : null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter formatter = new DiffFormatter(out);
            formatter.setRepository(repo);
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            formatter.setContext(3);

            org.eclipse.jgit.treewalk.CanonicalTreeParser newParser = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
            newParser.reset(reader, commit.getTree());
            List<DiffEntry> entries;
            if (parent == null) {
                entries = formatter.scan(new org.eclipse.jgit.treewalk.EmptyTreeIterator(), newParser);
            } else {
                org.eclipse.jgit.treewalk.CanonicalTreeParser oldParser = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                oldParser.reset(reader, parent.getTree());
                entries = formatter.scan(oldParser, newParser);
            }

            List<GitDiffFile> result = new ArrayList<>();
            for (DiffEntry entry : entries) {
                GitDiffFile df = new GitDiffFile();
                df.path = entry.getNewPath();
                df.oldPath = entry.getOldPath();
                df.changeType = entry.getChangeType().name();
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(baos)) {
                String text;
                try {
                    formatter.setRepository(repo);
                    formatter.format(entry);
                    text = baos.toString(StandardCharsets.UTF_8.name());
                } finally {
                    formeatter.close();
                }
                df.diff = text;
                df.additions = countLines(text, '+');
                df.deletions = countLines(text, '-');
                result.add(df);
            }
            formatter.close();
            return result;
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to show commit", e);
        }
    }

    public static List<GitCommitInfo> log(File dir, int maxCount) throws GitServiceException {
        try (Repository repo = open(dir)) {
            LogCommand cmd = Git.wrap(repo).log();
            cmd.setMaxCount(maxCount > 0 ? maxCount : 100);
            List<GitCommitInfo> result = new ArrayList<>();
            try {
                for (RevCommit commit : cmd.call()) {
                    GitCommitInfo info = new GitCommitInfo();
                    info.id = commit.getId().name();
                    info.shortId = abbreviate(info.id);
                    info.authorName = commit.getAuthorIdent().getName();
                    info.authorEmail = commit.getAuthorIdent().getEmailAddress();
                    info.message = commit.getFullMessage().trim();
                    info.subject = commit.getShortMessage();
                    info.commitTime = commit.getCommitTime();
                    info.parentCount = commit.getParentCount();
                    result.add(info);
                }
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to read history", e);
            }
            return result;
        }
    }

    // ------------------------------------------------------------------
    // Branches
    // ------------------------------------------------------------------

    public static GitBranchInfo createBranch(File dir, String name, String startPoint) throws GitServiceException {
        try (Repository repo = open(dir)) {
            CreateBranchCommand cmd = Git.wrap(repo).branchCreate();
            cmd.setName(name);
            if (startPoint != null && !startPoint.isEmpty()) cmd.setStartPoint(startPoint);
            try {
                cmd.call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to create branch " + name, e);
            }
            return findBranch(repo, "refs/heads/" + name);
        }
    }

    public static GitBranchInfo checkout(File dir, String name, boolean createNew, String startPoint)
            throws GitServiceException {
        try (Repository repo = open(dir)) {
            org.eclipse.jgit.api.CheckoutCommand cmd = Git.wrap(repo).checkout();
            cmd.setName(name);
            if (createNew) {
                cmd.setCreateBranch(true);
                cmd.setStartPoint(startPoint != null ? startPoint : Constants.HEAD);
            }
            try {
                cmd.call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to checkout " + name, e);
            }

            GitBranchInfo b = new GitBranchInfo();
            b.name = name;
            b.fullName = createNew ? Constants.R_HEADS + name : name;
            String current = safe(() -> repo.getBranch());
            b.current = name.equals(current);
            if (!b.current && current != null) {
                // checked out a remote ref or commit; report the resolved branch
                b.current = current.equals(name) || current.equals("HEAD");
                if (current.equals("HEAD")) {
                    b.name = name;
                    b.current = true;
                }
            }
            return b;
        }
    }

    public static void deleteBranch(File dir, String name, boolean force) throws GitServiceException {
        try (Repository repo = open(dir)) {
            org.eclipse.jgit.api.DeleteBranchCommand cmd = Git.wrap(repo).branchDelete();
            cmd.setBranchNames(name);
            cmd.setForce(force);
            try {
                cmd.call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to delete branch " + name, e);
            }
        }
    }

    public static void checkoutCommit(File dir, String commitId) throws GitServiceException {
        try (Repository repo = open(dir)) {
            org.eclipse.jgit.api.CheckoutCommand cmd = Git.wrap(repo).checkout();
            cmd.setName(commitId);
            try {
                cmd.call();
            } catch (GitAPIException e) {
                throw GitServiceException.wrap("Failed to checkout " + abbreviate(commitId), e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Remotes: clone, push, pull, fetch
    // ------------------------------------------------------------------

    public static GitRepoInfo clone(String url, File destDir, boolean recursive,
                                    GitCredentialRequest creds, GitProgress progress) throws GitServiceException {
        if (url == null || url.trim().isEmpty()) {
            throw new GitServiceException("Clone URL cannot be empty");
        }
        if (destDir == null) {
            throw new GitServiceException("Destination directory not provided");
        }
        if (destDir.exists()) {
            File[] children = destDir.listFiles();
            if (children != null && children.length > 0) {
                throw new GitServiceException("Destination directory is not empty: " + destDir.getAbsolutePath());
            }
        }
        try {
            if (!destDir.exists() && !destDir.mkdirs()) {
                throw new GitServiceException("Could not create directory " + destDir.getAbsolutePath());
            }
        } catch (SecurityException e) {
            throw GitServiceException.wrap("Could not create directory", e);
        }

        CloneCommand cmd = Git.cloneRepository();
        cmd.setURI(url.trim());
        cmd.setDirectory(destDir);
        cmd.setCloneSubmodules(recursive);
        cmd.setProgressMonitor(progressMonitor(progress));

        CredentialsProvider cp = credentialsProviderFor(url, creds);
        if (cp != null) cmd.setCredentialsProvider(cp);

        try (Git git = cmd.call()) {
            Repository repo = git.getRepository();
            configureRepository(repo);
        } catch (GitAPIException e) {
            throw GitServiceException.wrap("Clone failed", e);
        } catch (Exception e) {
            // Clean up a partial clone so a retry is possible.
            org.eclipse.jgit.util.FileUtils.delete(destDir, org.eclipse.jgit.util.FileUtils.RECURSIVE | org.eclipse.jgit.util.FileUtils.IGNORE_ERRORS);
            throw GitServiceException.wrap("Clone failed", e);
        }

        return getRepoInfo(destDir);
    }

    public static void push(File dir, String remote, String branch, boolean force,
                            GitCredentialRequest creds, GitProgress progress) throws GitServiceException {
        try (Repository repo = open(dir)) {
            String current = branch;
            if (current == null) {
                current = safe(() -> repo.getBranch());
            }
            if (current == null || current.equals("HEAD")) {
                throw new GitServiceException("Cannot push while in detached HEAD state");
            }

            String remoteName = remote;
            String merge = repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, current,
                    ConfigConstants.CONFIG_KEY_MERGE);
            if (remoteName == null) {
                remoteName = repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, current,
                        ConfigConstants.CONFIG_KEY_REMOTE);
            }
            if (remoteName == null) remoteName = Constants.DEFAULT_REMOTE_NAME;

            RefSpec spec = new RefSpec(current + ":" + (merge != null ? merge : "refs/heads/" + current));

            PushCommand cmd = Git.wrap(repo).push();
            cmd.setRemote(remoteName);
            cmd.setRefSpecs(spec);
            cmd.setForce(force);
            cmd.setProgressMonitor(progressMonitor(progress));
            cmd.setPushTags();

            CredentialsProvider cp = credentialsProviderFor(repo.getConfig().getString("remote", remoteName, "url"), creds);
            if (cp != null) cmd.setCredentialsProvider(cp);

            try {
                boolean rejected = false;
                StringBuilder detail = new StringBuilder();
                for (PushResult result : cmd.call()) {
                    for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                        org.eclipse.jgit.transport.RemoteRefUpdate.Status st = update.getStatus();
                        if (st != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK
                                && st != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                            rejected = true;
                            if (detail.length() > 0) detail.append("; ");
                            detail.append(update.getRemoteName()).append(": ").append(st);
                            if (update.getMessage() != null) detail.append(" (").append(update.getMessage()).append(")");
                        }
                    }
                }
                if (rejected) {
                    throw new GitServiceException("Push rejected: " + detail);
                }
            } catch (GitServiceException e) {
                throw e;
            } catch (Exception e) {
                throw GitServiceException.wrap("Push failed", e);
            }
        }
    }

    public static void pull(File dir, String remote, String branch, boolean rebase,
                            GitCredentialRequest creds, GitProgress progress) throws GitServiceException {
        try (Repository repo = open(dir)) {
            PullCommand cmd = Git.wrap(repo).pull();
            if (remote != null) cmd.setRemote(remote);
            if (branch != null) cmd.setRemoteBranchName(branch);
            cmd.setRebase(rebase);
            cmd.setProgressMonitor(progressMonitor(progress));

            String url = repo.getConfig().getString("remote", remote != null ? remote
                    : Constants.DEFAULT_REMOTE_NAME, "url");
            CredentialsProvider cp = credentialsProviderFor(url, creds);
            if (cp != null) cmd.setCredentialsProvider(cp);

            try {
                PullResult result = cmd.call();
                if (result == null || !result.isSuccessful()) {
                    org.eclipse.jgit.api.MergeResult merge = result != null ? result.getMergeResult() : null;
                    org.eclipse.jgit.api.RebaseResult rebaseRes = result != null ? result.getRebaseResult() : null;
                    String detail = "Pull failed";
                    if (merge != null) {
                        detail += " — " + merge.getMergeStatus();
                        if (merge.getMergeStatus() == org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING) {
                            detail += " (conflicting files)";
                        }
                    }
                    if (rebaseRes != null) {
                        detail += " — " + rebaseRes.getStatus();
                    }
                    throw new GitServiceException(detail);
                }
            } catch (GitServiceException e) {
                throw e;
            } catch (Exception e) {
                throw GitServiceException.wrap("Pull failed", e);
            }
        }
    }

    public static void fetch(File dir, String remote, GitCredentialRequest creds, GitProgress progress)
            throws GitServiceException {
        try (Repository repo = open(dir)) {
            FetchCommand cmd = Git.wrap(repo).fetch();
            if (remote != null) cmd.setRemote(remote);
            cmd.setProgressMonitor(progressMonitor(progress));
            String url = repo.getConfig().getString("remote",
                    remote != null ? remote : Constants.DEFAULT_REMOTE_NAME, "url");
            CredentialsProvider cp = credentialsProviderFor(url, creds);
            if (cp != null) cmd.setCredentialsProvider(cp);
            try {
                cmd.call();
            } catch (Exception e) {
                throw GitServiceException.wrap("Fetch failed", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // SSH configuration
    // ------------------------------------------------------------------

    /**
     * Point JGit's SSH transport at the app's key directory so key-based auth
     * works for ssh:// and scp-like URLs. Must be called before any SSH remote
     * operation; safe to call repeatedly.
     */
    public static void setSshConfig(File sshDir, GitCredentialRequest creds) {
        if (sshDir == null) return;
        GitSshSessionFactory factory = new GitSshSessionFactory(sshDir, creds);
        SSH_LOCK.lock();
        try {
            SshSessionFactory.setInstance(factory);
            sshFactory = factory;
        } finally {
            SSH_LOCK.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static List<GitBranchInfo> listBranches(Repository repo) throws GitServiceException {
        List<GitBranchInfo> result = new ArrayList<>();
        String current = safe(() -> repo.getBranch());
        try {
            for (Ref ref : Git.wrap(repo).branchList().setListMode(ListBranchCommand.ListMode.ALL).call()) {
                GitBranchInfo b = new GitBranchInfo();
                b.fullName = ref.getName();
                b.remote = ref.getName().startsWith(Constants.R_REMOTES);
                if (b.remote) {
                    b.remoteName = shortenRef(ref.getName(), Constants.R_REMOTES);
                    b.name = b.remoteName;
                } else {
                    b.name = shortenRef(ref.getName(), Constants.R_HEADS);
                    b.tracking = upstreamName(repo, b.name);
                    b.current = b.name.equals(current);
                }
                result.add(b);
            }
        } catch (GitAPIException e) {
            throw GitServiceException.wrap("Failed to list branches", e);
        }
        result.sort(Comparator.comparing((GitBranchInfo a) -> a.remote).thenComparing(a -> a.name));
        return result;
    }

    private static GitBranchInfo findBranch(Repository repo, String fullName) throws GitServiceException {
        for (GitBranchInfo b : listBranches(repo)) {
            if (b.fullName.equals(fullName)) return b;
        }
        GitBranchInfo b = new GitBranchInfo();
        b.fullName = fullName;
        b.name = fullName.startsWith(Constants.R_HEADS) ? shortenRef(fullName, Constants.R_HEADS) : fullName;
        return b;
    }

    private static String upstreamName(Repository repo, String branch) {
        if (branch == null) return null;
        String remote = repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                ConfigConstants.CONFIG_KEY_REMOTE);
        String merge = repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                ConfigConstants.CONFIG_KEY_MERGE);
        if (remote == null || merge == null) return null;
        return remote + "/" + shortenRef(merge, Constants.R_HEADS);
    }

    private static void configureRepository(Repository repo) throws GitServiceException {
        // Android filesystems can report unreliable folder modification times;
        // disabling folder stat avoids false "changed" results.
        StoredConfig cfg = repo.getConfig();
        cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, "trustfolderstat", false);
        try {
            cfg.save();
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to write repository config", e);
        }
    }

    private static CredentialsProvider credentialsProviderFor(String url, GitCredentialRequest creds) {
        if (creds == null) return null;
        return new GitCredentialsProvider(creds);
    }

    private static boolean isSshUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        return u.startsWith("ssh://") || u.startsWith("ssh+git://") || u.startsWith("git+ssh://")
                || u.startsWith("git@") || u.matches("^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+:.+");
    }

    private static ProgressMonitor progressMonitor(GitProgress progress) {
        if (progress == null) return null;
        return new ProgressMonitor() {
            private volatile String title = "";
            private volatile int total = -1;

            @Override
            public void start(int totalTasks) {
            }

            @Override
            public void beginTask(String title, int totalWork) {
                this.title = title;
                this.total = totalWork;
                progress.onProgress(title, 0, totalWork);
            }

            @Override
            public void update(int completed) {
                progress.onProgress(title, completed, total);
            }

            @Override
            public void endTask() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void showDuration(boolean enabled) {
            }
        };
    }

    private static String normalizePattern(String path) {
        String p = path;
        while (p.startsWith("./")) p = p.substring(2);
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String[] splitConfigKey(String key) {
        return key.split("\\.");
    }

    private static String abbreviate(String id) {
        return id != null && id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static String shortenRef(String ref, String prefix) {
        return ref.startsWith(prefix) ? ref.substring(prefix.length()) : ref;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> T safe(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }
}
