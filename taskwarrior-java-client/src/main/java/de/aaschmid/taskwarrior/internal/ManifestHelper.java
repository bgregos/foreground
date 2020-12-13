package de.aaschmid.taskwarrior.internal;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.jar.Manifest;

public class ManifestHelper {

    public static Optional<URL> getResourceUrlForClass(Class<?> clazz) {
        String className = clazz.getSimpleName() + ".class";
        return Optional.ofNullable(clazz.getResource(className));
    }

    public static Optional<String> getJarUrlForClass(Class<?> clazz) {
        // @formatter:off
        return getResourceUrlForClass(clazz)
                .map(URL::toString)
                .filter(p -> p.startsWith("jar:"))
                .map(p -> p.substring(0, p.lastIndexOf("!") + 1));
        // @formatter:on
    }

    public static Optional<String> getManifestAttributeValue(String jarUrl, String manifestAttributeKey) {
        try {
            Manifest manifest = new Manifest(new URL(jarUrl + "/META-INF/MANIFEST.MF").openStream());
            return Optional.of(manifest.getMainAttributes().getValue(manifestAttributeKey));

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static String getImplementationVersionFromManifest(String fallbackVersion) {
        // @formatter:off
        return Optional.of(ManifestHelper.class)
                .flatMap(c -> getJarUrlForClass(c))
                .flatMap(u -> getManifestAttributeValue(u, "Implementation-Version"))
                .orElse(fallbackVersion);
        // @formatter:on
    }
}
