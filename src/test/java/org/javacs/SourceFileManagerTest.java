package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import javax.tools.*;
import org.junit.Before;
import org.junit.Test;

public class SourceFileManagerTest {
    static final Path src = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve("src");
    static final Path classes = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve("target/classes");
    final SourceFileManager sourceFileManager = createSourceFileManager();
    final StandardJavaFileManager standardFileManager = createDelegateFileManager();

    private static SourceFileManager createSourceFileManager() {
        var fileManager = new SourceFileManager();
        try {
            fileManager.setLocation(StandardLocation.SOURCE_PATH, List.of(src.toFile()));
            fileManager.setLocation(StandardLocation.CLASS_PATH, List.of(classes.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileManager;
    }

    private static StandardJavaFileManager createDelegateFileManager() {
        var compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
        var fileManager =
                compiler.getStandardFileManager(
                        err -> LOG.severe(err.getMessage(null)), null, Charset.defaultCharset());
        try {
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(src));
            fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(classes));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileManager;
    }

    @Before
    public void setWorkspaceRoot() {
        FileStore.setWorkspaceRoots(Set.of(LanguageServerFixture.DEFAULT_WORKSPACE_ROOT));
    }

    @Test
    public void binaryNameOfPackagePrivateClass() throws IOException {
        var standardJava =
                standardFileManager.getJavaFileForInput(
                        StandardLocation.SOURCE_PATH, "com.example.PackagePrivate", JavaFileObject.Kind.SOURCE);
        var standardClass =
                standardFileManager.getJavaFileForInput(
                        StandardLocation.CLASS_PATH, "com.example.PackagePrivate", JavaFileObject.Kind.CLASS);
        var sourceJava =
                sourceFileManager.getJavaFileForInput(
                        StandardLocation.SOURCE_PATH, "com.example.PackagePrivate", JavaFileObject.Kind.SOURCE);
        var sourceClass =
                sourceFileManager.getJavaFileForInput(
                        StandardLocation.CLASS_PATH, "com.example.PackagePrivate", JavaFileObject.Kind.CLASS);
        var standardJavaName = standardFileManager.inferBinaryName(StandardLocation.SOURCE_PATH, standardJava);
        var standardClassName = standardFileManager.inferBinaryName(StandardLocation.CLASS_PATH, standardClass);
        var sourceJavaName = sourceFileManager.inferBinaryName(StandardLocation.SOURCE_PATH, sourceJava);
        var sourceClassName = sourceFileManager.inferBinaryName(StandardLocation.CLASS_PATH, sourceClass);
        assertThat(standardClassName, equalTo(standardJavaName));
        assertThat(sourceJavaName, equalTo(standardJavaName));
        assertThat(sourceClassName, equalTo(standardJavaName));
    }

    @Test
    public void javaUtilList() throws IOException {
        var file =
                sourceFileManager.getJavaFileForInput(
                        StandardLocation.PLATFORM_CLASS_PATH, "java.util.List", JavaFileObject.Kind.CLASS);
        assertThat("Found java.util.List in platform classpath", file, notNullValue());

        var header = ClassHeader.of(file.openInputStream());
        assertTrue(header.isInterface);
        assertTrue(header.isAbstract);
        assertTrue(header.isPublic);
    }

    private static final Logger LOG = Logger.getLogger("main");
}
