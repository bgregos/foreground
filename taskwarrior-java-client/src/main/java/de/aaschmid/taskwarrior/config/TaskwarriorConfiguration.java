package de.aaschmid.taskwarrior.config;

import java.io.File;
import java.net.InetAddress;

public interface TaskwarriorConfiguration {
    File getCaCertFile();

    File getPrivateKeyCertFile();

    File getPrivateKeyFile();

    InetAddress getServerHost();

    int getServerPort();

    TaskwarriorAuthentication getAuthentication();
}