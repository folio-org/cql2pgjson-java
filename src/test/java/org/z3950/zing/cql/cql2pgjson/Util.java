package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Util {
    private Util() throws InstantiationException {
        throw new InstantiationException("Utility class");
    }

    /**
     * Return the resource from the filePath as a String.
     * @param filePath  path to resource
     * @return resource as String
     * @throws RuntimeException if loading fails
     */
    public static String getResource(String filePath) {
        try {
            URI uri = Thread.currentThread().getContextClassLoader().getResource(filePath).toURI();
            return new String(Files.readAllBytes(Paths.get(uri)));
        } catch (IOException|URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
