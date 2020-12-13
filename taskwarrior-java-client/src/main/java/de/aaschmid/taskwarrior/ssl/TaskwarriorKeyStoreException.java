package de.aaschmid.taskwarrior.ssl;

import java.security.KeyStore;

import de.aaschmid.taskwarrior.TaskwarriorException;

/** Exception occurs if a problem with the {@link KeyStore} configuration / creation occurs. */
public class TaskwarriorKeyStoreException extends TaskwarriorException {

    private static final long serialVersionUID = 1L;

    public TaskwarriorKeyStoreException(Throwable cause, String message) {
        super(message, cause);
    }

    public TaskwarriorKeyStoreException(Throwable cause, String format, Object... args) {
        super(cause, format, args);
    }

    public TaskwarriorKeyStoreException(String message) {
        super(message);
    }

    public TaskwarriorKeyStoreException(String format, Object... args) {
        super(format, args);
    }
}
