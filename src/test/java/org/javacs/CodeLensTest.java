package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.*;
import org.junit.Test;

public class CodeLensTest {

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private List<? extends CodeLens> lenses(String file) {
        var uri = FindResource.uri(file);
        var params = new CodeLensParams(new TextDocumentIdentifier(uri));
        var lenses = server.codeLens(params);
        var resolved = new ArrayList<CodeLens>();
        for (var lens : lenses) {
            if (lens.command == null) {
                lens = server.resolveCodeLens(lens);
            }
            resolved.add(lens);
        }
        return resolved;
    }

    private List<String> commands(List<? extends CodeLens> lenses) {
        var commands = new ArrayList<String>();
        for (var lens : lenses) {
            commands.add(String.format("%s(%s)", lens.command.command, lens.command.arguments));
        }
        return commands;
    }

    @Test
    public void testMethods() {
        var lenses = lenses("/org/javacs/example/HasTest.java");
        assertThat(lenses, not(empty()));

        var commands = commands(lenses);
        assertThat(commands, hasItem(containsString("\"org.javacs.example.HasTest\",null")));
        assertThat(commands, hasItem(containsString("\"org.javacs.example.HasTest\",\"testMethod\"")));
        assertThat(commands, hasItem(containsString("\"org.javacs.example.HasTest\",\"otherTestMethod\"")));
    }
}
