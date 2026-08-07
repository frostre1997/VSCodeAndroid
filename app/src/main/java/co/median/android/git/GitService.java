package co.median.android.git;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
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
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GitService {

    private static final String EMPTY_TREE_ID = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
    private static final Lock REPO_LOCK = new ReentrantLock();

    // ------------------------------------------------------------------
    // SSH configuration
    // ------------------------------------------------------------------

    private static File sshDir;
    private static GitCredentialRequest globalCredentialRequest;

    public static void setSshConfig(File sshDir, GitCredentialRequest request) {
        GitService.sshDir = sshDir;
        GitService.globalCredentialRequest = request;
        // Set the SSH session factory if needed – depends on your SshKeyManager
    }

    // ------------------------------------------------------------------
    // Repository helpers
    // ------------------------------------------------------------------

    public static boolean isRepository(File dir) {
        if (dir == null) return false;
        File gitDir = new File(dir, ".git");
        return gitDir.isDirectory();
    }

    public static File findRepositoryRoot(File dir) {
        if (dir == null) return null;
        File current = dir.getAbsoluteFile();
        while (current != null) {
            if (isRepository(current)) return current;
            current = current.getParentFile();
        }
        return null;
    }

    public static File defaultCloneDir(File baseDir, String url) {
        String name = url.replaceFirst("^.*/([^/]+?)(\\.git)?$", "$1");
        if (name.isEmpty()) name = "repo";
        return new File(baseDir, name);
    }

    // ------------------------------------------------------------------
    // Open repository
    // ------------------------------------------------------------------

    private static Repository open(File dir) throws GitServiceException {
        try {
            return new FileRepositoryBuilder()
                    .setGitDir(new File(dir, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to open repository at " + dir, e);
        }
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    public static List<GitFileStatus> status(File dir) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            Status status = git.status().call();
            List<GitFileStatus> result = new ArrayList<>();
            for (String path : status.getModified()) {
                result.add(new GitFileStatus(path, 'M', ' ', false, false));
            }
            for (String path : status.getAdded()) {
                result.add(new GitFileStatus(path, 'A', ' ', false, false));
            }
            for (String path : status.getRemoved()) {
                result.add(new GitFileStatus(path, 'D', ' ', false, false));
            }
            for (String path : status.getChanged()) {
                result.add(new GitFileStatus(path, 'C', ' ', false, false));
            }
            for (String path : status.getUntracked()) {
                result.add(new GitFileStatus(path, ' ', '?', true, false));
            }
            for (String path : status.getConflicting()) {
                result.add(new GitFileStatus(path, ' ', ' ', false, true));
            }
            return result;
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to get status", e);
        }
    }

    // ------------------------------------------------------------------
    // Stage / unstage
    // ------------------------------------------------------------------

    public static void stage(File dir, List<String> paths, boolean all) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            if (all) {
                git.add().addFilepattern(".").call();
            } else if (paths != null && !paths.isEmpty()) {
                for (String p : paths) git.add().addFilepattern(p).call();
            } else {
                throw new GitServiceException("No paths specified for stage");
            }
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to stage", e);
        }
    }

    public static void unstage(File dir, List<String> paths, boolean all) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            if (all) {
                git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HEAD).call();
            } else if (paths != null && !paths.isEmpty()) {
                for (String p : paths) git.reset().addPath(p).call();
            } else {
                throw new GitServiceException("No paths specified for unstage");
            }
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to unstage", e);
        }
    }

    // ------------------------------------------------------------------
    // Commit
    // ------------------------------------------------------------------

    public static void commit(File dir, String message, boolean amend,
                              String authorName, String authorEmail) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            org.eclipse.jgit.api.CommitCommand cmd = git.commit()
                    .setMessage(message)
                    .setAmend(amend);
            if (authorName != null && authorEmail != null) {
                cmd.setAuthor(authorName, authorEmail);
            }
            cmd.call();
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to commit", e);
        }
    }

    // ------------------------------------------------------------------
    // Push
    // ------------------------------------------------------------------

    public static void push(File dir, String remote, String branch,
                            boolean force, GitCredentialRequest request, GitProgress progress) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            PushCommand cmd = git.push()
                    .setCredentialsProvider(wrapCredentials(request))
                    .setProgressMonitor(wrapProgress(progress));
            if (remote != null && !remote.isEmpty()) cmd.setRemote(remote);
            if (branch != null && !branch.isEmpty()) {
                cmd.setRefSpecs(new RefSpec("refs/heads/" + branch));
            }
            if (force) cmd.setForce(true);
            cmd.call();
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to push", e);
        }
    }

    // ------------------------------------------------------------------
    // Pull
    // ------------------------------------------------------------------

    public static void pull(File dir, String remote, String branch,
                            boolean rebase, GitCredentialRequest request, GitProgress progress) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            PullCommand cmd = git.pull()
                    .setCredentialsProvider(wrapCredentials(request))
                    .setProgressMonitor(wrapProgress(progress));
            if (rebase) cmd.setRebase(true);
            if (remote != null && !remote.isEmpty()) cmd.setRemote(remote);
            if (branch != null && !branch.isEmpty()) cmd.setRemoteBranchName(branch);
            PullResult result = cmd.call();
            if (!result.isSuccessful()) {
                throw new GitServiceException("Pull failed: " + result.toString());
            }
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to pull", e);
        }
    }

    // ------------------------------------------------------------------
    // Fetch
    // ------------------------------------------------------------------

    public static void fetch(File dir, String remote,
                             GitCredentialRequest request, GitProgress progress) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            FetchCommand cmd = git.fetch()
                    .setCredentialsProvider(wrapCredentials(request))
                    .setProgressMonitor(wrapProgress(progress));
            if (remote != null && !remote.isEmpty()) cmd.setRemote(remote);
            cmd.call();
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to fetch", e);
        }
    }

    // ------------------------------------------------------------------
    // Clone
    // ------------------------------------------------------------------

    public static GitRepoInfo clone(String url, File destDir, boolean recursive,
                                    GitCredentialRequest request, GitProgress progress) throws GitServiceException {
        try {
            CloneCommand cmd = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(destDir)
                    .setCredentialsProvider(wrapCredentials(request))
                    .setProgressMonitor(wrapProgress(progress));
            if (recursive) cmd.setCloneSubmodules(true);
            try (Git git = cmd.call()) {
                return getRepoInfo(git.getRepository().getDirectory().getParentFile());
            }
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to clone " + url, e);
        }
    }

    // ------------------------------------------------------------------
    // Init
    // ------------------------------------------------------------------

    public static void init(File dir) throws GitServiceException {
        try {
            Git.init().setDirectory(dir).setInitialBranch("main").call().close();
        } catch (GitAPIException e) {
            throw GitServiceException.wrap("Failed to init repository at " + dir, e);
        }
    }

    // ------------------------------------------------------------------
    // Branches
    // ------------------------------------------------------------------

    public static List<GitBranchInfo> listBranches(File dir) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            List<GitBranchInfo> result = new ArrayList<>();
            String currentBranch = repo.getBranch();
            for (Ref ref : refs) {
                GitBranchInfo info = new GitBranchInfo();
                info.fullName = ref.getName();
                info.name = Repository.shortenRefName(ref.getName());
                info.current = info.name.equals(currentBranch) ||
                        (currentBranch.equals("HEAD") && info.name.equals(repo.getBranch()));
                info.remote = ref.getName().startsWith(Constants.R_REMOTES);
                info.remoteName = info.remote ? ref.getName().replaceFirst("^refs/remotes/", "") : null;
                result.add(info);
            }
            // sort local first, then remote
            result.sort((a, b) -> {
                if (a.remote != b.remote) return Boolean.compare(a.remote, b.remote);
                return a.name.compareToIgnoreCase(b.name);
            });
            return result;
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to list branches", e);
        }
    }

    public static GitBranchInfo createBranch(File dir, String name, String startPoint) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            CreateBranchCommand cmd = git.branchCreate().setName(name);
            if (startPoint != null && !startPoint.isEmpty()) cmd.setStartPoint(startPoint);
            cmd.call();
            return findBranch(repo, Constants.R_HEADS + name);
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to create branch " + name, e);
        }
    }

    public static GitBranchInfo checkout(File dir, String name, boolean createNew, String startPoint)
            throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            org.eclipse.jgit.api.CheckoutCommand cmd = git.checkout().setName(name);
            if (createNew) {
                cmd.setCreateBranch(true);
                if (startPoint != null && !startPoint.isEmpty()) cmd.setStartPoint(startPoint);
            }
            cmd.call();
            return findBranch(repo, Constants.R_HEADS + name);
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to checkout " + name, e);
        }
    }

    public static void deleteBranch(File dir, String name, boolean force) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            git.branchDelete().setBranchNames(name).setForce(force).call();
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to delete branch " + name, e);
        }
    }

    public static void checkoutCommit(File dir, String commit) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            git.checkout().setName(commit).call();
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to checkout commit " + commit, e);
        }
    }

    private static GitBranchInfo findBranch(Repository repo, String fullName) throws GitServiceException {
        try {
            List<GitBranchInfo> branches = listBranches(repo.getDirectory());
            return branches.stream().filter(b -> fullName.equals(b.fullName)).findFirst()
                    .orElseThrow(() -> new GitServiceException("Branch not found: " + fullName));
        } catch (Exception e) {
            throw GitServiceException.wrap("Failed to find branch", e);
        }
    }

    // ------------------------------------------------------------------
    // Diff / Show
    // ------------------------------------------------------------------

    public static List<GitDiffFile> diff(File dir, String path, boolean staged) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            DiffCommand cmd = git.diff()
                    .setShowNameAndStatusOnly(false)
                    .setCached(staged)
                    .setDetectRenames(true);
            if (path != null && !path.isEmpty()) {
                cmd.setPathFilter(PathFilter.create(path));
            }
            List<DiffEntry> entries = cmd.call();
            return formatDiffEntries(repo, entries);
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to diff", e);
        }
    }

    public static List<GitDiffFile> show(File dir, String commit) throws GitServiceException {
        try (Repository repo = open(dir); RevWalk walk = new RevWalk(repo)) {
            ObjectId id = repo.resolve(commit);
            if (id == null) throw new GitServiceException("Invalid commit: " + commit);
            RevCommit commitObj = walk.parseCommit(id);
            RevCommit parent = commitObj.getParentCount() > 0 ? walk.parseCommit(commitObj.getParent(0)) : null;

            List<DiffEntry> entries;
            try (DiffFormatter diffFormatter = new DiffFormatter(new ByteArrayOutputStream())) {
                diffFormatter.setRepository(repo);
                diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                diffFormatter.setDetectRenames(true);
                if (parent == null) {
                    // Compare with empty tree
                    ObjectId emptyTreeId = repo.resolve(EMPTY_TREE_ID);
                    if (emptyTreeId == null) {
                        // Fallback: use TreeWalk with empty tree
                        try (TreeWalk tw = new TreeWalk(repo)) {
                            tw.addTree(new org.eclipse.jgit.treewalk.EmptyTreeIterator());
                            tw.addTree(commitObj.getTree());
                            entries = diffFormatter.scan(tw);
                        }
                    } else {
                        try (RevWalk emptyWalk = new RevWalk(repo)) {
                            entries = diffFormatter.scan(emptyWalk.parseTree(emptyTreeId), commitObj.getTree());
                        }
                    }
                } else {
                    entries = diffFormatter.scan(parent.getTree(), commitObj.getTree());
                }
            }
            return formatDiffEntries(repo, entries);
        } catch (IOException | GitAPIException e) {
            throw GitServiceException.wrap("Failed to show commit", e);
        }
    }

    private static List<GitDiffFile> formatDiffEntries(Repository repo, List<DiffEntry> entries) throws IOException {
        List<GitDiffFile> result = new ArrayList<>();
        for (DiffEntry entry : entries) {
            GitDiffFile df = new GitDiffFile();
            df.path = entry.getNewPath();
            df.oldPath = entry.getOldPath();
            df.changeType = entry.getChangeType().name();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(baos)) {
                formatter.setRepository(repo);
                formatter.setDiffComparator(RawTextComparator.DEFAULT);
                formatter.setDetectRenames(true);
                formatter.format(entry);
                String text = baos.toString(StandardCharsets.UTF_8.name());
                df.diff = text;
                df.additions = countLines(text, '+');
                df.deletions = countLines(text, '-');
            }
            result.add(df);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Log
    // ------------------------------------------------------------------

    public static List<GitCommitInfo> log(File dir, int maxCount) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            LogCommand cmd = git.log();
            cmd.setMaxCount(maxCount > 0 ? maxCount : 100);
            List<GitCommitInfo> result = new ArrayList<>();
            for (RevCommit commit : cmd.call()) {
                GitCommitInfo info = new GitCommitInfo();
                info.id = commit.getId().name();
                info.shortId = info.id.substring(0, Math.min(info.id.length(), 7));
                info.authorName = commit.getAuthorIdent().getName();
                info.authorEmail = commit.getAuthorIdent().getEmailAddress();
                info.message = commit.getFullMessage().trim();
                info.subject = commit.getShortMessage();
                info.commitTime = commit.getCommitTime();
                info.parentCount = commit.getParentCount();
                result.add(info);
            }
            return result;
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to get log", e);
        }
    }

    // ------------------------------------------------------------------
    // Remotes
    // ------------------------------------------------------------------

    public static List<String> listRemotes(File dir) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            RemoteListCommand cmd = git.remoteList();
            List<RemoteConfig> remotes = cmd.call();
            List<String> result = new ArrayList<>();
            for (RemoteConfig rc : remotes) result.add(rc.getName());
            return result;
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to list remotes", e);
        }
    }

    // ------------------------------------------------------------------
    // Ahead / Behind
    // ------------------------------------------------------------------

    public static int[] aheadBehind(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            String branch = repo.getBranch();
            if (branch == null || branch.equals("HEAD")) {
                // detached HEAD – cannot compute ahead/behind
                return new int[]{0, 0};
            }
            String upstream = repo.getConfig().getString(
                    ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE);
            if (upstream == null) return new int[]{0, 0};
            String remote = repo.getConfig().getString(
                    ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE);
            if (remote == null) return new int[]{0, 0};
            String remoteRef = Constants.R_REMOTES + remote + "/" + upstream.replace("refs/heads/", "");
            ObjectId head = repo.resolve(Constants.HEAD);
            ObjectId up = repo.resolve(remoteRef);
            if (head == null || up == null) return new int[]{0, 0};

            long behind = 0, ahead = 0;
            for (RevCommit c : Git.wrap(repo).log().addRange(head, up).call()) behind++;
            for (RevCommit c : Git.wrap(repo).log().addRange(up, head).call()) ahead++;
            return new int[]{(int) ahead, (int) behind};
        } catch (IOException | GitAPIException e) {
            throw GitServiceException.wrap("Failed to compute ahead/behind", e);
        }
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    public static String getConfig(File dir, String key) throws GitServiceException {
        try (Repository repo = open(dir)) {
            StoredConfig config = repo.getConfig();
            return config.getString(null, null, key);
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to get config", e);
        }
    }

    public static void setConfig(File dir, String key, String value) throws GitServiceException {
        try (Repository repo = open(dir)) {
            StoredConfig config = repo.getConfig();
            config.setString(null, null, key, value);
            config.save();
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to set config", e);
        }
    }

    // ------------------------------------------------------------------
    // Discard changes
    // ------------------------------------------------------------------

    public static void discardChanges(File dir, List<String> paths) throws GitServiceException {
        try (Repository repo = open(dir); Git git = new Git(repo)) {
            if (paths == null || paths.isEmpty()) {
                git.checkout().setAllPaths(true).setForce(true).call();
            } else {
                for (String p : paths) git.checkout().addPath(p).setForce(true).call();
            }
        } catch (GitAPIException | IOException e) {
            throw GitServiceException.wrap("Failed to discard changes", e);
        }
    }

    // ------------------------------------------------------------------
    // Repo info
    // ------------------------------------------------------------------

    public static GitRepoInfo getRepoInfo(File dir) throws GitServiceException {
        try (Repository repo = open(dir)) {
            GitRepoInfo info = new GitRepoInfo();
            info.directory = repo.getDirectory().getParentFile();
            info.currentBranch = repo.getBranch();
            info.detachedHead = info.currentBranch.equals("HEAD");
            info.headId = repo.resolve(Constants.HEAD) != null ? repo.resolve(Constants.HEAD).name() : null;
            info.upstream = repo.getConfig().getString(
                    ConfigConstants.CONFIG_BRANCH_SECTION, info.currentBranch, ConfigConstants.CONFIG_KEY_MERGE);
            info.branches = listBranches(dir);
            return info;
        } catch (IOException e) {
            throw GitServiceException.wrap("Failed to get repo info", e);
        }
    }

    // ------------------------------------------------------------------
    // Progress and credential wrappers
    // ------------------------------------------------------------------

    private static ProgressMonitor wrapProgress(GitProgress progress) {
        return new ProgressMonitor() {
            @Override
            public void start(int totalTasks) { /* optional */ }
            @Override
            public void beginTask(String title, int totalWork) {
                if (progress != null) progress.onProgress(title, 0, totalWork);
            }
            @Override
            public void update(int completed) {
                // not used
            }
            @Override
            public void endTask() { /* optional */ }
            @Override
            public boolean isCancelled() { return false; }
        };
    }

    private static CredentialsProvider wrapCredentials(GitCredentialRequest request) {
        if (request == null) return null;
        return new CredentialsProvider() {
            @Override
            public boolean isInteractive() { return false; }
            @Override
            public boolean supports(String... credentialTypes) {
                for (String ct : credentialTypes) {
                    if (ct.startsWith("UsernamePasswordCredential")) return true;
                }
                return false;
            }
            @Override
            public boolean get(URIish uri, Map<String, String> map) throws org.eclipse.jgit.errors.UnsupportedCredentialItem {
                String url = uri.toString();
                GitCredentials creds = request.requestCredentials(url);
                if (creds == null) return false;
                map.put("Username", creds.username);
                map.put("Password", creds.password);
                return true;
            }
        };
    }

    // ------------------------------------------------------------------
    // Helper: count lines
    // ------------------------------------------------------------------

    private static int countLines(String text, char prefix) {
        if (text == null) return 0;
        int count = 0;
        for (String line : text.split("\n")) {
            if (!line.isEmpty() && line.charAt(0) == prefix) count++;
        }
        return count;
    }
}
