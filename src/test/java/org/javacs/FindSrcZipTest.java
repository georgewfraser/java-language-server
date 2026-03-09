package org.javacs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class FindSrcZipTest {
    @Test
    public void testFindSrcZip() {
        // This test is not run in the CI pipeline, but it can be run locally to check if the
        // src.zip file is found correctly.
        Path srcZip = Docs.findSrcZip(Docs.NOT_FOUND);
        assertThat(srcZip, not(equalTo(Docs.NOT_FOUND)));
        assertTrue(Files.exists(srcZip));
    }
}
