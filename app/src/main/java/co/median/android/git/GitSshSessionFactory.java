package co.median.android.git;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * SshSessionFactory pointing JGit's SSH transport at the app's key directory
 * (e.g. {@code filesDir/git/ssh}), with encrypted-key passphrase requests
 * delegated to the bridge's credential callback.
 *
 * Keys discovered in the SSH directory follow the standard naming convention
 * (id_rsa, id_ecdsa, id_ed25519, ...). Password-based SSH auth is handled by
 * the CredentialsProvider supplied to the transport command.
 */
public class GitSshSessionFactory extends SshdSessionFactory {

    private final File sshDir;
    private final GitCredentialRequest creds;

    public GitSshSessionFactory(File sshDir, GitCredentialRequest creds) {
        this.sshDir = sshDir;
        this.creds = creds;
        setHomeDirectory(sshDir);
        setSshDirectory(sshDir);
    }

    @Override
    protected KeyPasswordProvider createKeyPasswordProvider(CredentialsProvider cp) {
        return new KeyPasswordProvider() {
            @Override
            public char[] getPassphrase(URIish uri, int attempt) throws IOException {
                if (attempt > 1 || creds == null) return null;
                return creds.requestKeyPassphrase(uri.toString());
            }

            @Override
            public void setAttempts(int attempts) {
            }

            @Override
            public boolean keyLoaded(URIish uri, int attempt, Exception error)
                    throws IOException, GeneralSecurityException {
                return true;
            }
        };
    }
}
