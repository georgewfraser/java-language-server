package org.javacs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.javacs.lsp.*;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression tests for bugs caused by external (out-of-process) file deletions — i.e. files deleted
 * on disk without the language server receiving a didChangeWatchedFiles notification.
 *
 * <p>Bug 1: Deleting a file externally doesn't clear it from javac's symbol table.
 *
 * <p>Bug 2: Moving a class to a different package externally — compiler still thinks it's in the
 * old package, and FileStore still maps the old path.
 *
 * <p>Bug 3: External delete leaves the file in FileStore.javaSources(), causing a crash when the
 * server later tries to compile it via the needsAdditionalSources() path.
 */
public class ExternalDeleteTest {

    /**
     * Bug 1: When a file is deleted externally (no LSP notification), recompiling a file that
     * depended on the deleted class should report "cannot find symbol". If the bug is present,
     * javac will silently use its cached symbol table and produce no error.
     */
    @Test
    public void externalDeleteShouldClearFileFromJavac() throws IOException {
        var xPath = FindResource.path("/org/javacs/example/ExtDeleteX.java");
        var yPath = FindResource.path("/org/javacs/example/ExtDeleteY.java");

        List<String> lintErrors = new ArrayList<>();
        var server =
                LanguageServerFixture.getJavaLanguageServer(
                        diagnostic -> lintErrors.add(diagnostic.message));

        try {
            Files.writeString(
                    xPath,
                    "package org.javacs.example;\n"
                            + "class ExtDeleteX {\n"
                            + "    static void test() {\n"
                            + "        ExtDeleteY.test();\n"
                            + "    }\n"
                            + "}\n",
                    StandardOpenOption.CREATE_NEW);
            Files.writeString(
                    yPath,
                    "package org.javacs.example;\n"
                            + "class ExtDeleteY {\n"
                            + "    static int test() { return 1; }\n"
                            + "}\n",
                    StandardOpenOption.CREATE_NEW);

            // Force an initial compile of both files so javac caches the symbols.
            server.compiler().compile(xPath, yPath).close();

            // Delete Y externally — no didChangeWatchedFiles notification.
            Files.delete(yPath);

            // Re-lint X. If javac's symbol table was properly cleared, this should
            // produce a "cannot find symbol" error for ExtDeleteY.
            server.lint(List.of(xPath));

            assertThat(
                    "externally deleted file should be cleared from javac — X should report"
                            + " 'cannot find symbol'",
                    lintErrors,
                    hasItem(containsString("cannot find symbol")));
        } finally {
            Files.deleteIfExists(xPath);
            Files.deleteIfExists(yPath);
        }
    }

    /**
     * Bug 3: When a file is deleted externally (no LSP notification), it remains in
     * FileStore.javaSources() permanently. FileStore.list(packageName) reads packageName directly
     * from the in-memory map without touching disk, so the stale entry is never evicted.
     *
     * <p>This test directly asserts that the stale path is gone from FileStore after an external
     * delete. If the bug is present, FileStore.all() will still contain the deleted path.
     */
    @Test
    public void externalDeleteShouldRemoveFileFromFileStore() throws IOException {
        var stalePath = FindResource.path("/org/javacs/example/ExtDeleteStale.java");

        var server = LanguageServerFixture.getJavaLanguageServer();

        try {
            Files.writeString(
                    stalePath,
                    "package org.javacs.example;\n" + "class ExtDeleteStale {}\n",
                    StandardOpenOption.CREATE_NEW);

            // Force FileStore to register the file by compiling it.
            server.compiler().compile(stalePath).close();

            assertThat(
                    "file should be in FileStore before deletion",
                    FileStore.all(),
                    hasItem(stalePath));

            // Delete externally — no LSP notification.
            Files.delete(stalePath);

            // FileStore.contains() should no longer return true for the deleted path.
            assertThat(
                    "externally deleted file should be removed from FileStore",
                    FileStore.contains(stalePath),
                    is(false));
        } finally {
            Files.deleteIfExists(stalePath);
        }
    }

    /**
     * Bug 2: When a class is moved to a different package externally (no LSP notification), the
     * compiler still thinks it's in the old package and FileStore still maps the old path.
     *
     * <p>A move is a delete of the old path + create at the new path. Neither FileStore nor javac
     * are updated, so both stale-state bugs apply simultaneously:
     *
     * <ul>
     *   <li>The old path stays in FileStore.all() (same root cause as bug 3).
     *   <li>javac still resolves the class name to the old package (same root cause as bug 1).
     * </ul>
     *
     * <p>After the move, recompiling a file that imports the class by its new package should
     * succeed (or at least report "cannot find symbol" for the old name). The old path must be gone
     * from FileStore and the new path must be present.
     */
    @Test
    public void externalMoveShouldUpdateFileStoreAndJavac() throws IOException {
        // Old location: org.javacs.example package
        var oldPath = FindResource.path("/org/javacs/example/ExtMoveClass.java");
        // New location: org.javacs.other package (different directory)
        var newPath = FindResource.path("/org/javacs/other/ExtMoveClass.java");
        // A file that depends on the class via its new package location
        var callerPath = FindResource.path("/org/javacs/example/ExtMoveCaller.java");

        List<String> lintErrors = new ArrayList<>();
        var server =
                LanguageServerFixture.getJavaLanguageServer(
                        diagnostic -> lintErrors.add(diagnostic.message));

        try {
            Files.writeString(
                    oldPath,
                    "package org.javacs.example;\n"
                            + "public class ExtMoveClass {\n"
                            + "    public static void hello() {}\n"
                            + "}\n",
                    StandardOpenOption.CREATE_NEW);
            Files.writeString(
                    callerPath,
                    "package org.javacs.example;\n"
                            + "class ExtMoveCaller {\n"
                            + "    void run() {\n"
                            + "        ExtMoveClass.hello();\n"
                            + "    }\n"
                            + "}\n",
                    StandardOpenOption.CREATE_NEW);

            // Compile both so javac and FileStore know about the old location.
            server.compiler().compile(oldPath, callerPath).close();

            // Move the file externally — rename on disk, no LSP notification.
            // The class declaration still says "package org.javacs.example" but it now lives
            // under org/javacs/other/, simulating what a file manager or git would do.
            Files.move(oldPath, newPath);

            // --- FileStore assertions ---
            // Old path must be gone from FileStore.
            assertThat(
                    "old path should be removed from FileStore after external move",
                    FileStore.contains(oldPath),
                    is(false));
            // New path must be visible in FileStore.
            assertThat(
                    "new path should appear in FileStore after external move",
                    FileStore.contains(newPath),
                    is(true));

            // --- javac assertion ---
            // Re-lint the caller. Since ExtMoveClass is no longer at the old path, javac
            // should now report "cannot find symbol". If the bug is present, it silently
            // succeeds using the cached symbol.
            server.lint(List.of(callerPath));
            assertThat(
                    "compiler should report cannot find symbol after class is moved away",
                    lintErrors,
                    hasItem(containsString("cannot find symbol")));
        } finally {
            Files.deleteIfExists(oldPath);
            Files.deleteIfExists(newPath);
            Files.deleteIfExists(callerPath);
        }
    }
}
