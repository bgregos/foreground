package de.aaschmid.taskwarrior.config;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

/** Immutable class containing the relevant taskwarrior authentication information. */
public class TaskwarriorAuthentication {
    private final String organistion;
    private final String user;
    private final UUID key;

    public TaskwarriorAuthentication(String organistion, String user, UUID key) {
        this.organistion = requireNonNull(organistion, "'organisation' must not be null.");
        this.user = requireNonNull(user, "'user' must not be null.");
        this.key = requireNonNull(key, "'key' must not be null.");
    }

    public String getOrganistion() {
        return organistion;
    }

    public String getUser() {
        return user;
    }

    public UUID getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((organistion == null) ? 0 : organistion.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TaskwarriorAuthentication other = (TaskwarriorAuthentication) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        if (organistion == null) {
            if (other.organistion != null) {
                return false;
            }
        } else if (!organistion.equals(other.organistion)) {
            return false;
        }
        if (user == null) {
            if (other.user != null) {
                return false;
            }
        } else if (!user.equals(other.user)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "TaskwarriorAuthentication [organistion=" + organistion + ", user=" + user + ", key=" + key + "]";
    }
}