package de.aaschmid.taskwarrior.config;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.UUID;

/** {@link TaskwarriorConfiguration} based on a specified properties file */
public class TaskwarriorPropertiesConfiguration implements TaskwarriorConfiguration {

    private static final String PROPERTY_TASKWARRIOR_SSL_CERT_CA_FILE = "taskwarrior.ssl.cert.ca.file";
    private static final String PROPERTY_TASKWARRIOR_SSL_CERT_KEY_FILE = "taskwarrior.ssl.cert.key.file";
    private static final String PROPERTY_TASKWARRIOR_SSL_PUBLIC_KEY_FILE = "taskwarrior.ssl.private.key.file";
    private static final String PROPERTY_TASKWARRIOR_SERVER_HOST = "taskwarrior.server.host";
    private static final String PROPERTY_TASKWARRIOR_SERVER_PORT = "taskwarrior.server.port";
    private static final String PROPERTY_TASKWARRIOR_AUTH_KEY = "taskwarrior.auth.key";
    private static final String PROPERTY_TASKWARRIOR_AUTH_USER = "taskwarrior.auth.user";
    private static final String PROPERTY_TASKWARRIOR_AUTH_ORGANISATION = "taskwarrior.auth.organisation";

    private final URL propertiesUrl;
    private final Properties taskwarriorProperties;

    public TaskwarriorPropertiesConfiguration(URL propertiesUrl) {
        this.propertiesUrl = requireNonNull(propertiesUrl, "'propertiesUrl' must not be null.");

        this.taskwarriorProperties = new Properties();
        try {
            taskwarriorProperties.load(propertiesUrl.openStream());

        } catch (IOException e) {
            throw new TaskwarriorConfigurationException("Cannot read 'taskwarrior.properties'. Check permissions and content.", e);
        }
    }

    @Override
    public File getCaCertFile() {
        return getExistingFileFromProperty(PROPERTY_TASKWARRIOR_SSL_CERT_CA_FILE, "CA certificate");
    }

    @Override
    public File getPrivateKeyCertFile() {
        return getExistingFileFromProperty(PROPERTY_TASKWARRIOR_SSL_CERT_KEY_FILE, "private key certificate");
    }

    @Override
    public File getPrivateKeyFile() {
        return getExistingFileFromProperty(PROPERTY_TASKWARRIOR_SSL_PUBLIC_KEY_FILE, "private key");
    }

    @Override
    public InetAddress getServerHost() {
        String host = getExistingProperty(PROPERTY_TASKWARRIOR_SERVER_HOST);
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new TaskwarriorConfigurationException(e, "Cannot resolve host address '%s': %s", host, e.getMessage());
        }
    }

    @Override
    public int getServerPort() {
        String key = PROPERTY_TASKWARRIOR_SERVER_PORT;
        String value = getExistingProperty(key);

        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            throw new TaskwarriorConfigurationException(e, "Property '%s' is not a parseable integer but was '%s'.", key, value);
        }
    }

    @Override
    public TaskwarriorAuthentication getAuthentication() {
        String org = getExistingProperty(PROPERTY_TASKWARRIOR_AUTH_ORGANISATION);
        String user = getExistingProperty(PROPERTY_TASKWARRIOR_AUTH_USER);
        UUID key = getExistingAuthenticationKey(PROPERTY_TASKWARRIOR_AUTH_KEY);
        return new TaskwarriorAuthentication(org, user, key);
    }

    private String getExistingProperty(String key) {
        String value = taskwarriorProperties.getProperty(key);
        if (value == null) {
            throw new TaskwarriorConfigurationException("Could not find property with key '%s' in '%s'.", key, propertiesUrl);
        }
        return value;
    }

    private File getExistingFileFromProperty(String key, String fileErrorText) {
        String property = getExistingProperty(key);

        File result = new File(property);
        if (!result.exists()) {
            throw new TaskwarriorConfigurationException("Given %s file does not exist, was '%s'.", fileErrorText, property);
        }
        return result;
    }

    private UUID getExistingAuthenticationKey(String keyProperty) {
        String keyValue = getExistingProperty(keyProperty);
        try {
            return UUID.fromString(keyValue);
        } catch (IllegalArgumentException e) {
            throw new TaskwarriorConfigurationException(e, "Property '%s' is not a parsable UUID but was '%s'.", keyProperty, keyValue);
        }
    }
}
