package de.aaschmid.taskwarrior.message;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TaskwarriorMessage {

    public static final String HEADER_TYPE = "type";
    public static final String HEADER_PROTOCOL = "protocol";
    public static final String HEADER_CLIENT = "client";

    private final Map<String, String> headers;
    private final Optional<String> payload;

    public TaskwarriorMessage(Map<String, String> headers, Optional<String> payload) {
        this.headers = new HashMap<>(Objects.requireNonNull(headers, "'headers' must not be null."));
        this.payload = payload;
    }

    public TaskwarriorMessage(Map<String, String> headers, String payload) {
        this(headers, Optional.of(requireNonNull(payload, "'payload' must not be null.")));
    }

    public TaskwarriorMessage(Map<String, String> headers) {
        this(headers, Optional.empty());
    }

    public Map<String, String> getHeaders() {
        return unmodifiableMap(headers);
    }

    public Optional<String> getPayload() {
        return payload;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((headers == null) ? 0 : headers.hashCode());
        result = prime * result + ((payload == null) ? 0 : payload.hashCode());
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
        TaskwarriorMessage other = (TaskwarriorMessage) obj;
        if (headers == null) {
            if (other.headers != null) {
                return false;
            }
        } else if (!headers.equals(other.headers)) {
            return false;
        }
        if (payload == null) {
            if (other.payload != null) {
                return false;
            }
        } else if (!payload.equals(other.payload)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "TaskwarriorMessage [headers=" + headers + ", payload=" + payload + "]";
    }
}
