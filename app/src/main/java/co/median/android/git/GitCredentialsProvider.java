package co.median.android.git;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Non-interactive CredentialsProvider that satisfies JGit's username/password
 * requests from the bridge's credential callback. Works for both HTTPS and
 * password-based SSH authentication. Unknown credential items (e.g. yes/no
 * prompts) are declined so the operation fails with a clear message instead of
 * silently auto-accepting something.
 */
public class GitCredentialsProvider extends CredentialsProvider {

    private final GitCredentialRequest request;

    public GitCredentialsProvider(GitCredentialRequest request) {
        this.request = request;
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        GitCredentials c = request.requestCredentials(uri.toString());
        String username = c != null ? c.username : null;
        String password = c != null ? c.password : null;
        if (password == null) return false;

        boolean filled = false;
        for (CredentialItem item : items) {
            if (item instanceof CredentialItem.Username) {
                ((CredentialItem.Username) item).setValue(username != null ? username : "git");
                filled = true;
            } else if (item instanceof CredentialItem.Password) {
                ((CredentialItem.Password) item).setValue(password.toCharArray());
                filled = true;
            } else if (item instanceof CredentialItem.StringType) {
                ((CredentialItem.StringType) item).setValue(username != null ? username : "");
                filled = true;
            } else {
                return false;
            }
        }
        return filled;
    }
}
