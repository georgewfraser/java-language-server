package org.javacs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.List;
import org.junit.Test;

public class JavaCompilerServiceTest {
    @Test
    public void compileDoesNotReuseStaleInMemorySourceForSamePathAndNewVersion() {
        var compiler = LanguageServerFixture.getCompilerProvider();
        var file = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve("src/org/javacs/example/CacheKeyRegression.java");
        var firstModified = SourceFileObject.now();
        var secondModified = SourceFileObject.now();
        var first =
                new SourceFileObject(
                        file,
                        "package org.javacs.example; class CacheKeyRegression { int first; }",
                        firstModified);
        var second =
                new SourceFileObject(
                        file,
                        "package org.javacs.example; class CacheKeyRegression { int second; }",
                        secondModified);

        try (var compile = compiler.compile(List.of(first))) {
            assertThat(compile.root().toString(), containsString("first"));
        }

        try (var compile = compiler.compile(List.of(second))) {
            assertThat(compile.root().toString(), containsString("second"));
            assertThat(compile.root().toString(), not(containsString("first")));
        }
    }
}
