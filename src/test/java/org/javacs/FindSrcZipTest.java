package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class FindSrcZipTest {
    @Test
    public void testFindSrcZip() {
        // This test is not run in the CI pipeline, but it can be run locally to check if the src.zip file is found correctly.
        Path srcZip = Docs.findSrcZip();
        assertThat(srcZip, not(equalTo(Docs.NOT_FOUND)));
        assertTrue(Files.exists(srcZip));
    }
}