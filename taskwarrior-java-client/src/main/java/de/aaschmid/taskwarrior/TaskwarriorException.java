package de.aaschmid.taskwarrior;

/** Exception occurs if any taskwarrior problem occurs. */
public class TaskwarriorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskwarriorException(Throwable cause, String message) {
        super(message, cause);
    }

    public TaskwarriorException(Throwable cause, String format, Object... args) {
        this(cause, String.format(format, args));
    }

    public TaskwarriorException(String message) {
        super(message);
    }

    public TaskwarriorException(String format, Object... args) {
        this(String.format(format, args));
    }
}
