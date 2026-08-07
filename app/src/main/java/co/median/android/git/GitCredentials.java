package co.median.android.git;

/**
 * Credentials used for HTTPS Git operations (username + password/token) and for
 * SSH key passphrases. Android-free; serialized by the bridge layer.
 */
public class GitCredentials {
    public String username;
    public String password;

    public GitCredentials() {
    }

    public GitCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    public boolean isEmpty() {
        return (username == null || username.isEmpty()) && !hasPassword();
    }
}
