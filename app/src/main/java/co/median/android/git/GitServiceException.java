package co.median.android.git;

/**
 * Unified checked exception for GitService failures. Carries a user-facing
 * message plus the original cause.
 */
public class GitServiceException extends Exception {

    public GitServiceException(String message) {
        super(message);
    }

    public GitServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public static GitServiceException wrap(String message, Throwable cause) {
        String detail = cause.getMessage() != null && !cause.getMessage().isEmpty()
                ? cause.getMessage() : cause.getClass().getSimpleName();
        return new GitServiceException(message + ": " + detail, cause);
    }
}
