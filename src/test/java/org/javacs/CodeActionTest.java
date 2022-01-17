package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Before;
import org.junit.Test;

public class CodeActionTest {
    private static final List<Diagnostic> errors = new ArrayList<>();
    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer(errors::add);

    @Before
    public void clearErrors() {
        errors.clear();
    }

    @Test
    public void testCantConvertToStatement() {
        assertThat(quickFix("org/javacs/action/TestCantConvertToStatement.java"), empty());
    }

    @Test
    public void testConvertToStatement() {
        assertThat(quickFix("org/javacs/action/TestConvertToStatement.java"), contains("Convert to statement"));
    }

    @Test
    public void testConvertToBlock() {
        assertThat(quickFix("org/javacs/action/TestConvertToBlock.java"), contains("Convert to block"));
    }

    @Test
    public void testRemoveDeclaration() {
        assertThat(quickFix("org/javacs/action/TestRemoveDeclaration.java"), contains("Remove method"));
    }

    @Test
    public void testUnusedException() {
        assertThat(quickFix("org/javacs/action/TestUnusedException.java"), empty());
    }

    @Test
    public void testSuppressWarning() {
        assertThat(quickFix("org/javacs/action/TestSuppressWarning.java"), contains("Suppress 'unchecked' warning"));
    }

    @Test
    public void testAddThrows() {
        assertThat(quickFix("org/javacs/action/TestAddThrows.java"), contains("Add 'throws'"));
    }

    @Test
    public void testAddImport() {
        String[] expect = {
            "Import 'java.util.List'", "Import 'com.google.gson.Gson'", "Import 'com.sun.source.util.TreePathScanner'"
        };
        assertThat(quickFix("org/javacs/action/TestAddImport.java"), hasItems(expect));
    }

    @Test
    public void testAddImportInAnonymousClass() {
        String[] expect = {"Import 'java.util.List'"};
        assertThat(quickFix("org/javacs/action/TestAddImportAnonymousClass.java"), hasItems(expect));
    }

    @Test
    public void testRemoveNotThrown() {
        assertThat(quickFix("org/javacs/action/TestRemoveNotThrown.java"), contains("Remove 'IOException'"));
    }

    @Test
    public void testGenerateConstructor() {
        assertThat(quickFix("org/javacs/action/TestGenerateConstructor.java"), contains("Generate constructor"));
    }

    @Test
    public void testDontGenerateConstructor() {
        assertThat(
                quickFix("org/javacs/action/TestDontGenerateConstructor.java"), not(hasItem("Generate constructor")));
    }

    @Test
    public void testImplementAbstractMethods() {
        assertThat(
                quickFix("org/javacs/action/TestImplementAbstractMethods.java"), hasItem("Implement abstract methods"));
    }

    @Test
    public void testOverrideInheritedMethod() {
        assertThat(
                forCursor("org/javacs/action/TestOverrideInheritedMethod.java", 6, 1),
                hasItem("Override 'andThen' from java.util.function.Function"));
    }

    @Test
    public void testCreateMissingMethod() {
        assertThat(quickFix("org/javacs/action/TestCreateMissingMethod.java"), hasItem("Create missing method"));
    }

    private List<String> quickFix(String testFile) {
        var file = FindResource.path(testFile);
        server.lint(List.of(file));
        var params = new CodeActionParams();
        params.textDocument = new TextDocumentIdentifier(file.toUri());
        params.context.diagnostics = errors;
        var actions = server.codeAction(params);
        return extractTitles(actions);
    }

    private List<String> forCursor(String testFile, int line, int column) {
        var file = FindResource.path(testFile);
        var cursor = new Position(line - 1, column - 1);
        server.lint(List.of(file));
        var params = new CodeActionParams();
        params.textDocument = new TextDocumentIdentifier(file.toUri());
        params.range = new Range(cursor, cursor);
        var actions = server.codeAction(params);
        return extractTitles(actions);
    }

    private List<String> extractTitles(List<CodeAction> actions) {
        var quickFix = new ArrayList<String>();
        for (var a : actions) {
            quickFix.add(a.title);
        }
        return quickFix;
    }
}
