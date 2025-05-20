package org.javacs;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;
import java.util.Set;
import org.junit.Test;
import org.junit.Before;

/**
 * These tests are isolated because bugs caused by encoding issues could cause
 * phantom failures in other tests.
 */
public class FileEncodingTest {

    @Before
    public void resetSourcesBefore() {
        FileStore.reset();
    }

    @Test
    public void packageNameForNonUnicodeSource() {
        var encodingTestRoot = Paths.get("src/test/examples/encoding").normalize();

        // If an exception is thrown due to an unknown encoding it would
        // be here.
        FileStore.setWorkspaceRoots(Set.of(encodingTestRoot));

        var file = FindResource.path("/org/javacs/example/EncodingWindows1252.java");

        // Currently, non-unicode files are ignored. This test will change if
        // support is added in the future.
        assertThat(FileStore.suggestedPackageName(file), equalTo(""));
    }
}
