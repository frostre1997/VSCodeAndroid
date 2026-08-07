package co.median.android.git;

/**
 * Request callback the bridge layer implements so GitService can ask for
 * credentials when a remote operation needs them. Implementations normally
 * consult an encrypted credential store and/or show a prompt.
 */
public interface GitCredentialRequest {

    /**
     * Return credentials (username + password/token) for an HTTPS remote URL,
     * or {@code null} to continue anonymously (which will fail if the remote
     * requires auth).
     */
    GitCredentials requestCredentials(String url);

    /**
     * Return the passphrase for an encrypted SSH key, or {@code null} if the
     * key is unencrypted / unavailable.
     */
    char[] requestKeyPassphrase(String url);
}
