[![Build Status](https://travis-ci.org/aaschmid/taskwarrior-java-client.png?branch=master)](https://travis-ci.org/aaschmid/taskwarrior-java-client)
[![codebeat badge](https://codebeat.co/badges/90f3d360-88bb-4040-b8b6-2e3e684f11f4)](https://codebeat.co/projects/github-com-aaschmid-taskwarrior-java-client-master)
[![License](https://img.shields.io/github/license/aaschmid/taskwarrior-java-client.svg)](https://github.com/aaschmid/taskwarrior-java-client/blob/master/LICENSE.TXT)
[![Issues](https://img.shields.io/github/issues/aaschmid/taskwarrior-java-client.svg)](https://github.com/aaschmid/taskwarrior-java-client/issues)

taskwarrior-java-client
=======================

#### Table of Contents
* [What is it](#what-is-it)
* [Motivation and distinction](#motivation-and-distinction)
* [Requirements](#requirements)
* [Download](#download)
* [Usage example](#usage-example)
* [Release notes](#release-notes)


What is it
----------

A Java client to communicate with a [taskwarrior][] server (= [taskd](https://taskwarrior.org/docs/taskserver/why.html)).

[taskwarrior]: https://taskwarrior.org/


Motivation and distinction
--------------------------

The current taskwarrior Android app does not satisfy my requirements. Therefore I created this client library to integrate it into my prefered task app.
And I also want to share it with everybody who will love to use it.


Requirements
-----------

* JDK 8


Download
--------

Currently there is no released version available but feel free to clone / fork and build it yourself. If you would love to see this on
[Maven Central](http://search.maven.org/) feel free to create an issue.


Usage example
-------------

For example using it with [Java](https://www.java.com/):


```java
import static de.aaschmid.taskwarrior.message.TaskwarriorMessage.*;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import de.aaschmid.taskwarrior.config.*;
import de.aaschmid.taskwarrior.internal.ManifestHelper;
import de.aaschmid.taskwarrior.message.TaskwarriorMessage;

public class Taskwarrior {

    private static final URL PROPERTIES_TASKWARRIOR = Taskwarrior.class.getResource("/taskwarrior.properties");

    public static void main(String[] args) throws Exception {
        if (PROPERTIES_TASKWARRIOR == null) {
            throw new IllegalStateException(
                    "No 'taskwarrior.properties' found on Classpath. Create it by copy and rename 'taskwarrior.properties.template'. Also fill in proper values.");
        }
        TaskwarriorConfiguration config = new TaskwarriorPropertiesConfiguration(PROPERTIES_TASKWARRIOR);

        TaskwarriorClient client = new TaskwarriorClient(config);

        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_TYPE, "statistics");
        headers.put(HEADER_PROTOCOL, "v1");
        headers.put(HEADER_CLIENT, "taskwarrior-java-client " + ManifestHelper.getImplementationVersionFromManifest("local-dev"));

        TaskwarriorMessage response = client.sendAndReceive(new TaskwarriorMessage(headers));
        System.out.println(response);
    }
}
```

Used `taskwarrior.properties` can be created by copying and adjusting
[`src/main/resources/taskwarrior.properties.template`](https://github.com/aaschmid/taskwarrior-java-client/tree/master/src/main/resources/taskwarrior.properties.template).


Release notes
-------------

Currently there are no releases available, see [Release Notes](/aaschmid/taskwarrior-java-client/releases)
