package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.javacs.lsp.DidCloseTextDocumentParams;
import org.javacs.lsp.DidOpenTextDocumentParams;
import org.javacs.lsp.TextDocumentItem;
import org.javacs.lsp.TextDocumentIdentifier;

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

    @Test
    public void inputReadersPreferActiveDocumentContents() throws IOException {
        Path file = Files.createTempFile("FileStoreTest", ".java");
        try {
            Files.writeString(file, "class Foo { int onDisk; }\n");

            var open = new DidOpenTextDocumentParams();
            open.textDocument = new TextDocumentItem();
            open.textDocument.uri = file.toUri();
            open.textDocument.languageId = "java";
            open.textDocument.version = 1;
            open.textDocument.text = "class Foo { int inMemory; }\n";
            FileStore.open(open);

            var fromInputStream = new String(FileStore.inputStream(file).readAllBytes(), StandardCharsets.UTF_8);
            var fromBufferedReader = FileStore.bufferedReader(file).readLine();

            assertThat(fromInputStream, containsString("inMemory"));
            assertThat(fromInputStream, not(containsString("onDisk")));
            assertThat(fromBufferedReader, containsString("inMemory"));
            assertThat(fromBufferedReader, not(containsString("onDisk")));
        } finally {
            var close = new DidCloseTextDocumentParams();
            close.textDocument = new TextDocumentIdentifier(file.toUri());
            FileStore.close(close);
            Files.deleteIfExists(file);
        }
    }
}
