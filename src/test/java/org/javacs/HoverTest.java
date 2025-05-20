package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.StringJoiner;
import org.javacs.lsp.*;
import org.junit.Test;

public class HoverTest {

    @Test
    public void classIdentifier() {
        assertThat(
                symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 23),
                containsString("org.javacs.example.SymbolUnderCursor"));
    }

    @Test
    public void fieldIdentifier() {
        assertThat(symbolAt("/org/javacs/example/SymbolUnderCursor.java", 9, 27), containsString("field"));
    }

    @Test
    public void methodIdentifier() {
        assertThat(
                symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 12),
                containsString("method(String methodParameter)"));
    }

    @Test
    public void methodSelect() {
        assertThat(
                symbolAt("/org/javacs/example/SymbolUnderCursor.java", 13, 17),
                containsString("method(String methodParameter)"));
    }

    @Test
    public void methodReference() {
        assertThat(symbolAt("/org/javacs/example/SymbolUnderCursor.java", 14, 65), containsString("method"));
    }

    @Test
    public void annotationUse() {
        var found = symbolAt("/org/javacs/example/SymbolUnderCursor.java", 21, 8);
        assertThat(found, containsString("@interface Override"));
        assertThat(found, not(containsString("extends none")));
    }

    @Test
    public void methodParameterReference() {
        assertThat(symbolAt("/org/javacs/example/SymbolUnderCursor.java", 10, 32), containsString("methodParameter"));
    }

    @Test
    public void localVariableReference() {
        assertThat(symbolAt("/org/javacs/example/SymbolUnderCursor.java", 10, 16), containsString("localVariable"));
    }

    @Test
    public void throwsList() {
        assertThat(
                symbolAt("/org/javacs/example/HoverThrows.java", 11, 11), containsString("throws java.io.IOException"));
    }

    @Test
    public void docString() {
        assertThat(
                symbolAt("/org/javacs/example/HoverDocs.java", 7, 15),
                containsString("Returns an unmodifiable list containing zero elements."));
    }

    // Re-using the language server makes these tests go a lot faster, but it will potentially produce surprising output
    // if things go wrong
    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private String symbolAt(String file, int line, int character) {
        var pos =
                new TextDocumentPositionParams(
                        new TextDocumentIdentifier(FindResource.uri(file)), new Position(line - 1, character - 1));
        var result = new StringJoiner("\n");
        for (var h : server.hover(pos).get().contents) {
            result.add(h.value);
        }
        return result.toString();
    }
}
