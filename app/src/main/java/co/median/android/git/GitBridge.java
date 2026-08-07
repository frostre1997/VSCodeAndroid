package co.median.android.git;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.median.android.MainActivity;

/**
 * {@code @JavascriptInterface} exposed on the WebView as {@code GitBridge}.
 * JavaScript calls {@code GitBridge.invoke(command, paramsJson, callbackId)};
 * the result is delivered asynchronously to the global function
 * {@code window[callbackId]} with a JSON object {@code {success, error, data}}.
 *
 * All Git work runs on a single background thread so operations are
 * serialized. Long-running operations emit progress events and every
 * state-changing operation emits a {@code status-changed} event.
 */
@SuppressLint("JavascriptInterface")
public class GitBridge {

    public static final String NAME = "GitBridge";
    private static final String TAG = GitBridge.class.getName();
    private static final String PREFS_SETTINGS = "vscode_git_settings";
    private static final String KEY_ACTIVE_REPO = "activeRepo";

    private static final String GIT_UI_URL = "file:///android_asset/git/git.html";
    private static final String VSCODE_URL = "https://vscode.dev/";

    private final MainActivity activity;
    private final GitCredentialStore store;
    private final GitStorage storage;
    private final SshKeyManager sshKeys;
    private final SharedPreferences settings;
    private final Handler uiHandler;
    private final ExecutorService executor;

    private volatile String activeRepo;
    private volatile String pendingCredentialUrl;

    public GitBridge(MainActivity activity) {
        this.activity = activity;
        Context ctx = activity.getApplicationContext();
        this.settings = ctx.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        this.storage = new GitStorage(ctx);
        try {
            this.store = new GitCredentialStore(ctx);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize credential store", e);
        }
        this.sshKeys = new SshKeyManager(storage.getSshDir(), store);
        this.uiHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.activeRepo = settings.getString(KEY_ACTIVE_REPO, null);

        GitService.setSshConfig(storage.getSshDir(), new BridgeCredentialRequest());
    }

    public String getGitUiUrl() {
        return GIT_UI_URL;
    }

    public String getActiveRepo() {
        return activeRepo;
    }

    public File getReposDir() {
        return storage.getReposDir();
    }

    // ------------------------------------------------------------------
    // JS entry point
    // ------------------------------------------------------------------

