package org.javacs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.Diagnostic;
import org.javacs.lsp.DiagnosticSeverity;
import org.javacs.lsp.DidChangeConfigurationParams;
import org.junit.Before;
import org.junit.Test;

public class ExtraCompilerArgsTest {
    private static final Path WORKSPACE_ROOT = Paths.get("src/test/examples/compiler-args-project").normalize();
    private static final Path FILE = WORKSPACE_ROOT.resolve("UsesPreviewStringTemplate.java").normalize();

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    @Before
    public void clearDiagnostics() {
        diagnostics.clear();
    }

    @Test
    public void reportsErrorWithoutExtraCompilerArgs() {
        var server = LanguageServerFixture.getJavaLanguageServer(WORKSPACE_ROOT, diagnostics::add);

        server.lint(List.of(FILE));

        assertThat(firstError(), notNullValue());
    }

    @Test
    public void appliesExtraCompilerArgsFromSettings() {
        var server = LanguageServerFixture.getJavaLanguageServer(WORKSPACE_ROOT, diagnostics::add);
        var change = new DidChangeConfigurationParams();
        var settings = new JsonObject();
        var java = new JsonObject();
        var args = new JsonArray();
        args.add("--enable-preview");
        args.add("-source");
        args.add("21");
        java.add("extraCompilerArgs", args);
        settings.add("java", java);
        change.settings = settings;

        server.didChangeConfiguration(change);
        server.lint(List.of(FILE));

        assertThat(firstError(), nullValue());
    }

    private Diagnostic firstError() {
        Diagnostic error = null;
        for (var diagnostic : diagnostics) {
            if (diagnostic.severity == DiagnosticSeverity.Error) {
                error = diagnostic;
                break;
            }
        }
        return error;
    }
}
