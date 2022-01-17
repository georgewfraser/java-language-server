package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Before;
import org.junit.Test;

public class WarningsTest {
    private static List<String> errors = new ArrayList<>();

    protected static final JavaLanguageServer server =
            LanguageServerFixture.getJavaLanguageServer(WarningsTest::onError);

    private static void onError(Diagnostic error) {
        var string = String.format("%s(%d)", error.code, error.range.start.line + 1);
        errors.add(string);
    }

    @Before
    public void setup() {
        errors.clear();
    }

    @Test
    public void wrongType() {
        var file = FindResource.path("org/javacs/err/WrongType.java");
        server.lint(List.of(file));
        assertThat(errors, hasItem("compiler.err.prob.found.req(5)"));
    }

    @Test
    public void clearErrorIncrementally() {
        var file = FindResource.path("org/javacs/err/ClearErrorIncrementally.java");
        open(file);
        server.lint(List.of(file));
        assertThat(errors, containsInAnyOrder("compiler.err.prob.found.req(5)", "unused_local(5)"));
        // Change 1 to "1"
        var newContents =
                "package org.javacs.err;\n\npublic class ClearErrorIncrementally {\n    void test() {\n        String x = \"1\";\n    }\n}";
        edit(file, newContents);
        errors.clear();
        server.lint(List.of(file));
        assertThat(errors, contains("unused_local(5)"));
        // Delete line `String x = "1";`
        newContents =
                "package org.javacs.err;\n\npublic class ClearErrorIncrementally {\n    void test() {\n        }\n}";
        edit(file, newContents);
        errors.clear();
        server.lint(List.of(file));
        assertThat(errors, empty());
    }

    private static int editVersion = 1;

    private void open(Path file) {
        var open = new DidOpenTextDocumentParams();
        open.textDocument.uri = file.toUri();
        open.textDocument.text = FileStore.contents(file);
        open.textDocument.version = editVersion++;
        open.textDocument.languageId = "java";
        server.didOpenTextDocument(open);
    }

    private void edit(Path file, String contents) {
        var change = new DidChangeTextDocumentParams();
        change.textDocument.uri = file.toUri();
        change.textDocument.version = editVersion++;
        var evt = new TextDocumentContentChangeEvent();
        evt.text = contents;
        change.contentChanges.add(evt);
        server.didChangeTextDocument(change);
    }

    @Test
    public void unused() {
        server.lint(List.of(FindResource.path("org/javacs/warn/Unused.java")));
        assertThat(errors, hasItem("unused_local(7)")); // int unusedLocal
        assertThat(errors, hasItem("unused_field(10)")); // int unusedPrivate
        assertThat(errors, hasItem("unused_local(13)")); // int unusedLocalInLambda
        assertThat(errors, hasItem("unused_method(16)")); // int unusedMethod() { ... }
        assertThat(errors, hasItem("unused_method(22)")); // private Unused(int i) { }
        assertThat(errors, hasItem("unused_class(24)")); // private class UnusedClass { }
        assertThat(errors, hasItem("unused_method(26)")); // void unusedSelfReference() { ... }
        assertThat(errors, not("unused_param(6)")); // test(int unusedParam)
        assertThat(errors, not("unused_param(12)")); // unusedLambdaParam -> {};
        assertThat(errors, not(hasItem("unused_method(20)"))); // private Unused() { }
        assertThat(errors, hasItem("unused_method(30)")); // private void unusedMutuallyRecursive1() { ... }
        assertThat(errors, hasItem("unused_method(34)")); // private void unusedMutuallyRecursive2() { ... }
        assertThat(errors, not(hasItem("unused_method(38)"))); // private int usedByUnusedVar() { ... }
        assertThat(errors, not(hasItem("unused_throw(46)"))); // void notActuallyThrown() throws Exception { }
    }

    @Test
    public void pseudoUsed() {
        server.lint(List.of(FindResource.path("org/javacs/warn/PseudoUsed.java")));
        assertThat(errors, not(hasItem("unused_method(8)"))); // void pseudoUsed(int)
    }

    @Test
    public void interfaceConst() {
        server.lint(List.of(FindResource.path("org/javacs/warn/InterfaceConst.java")));
        assertThat(errors, empty());
    }

    @Test
    public void referencePackagePrivateClassInFileWithDifferentName() {
        server.lint(List.of(FindResource.path("org/javacs/example/ReferenceGotoPackagePrivate.java")));
        assertThat(errors, empty());
    }

    @Test
    public void notThrown() {
        server.lint(List.of(FindResource.path("org/javacs/warn/NotThrown.java")));
        assertThat(errors, hasItem("unused_throws(6)"));
        assertThat(errors, not(hasItem("unused_throws(8)")));
    }

    @Test
    public void notThrownConstructor() {
        server.lint(List.of(FindResource.path("org/javacs/warn/NotThrownConstructor.java")));
        assertThat(errors, empty());
    }

    // TODO warn on type.equals(otherType)
    // TODO warn on map.get(wrongKeyType)
}