    @JavascriptInterface
    public void invoke(final String command, final String paramsJson, final String callbackId) {
        executor.execute(() -> {
            JSONObject result = new JSONObject();
            try {
                JSONObject params = paramsJson == null || paramsJson.isEmpty()
                        ? new JSONObject() : new JSONObject(paramsJson);
                result.put("data", dispatch(command, params));
                result.put("success", true);
            } catch (GitServiceException e) {
                Log.d(TAG, command + " failed", e);
                result.put("success", false);
                result.put("error", e.getMessage());
            } catch (JSONException e) {
                Log.d(TAG, command + " bad params", e);
                result.put("success", false);
                result.put("error", "Invalid parameters: " + e.getMessage());
            } catch (Exception e) {
                Log.d(TAG, command + " error", e);
                result.put("success", false);
                result.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            deliverResult(callbackId, result);
        });
    }

    private void deliverResult(String callbackId, JSONObject result) {
        if (callbackId == null || callbackId.isEmpty()) return;
        uiHandler.post(() -> activity.runJavascript(
                "window[" + jsWrap(callbackId) + "] && window[" + jsWrap(callbackId) + "](" + result.toString() + ");"));
    }

    private void emitEvent(String name, JSONObject data) {
        uiHandler.post(() -> activity.runJavascript(
                "if (window._medianGitEmit) window._medianGitEmit(" + jsWrap(name) + ", " + data.toString() + ");"));
    }

    // ------------------------------------------------------------------
    // Command dispatch
    // ------------------------------------------------------------------

    private Object dispatch(String command, JSONObject p) throws Exception {
        switch (command) {
            case "getInfo":
                return getInfo(p);
            case "findRepo":
                return findRepo(p);
            case "listRepos":
                return listRepos(p);
            case "setActiveRepo":
                return setActiveRepo(p);
            case "openRepoDir":
                return openRepoDir();
            case "status":
                return status(p);
            case "stage":
                GitService.stage(requireRepo(), pathList(p, "paths"), p.optBoolean("all", false));
                emitStatusChanged();
                return ok();
            case "unstage":
                GitService.unstage(requireRepo(), pathList(p, "paths"), p.optBoolean("all", false));
                emitStatusChanged();
                return ok();
            case "commit": {
                GitService.commit(requireRepo(), p.getString("message"), p.optBoolean("amend", false),
                        p.optString("authorName", null), p.optString("authorEmail", null));
                emitStatusChanged();
                return ok();
            }
            case "push": {
                GitService.push(requireRepo(), optNull(p, "remote"), optNull(p, "branch"),
                        p.optBoolean("force", false), new BridgeCredentialRequest(), new BridgeProgress());
                emitStatusChanged();
                return ok();
            }
            case "pull": {
                GitService.pull(requireRepo(), optNull(p, "remote"), optNull(p, "branch"),
                        p.optBoolean("rebase", false), new BridgeCredentialRequest(), new BridgeProgress());
                emitStatusChanged();
                return ok();
            }
            case "fetch": {
                GitService.fetch(requireRepo(), optNull(p, "remote"), new BridgeCredentialRequest(), new BridgeProgress());
                emitStatusChanged();
                return ok();
            }
            case "clone": {
                String url = p.getString("url");
                File dest = p.has("dir") && !p.isNull("dir")
                        ? new File(p.getString("dir")) : GitService.defaultCloneDir(storage.getReposDir(), url);
                GitRepoInfo info = GitService.clone(url, dest, p.optBoolean("recursive", false),
                        new BridgeCredentialRequest(), new BridgeProgress());
                if (p.optBoolean("setActive", true)) setActiveRepo(dest.getAbsolutePath());
                emitStatusChanged();
                return repoToJson(info);
            }
            case "init": {
                File dir = new File(p.getString("dir"));
                GitService.init(dir);
                setActiveRepo(dir.getAbsolutePath());
                emitStatusChanged();
                return repoToJson(GitService.getRepoInfo(dir));
            }
            case "branches":
                return branchesJson(GitService.listBranches(requireRepo()));
            case "createBranch": {
                GitBranchInfo info = GitService.createBranch(requireRepo(), p.getString("name"),
                        optNull(p, "startPoint"));
                emitStatusChanged();
                return branchToJson(info);
            }
            case "checkout": {
                GitBranchInfo info = GitService.checkout(requireRepo(), p.getString("name"),
                        p.optBoolean("createNew", false), optNull(p, "startPoint"));
                emitStatusChanged();
                return branchToJson(info);
            }
            case "deleteBranch": {
                GitService.deleteBranch(requireRepo(), p.getString("name"), p.optBoolean("force", false));
                emitStatusChanged();
                return ok();
            }
            case "checkoutCommit": {
                GitService.checkoutCommit(requireRepo(), p.getString("commit"));
                emitStatusChanged();
                return ok();
            }
            case "diff":
                return diffJson(GitService.diff(requireRepo(), optNull(p, "path"), p.optBoolean("staged", false)));
            case "show":
                return diffJson(GitService.show(requireRepo(), p.getString("commit")));
            case "log":
                return logJson(GitService.log(requireRepo(), p.optInt("max", 50)));
            case "remotes":
                return new JSONArray(GitService.listRemotes(requireRepo()));
            case "aheadBehind": {
                int[] ab = GitService.aheadBehind(requireRepo());
                JSONObject o = new JSONObject();
                o.put("ahead", ab[0]);
                o.put("behind", ab[1]);
                return o;
            }
            case "configGet":
                return GitService.getConfig(requireRepo(), p.getString("key"));
            case "configSet": {
                GitService.setConfig(requireRepo(), p.getString("key"), p.getString("value"));
                return ok();
            }
            case "discard":
                GitService.discardChanges(requireRepo(), pathList(p, "paths"));
                emitStatusChanged();
                return ok();
            case "getIdentity": {
                JSONObject o = new JSONObject();
                o.put("name", store.get(GitCredentialStore.KEY_IDENTITY_NAME));
                o.put("email", store.get(GitCredentialStore.KEY_IDENTITY_EMAIL));
                return o;
            }
            case "setIdentity":
                store.put(GitCredentialStore.KEY_IDENTITY_NAME, p.optString("name", ""));
                store.put(GitCredentialStore.KEY_IDENTITY_EMAIL, p.optString("email", ""));
                return ok();
            case "saveSshPassphrase":
                store.put("ssh.id_rsa.passphrase", p.optString("passphrase", ""));
                return ok();
            case "saveCredentials":
                saveCredentials(p);
                return ok();
            case "getCredentials": {
                JSONObject o = new JSONObject();
                String user = store.get(credKey("user", p.optString("url")));
                o.put("username", user);
                o.put("hasPassword", store.get(credKey("pass", p.optString("url"))) != null);
                return o;
            }
            case "deleteCredentials":
                store.remove(credKey("user", p.optString("url")));
                store.remove(credKey("pass", p.optString("url")));
                return ok();
            case "listCredentials": {
                JSONArray arr = new JSONArray();
                for (Map.Entry<String, String> e : store.all().entrySet()) {
                    JSONObject o = new JSONObject();
                    o.put("key", e.getKey());
                    o.put("value", e.getValue());
                    arr.put(o);
                }
                return arr;
            }
            case "listSshKeys":
                return sshKeysJson(sshKeys.listKeys());
            case "generateSshKey": {
                SshKeyManager.SshKeyInfo info = sshKeys.generateKey(
                        p.optString("comment", "android"), p.optString("passphrase", ""));
                return sshKeyToJson(info);
            }
            case "getSshPublicKey": {
                String name = p.optString("name", "id_rsa");
                String content = sshKeys.publicKeyContents(name);
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("content", content);
                return o;
            }
            case "deleteSshKey":
                sshKeys.deleteKey(p.getString("name"));
                return ok();
            case "openVscode":
                uiHandler.post(() -> activity.runOnUiThread(() -> activity.loadUrl(VSCODE_URL)));
                return ok();
            default:
                throw new GitServiceException("Unknown Git command: " + command);
        }
    }

    // ------------------------------------------------------------------
    // Command implementations
    // ------------------------------------------------------------------

    private JSONObject getInfo(JSONObject p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("reposDir", storage.getReposDir().getAbsolutePath());
        o.put("sshDir", storage.getSshDir().getAbsolutePath());
        o.put("activeRepo", activeRepo);
        o.put("reposDirExists", storage.getReposDir().exists());
        if (activeRepo != null && GitService.isRepository(new File(activeRepo))) {
            o.put("repo", repoToJson(GitService.getRepoInfo(new File(activeRepo))));
        }
        return o;
    }

    private JSONObject findRepo(JSONObject p) throws Exception {
        String dir = p.optString("dir");
        File root = GitService.findRepositoryRoot(new File(dir));
        JSONObject o = new JSONObject();
        if (root != null) {
            o.put("root", root.getAbsolutePath());
            o.put("repo", repoToJson(GitService.getRepoInfo(root)));
        } else {
            o.put("root", JSONObject.NULL);
        }
        return o;
    }

    private JSONObject listRepos(JSONObject p) throws Exception {
        String dir = p.optString("dir", storage.getReposDir().getAbsolutePath());
        File base = new File(dir);
        JSONArray arr = new JSONArray();
        if (base.isDirectory()) {
            File[] children = base.listFiles();
            if (children != null) {
                List<File> sorted = new ArrayList<>();
                Collections.addAll(sorted, children);
                sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File child : sorted) {
                    if (child.isDirectory() && GitService.isRepository(child)) {
                        JSONObject o = new JSONObject();
                        o.put("path", child.getAbsolutePath());
                        o.put("name", child.getName());
                        arr.put(o);
                    }
                }
            }
        }
        JSONObject out = new JSONObject();
        out.put("dir", base.getAbsolutePath());
        out.put("repos", arr);
        return out;
    }

