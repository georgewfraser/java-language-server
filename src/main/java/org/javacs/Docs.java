package org.javacs;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import javax.tools.*;

public class Docs {

    /** File manager with source-path + platform sources, which we will use to look up individual source files */
    final SourceFileManager fileManager = new SourceFileManager();

    Docs(Set<Path> docPath) {
        var srcZipPath = srcZip();
        // Path to source .jars + src.zip
        var sourcePath = new ArrayList<Path>(docPath);
        if (srcZipPath != NOT_FOUND) {
            sourcePath.add(srcZipPath);
        }
        try {
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, sourcePath);
            if (srcZipPath != NOT_FOUND) {
                fileManager.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, Set.of(srcZipPath));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static final Path NOT_FOUND = Paths.get("");
    private static Path cacheSrcZip;

    private static Path srcZip() {
        if (cacheSrcZip == null) {
            cacheSrcZip = findSrcZip();
        }
        if (cacheSrcZip == NOT_FOUND) {
            return NOT_FOUND;
        }
        try {
            var fs = FileSystems.newFileSystem(cacheSrcZip, Docs.class.getClassLoader());
            return fs.getPath("/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Path findSrcZip() {
        var javaHome = JavaHomeHelper.javaHome();
        String[] locations = {
            "lib/src.zip", "src.zip", "libexec/openjdk.jdk/Contents/Home/lib/src.zip"
        };
        for (var rel : locations) {
            var abs = javaHome.resolve(rel);
            if (Files.exists(abs)) {
                LOG.info("Found " + abs);
                return abs;
            }
        }
        LOG.warning("Couldn't find src.zip in " + javaHome);
        return NOT_FOUND;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
