package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.*;
import java.util.*;
import org.junit.*;

public class JavaCompilerServiceTest {
    static {
        Main.setRootFormat();
    }

    private JavaCompilerService compiler =
            new JavaCompilerService(
                    Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Docs.NOT_FOUND);

    static Path simpleProjectSrc() {
        return Paths.get("src/test/examples/simple-project").normalize();
    }

    @Before
    public void setWorkspaceRoot() {
        FileStore.setWorkspaceRoots(Set.of(simpleProjectSrc()));
    }
}
