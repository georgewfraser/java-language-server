package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class FileStoreTest {

    @Before
    public void setWorkspaceRoot() {
        FileStore.setWorkspaceRoots(Set.of(LanguageServerFixture.DEFAULT_WORKSPACE_ROOT));
    }

    @Test
    public void packageName() {
        var file = FindResource.path("/org/javacs/example/Goto.java");
        assertThat(FileStore.suggestedPackageName(file), equalTo("org.javacs.example"));
    }

    @Test
    public void missingFile() {
        var file = FindResource.path("/org/javacs/example/NoSuchFile.java");
        assertThat(FileStore.packageName(file), nullValue());
        assertThat(FileStore.modified(file), nullValue());
    }
}