    private JSONObject setActiveRepo(JSONObject p) throws Exception {
        String dir = p.getString("dir");
        if (!GitService.isRepository(new File(dir))) {
            throw new GitServiceException("Not a Git repository: " + dir);
        }
        setActiveRepo(dir);
        return repoToJson(GitService.getRepoInfo(new File(dir)));
    }

    private JSONObject openRepoDir() {
        uiHandler.post(activity::openGitDirectoryPicker);
        return ok();
    }

    public void onDirectoryPicked(String dir) {
        executor.execute(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("path", dir);
                File f = new File(dir);
                if (GitService.isRepository(f)) {
                    setActiveRepo(dir);
                    o.put("isRepo", true);
                    o.put("repo", repoToJson(GitService.getRepoInfo(f)));
                    emitStatusChanged();
                } else {
                    o.put("isRepo", false);
                }
                emitEvent("directory-picked", o);
            } catch (Exception e) {
                Log.d(TAG, "onDirectoryPicked error", e);
            }
        });
    }

    private JSONObject status(JSONObject p) throws Exception {
        File repo = requireRepo();
        GitRepoInfo info = GitService.getRepoInfo(repo);
        List<GitFileStatus> files = GitService.status(repo);
        int[] ab = GitService.aheadBehind(repo);
        List<String> remotes = GitService.listRemotes(repo);

        JSONObject o = repoToJson(info);
        o.put("files", statusJson(files));
        o.put("ahead", ab[0]);
        o.put("behind", ab[1]);
        o.put("remotes", new JSONArray(remotes));
        return o;
    }

    private void saveCredentials(JSONObject p) {
        String url = p.optString("url");
        if (url.isEmpty()) return;
        String username = p.optString("username", "");
        String password = p.optString("password", "");
        if (username.isEmpty()) {
            store.remove(credKey("user", url));
        } else {
            store.put(credKey("user", url), username);
        }
        if (password.isEmpty()) {
            store.remove(credKey("pass", url));
        } else {
            store.put(credKey("pass", url), password);
        }
        pendingCredentialUrl = null;
        emitStatusChanged();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private File requireRepo() throws GitServiceException {
        if (activeRepo == null || !GitService.isRepository(new File(activeRepo))) {
            throw new GitServiceException("No repository open. Open a repository first.");
        }
        return new File(activeRepo);
    }

    private void setActiveRepo(String dir) {
        this.activeRepo = dir;
        settings.edit().putString(KEY_ACTIVE_REPO, dir).apply();
    }

    private void emitStatusChanged() {
        JSONObject o = new JSONObject();
        o.put("activeRepo", activeRepo);
        emitEvent("status-changed", o);
    }

    private String credKey(String kind, String url) {
        return GitCredentialStore.remoteKey("remote." + kind, url == null ? "" : url);
    }

    private List<String> pathList(JSONObject p, String key) throws JSONException {
        if (!p.has(key) || p.isNull(key)) return null;
        JSONArray arr = p.getJSONArray(key);
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) paths.add(arr.getString(i));
        return paths;
    }

    private String optNull(JSONObject p, String key) {
        if (!p.has(key) || p.isNull(key)) return null;
        String v = p.optString(key);
        return v.isEmpty() ? null : v;
    }

    private JSONObject ok() {
        return new JSONObject();
    }

    private static String jsWrap(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ------------------------------------------------------------------
    // JSON serialization
    // ------------------------------------------------------------------

    static JSONObject repoToJson(GitRepoInfo info) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("directory", info.directory != null ? info.directory.getAbsolutePath() : null);
        o.put("currentBranch", info.currentBranch);
        o.put("detachedHead", info.detachedHead);
        o.put("headId", info.headId);
        o.put("upstream", info.upstream);
        o.put("branches", branchesJson(info.branches));
        return o;
    }

    static JSONArray statusJson(List<GitFileStatus> files) throws JSONException {
        JSONArray arr = new JSONArray();
        for (GitFileStatus f : files) {
            JSONObject o = new JSONObject();
            o.put("path", f.path);
            o.put("indexStatus", f.indexStatus);
            o.put("worktreeStatus", f.worktreeStatus);
            o.put("untracked", f.untracked);
            o.put("conflicted", f.conflicted);
            arr.put(o);
        }
        return arr;
    }

    static JSONArray branchesJson(List<GitBranchInfo> branches) throws JSONException {
        JSONArray arr = new JSONArray();
        for (GitBranchInfo b : branches) arr.put(branchToJson(b));
        return arr;
    }

    static JSONObject branchToJson(GitBranchInfo b) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", b.name);
        o.put("fullName", b.fullName);
        o.put("remoteName", b.remoteName);
        o.put("tracking", b.tracking);
        o.put("current", b.current);
        o.put("remote", b.remote);
        return o;
    }

    static JSONArray logJson(List<GitCommitInfo> commits) throws JSONException {
        JSONArray arr = new JSONArray();
        for (GitCommitInfo c : commits) {
            JSONObject o = new JSONObject();
            o.put("id", c.id);
            o.put("shortId", c.shortId);
            o.put("authorName", c.authorName);
            o.put("authorEmail", c.authorEmail);
            o.put("message", c.message);
            o.put("subject", c.subject);
            o.put("commitTime", c.commitTime);
            o.put("parentCount", c.parentCount);
            arr.put(o);
        }
        return arr;
    }

    static JSONArray diffJson(List<GitDiffFile> diffs) throws JSONException {
        JSONArray arr = new JSONArray();
        for (GitDiffFile d : diffs) {
            JSONObject o = new JSONObject();
            o.put("path", d.path);
            o.put("oldPath", d.oldPath);
            o.put("changeType", d.changeType);
            o.put("additions", d.additions);
            o.put("deletions", d.deletions);
            o.put("diff", d.diff);
            arr.put(o);
        }
        return arr;
    }

    static JSONArray sshKeysJson(List<SshKeyManager.SshKeyInfo> keys) throws JSONException {
        JSONArray arr = new JSONArray();
        for (SshKeyManager.SshKeyInfo k : keys) arr.put(sshKeyToJson(k));
        return arr;
    }

    static JSONObject sshKeyToJson(SshKeyManager.SshKeyInfo k) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", k.name);
        o.put("privateKeyPath", k.privateKeyPath);
        o.put("publicKeyPath", k.publicKeyPath);
        o.put("fingerprint", k.fingerprint);
        o.put("comment", k.comment);
        return o;
    }

    // ------------------------------------------------------------------
    // Credential + progress callbacks
    // ------------------------------------------------------------------

    private class BridgeCredentialRequest implements GitCredentialRequest {
        @Override
        public GitCredentials requestCredentials(String url) {
            if (url == null) return null;
            String username = store.get(credKey("user", url));
            String password = store.get(credKey("pass", url));
            if (password == null || password.isEmpty()) {
                pendingCredentialUrl = url;
                uiHandler.post(() -> {
                    JSONObject o = new JSONObject();
                    o.put("url", url);
                    emitEvent("credentials-required", o);
                });
                return null;
            }
            return new GitCredentials(username, password);
        }

        @Override
        public char[] requestKeyPassphrase(String url) {
            String passphrase = store.get("ssh.id_rsa.passphrase");
            if (passphrase == null) {
                pendingCredentialUrl = url;
                uiHandler.post(() -> {
                    JSONObject o = new JSONObject();
                    o.put("url", url);
                    emitEvent("ssh-passphrase-required", o);
                });
                return null;
            }
            return passphrase.toCharArray();
        }
    }

    private class BridgeProgress implements GitProgress {
        @Override
        public void onProgress(String task, int work, int total) {
            JSONObject o = new JSONObject();
            o.put("task", task);
            o.put("work", work);
            o.put("total", total);
            emitEvent("progress", o);
        }

        @Override
        public void onMessage(String message) {
            JSONObject o = new JSONObject();
            o.put("message", message);
            emitEvent("progress", o);
        }
    }
}
