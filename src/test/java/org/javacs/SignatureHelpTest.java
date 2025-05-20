package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Test;

public class SignatureHelpTest {
    @Test
    public void signatureHelp() {
        var help = doHelp("/org/javacs/example/SignatureHelp.java", 7, 36);
        assertThat(help.signatures, hasSize(2));
    }

    @Test
    public void partlyFilledIn() {
        var help = doHelp("/org/javacs/example/SignatureHelp.java", 8, 39);
        assertThat(help.signatures, hasSize(2));
        assertThat(help.activeSignature, equalTo(1));
        assertThat(help.activeParameter, equalTo(1));
    }

    @Test
    public void constructor() {
        var help = doHelp("/org/javacs/example/SignatureHelp.java", 9, 27);
        assertThat(help.signatures, hasSize(1));
        assertThat(help.signatures.get(0).label, startsWith("SignatureHelp"));
    }

    @Test
    public void platformConstructor() {
        var help = doHelp("/org/javacs/example/SignatureHelp.java", 10, 26);
        assertThat(help.signatures, not(empty()));
        assertThat(help.signatures.get(0).label, startsWith("ArrayList"));
        // TODO
        // assertThat(help.signatures.get(0).documentation, not(nullValue()));
    }

    @Test
    public void overloads() {
        var labels = labels("/org/javacs/example/Overloads.java", 5, 15);
        assertThat(labels, hasItem(containsString("print(int i)")));
        assertThat(labels, hasItem(containsString("print(String s)")));
    }

    @Test
    public void localDoc() {
        var help = doHelp("/org/javacs/example/LocalMethodDoc.java", 5, 23);
        var method = help.signatures.get(help.activeSignature);
        assertThat(method.documentation.value, containsString("A great method"));
    }

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private SignatureHelp doHelp(String file, int row, int column) {
        var document = new TextDocumentIdentifier();
        document.uri = FindResource.uri(file);
        var position = new Position();
        position.line = row - 1;
        position.character = column - 1;
        var p = new TextDocumentPositionParams();
        p.textDocument = document;
        p.position = position;
        var maybe = server.signatureHelp(p);
        if (maybe.isEmpty()) fail("not supported");
        return maybe.get();
    }

    private List<String> labels(String file, int row, int column) {
        var help = doHelp(file, row, column);
        var result = new ArrayList<String>();
        for (var s : help.signatures) {
            result.add(s.label);
        }
        return result;
    }
}
