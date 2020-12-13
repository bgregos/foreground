package de.aaschmid.taskwarrior.config;

import de.aaschmid.taskwarrior.TaskwarriorException;

/** Exception occurs if any configuration problem occurs. */
public class TaskwarriorConfigurationException extends TaskwarriorException {

    private static final long serialVersionUID = 1L;

    public TaskwarriorConfigurationException(Throwable cause, String message) {
        super(cause, message);
    }

    public TaskwarriorConfigurationException(Throwable cause, String format, Object... args) {
        super(cause, format, args);
    }

    public TaskwarriorConfigurationException(String message) {
        super(message);
    }

    public TaskwarriorConfigurationException(String format, Object... args) {
        super(format, args);
    }
}
