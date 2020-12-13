package de.aaschmid.taskwarrior;

/** Exception occurs if a problem within {@link TaskwarriorClient} occurs. */
public class TaskwarriorClientException extends TaskwarriorException {

    private static final long serialVersionUID = 1L;

    public TaskwarriorClientException(Throwable cause, String message) {
        super(message, cause);
    }

    public TaskwarriorClientException(Throwable cause, String format, Object... args) {
        super(cause, format, args);
    }

    public TaskwarriorClientException(String message) {
        super(message);
    }

    public TaskwarriorClientException(String format, Object... args) {
        super(format, args);
    }
}
