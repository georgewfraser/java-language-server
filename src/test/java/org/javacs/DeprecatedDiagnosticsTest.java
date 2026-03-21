package org.javacs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.Diagnostic;
import org.junit.Before;
import org.junit.Test;

public class DeprecatedDiagnosticsTest {
    private static final List<Diagnostic> diagnostics = new ArrayList<>();
    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer(diagnostics::add);

    @Before
    public void clearDiagnostics() {
        diagnostics.clear();
    }

    @Test
    public void marksDeprecatedWarnings() {
        server.lint(List.of(FindResource.path("org/javacs/example/DeprecationWarning.java")));

        Diagnostic warning = null;
        for (var diagnostic : diagnostics) {
            if ("compiler.warn.has.been.deprecated".equals(diagnostic.code)) {
                warning = diagnostic;
                break;
            }
        }

        assertThat(warning, notNullValue());
        assertThat(warning.code, equalTo("compiler.warn.has.been.deprecated"));
        assertThat(warning.tags, contains(2));
    }
}
