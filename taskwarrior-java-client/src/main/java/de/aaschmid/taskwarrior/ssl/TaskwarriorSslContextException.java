package de.aaschmid.taskwarrior.ssl;

import javax.net.ssl.SSLContext;

import de.aaschmid.taskwarrior.TaskwarriorException;

/** Exception occurs if a problem with the {@link SSLContext} configuration / creation occurs. */
public class TaskwarriorSslContextException extends TaskwarriorException {

    private static final long serialVersionUID = 1L;

    public TaskwarriorSslContextException(Throwable cause, String message) {
        super(message, cause);
    }

    public TaskwarriorSslContextException(Throwable cause, String format, Object... args) {
        super(cause, format, args);
    }

    public TaskwarriorSslContextException(String message) {
        super(message);
    }

    public TaskwarriorSslContextException(String format, Object... args) {
        super(format, args);
    }
}
