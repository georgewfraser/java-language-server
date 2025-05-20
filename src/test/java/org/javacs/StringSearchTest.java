package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.nio.file.Paths;
import org.javacs.lsp.DidChangeTextDocumentParams;
import org.javacs.lsp.DidCloseTextDocumentParams;
import org.javacs.lsp.DidOpenTextDocumentParams;
import org.javacs.lsp.TextDocumentContentChangeEvent;
import org.junit.Test;

public class StringSearchTest {
    private void testNext(String pat, String text, int index) {
        var got = new StringSearch(pat).next(text);
        assertThat(got, equalTo(index));
    }

    private void testNextWord(String pat, String text, int index) {
        var got = new StringSearch(pat).nextWord(text);
        assertThat(got, equalTo(index));
    }

    @Test
    public void testNext() {
        testNext("", "", 0);
        testNext("", "abc", 0);
        testNext("abc", "", -1);
        testNext("abc", "abc", 0);
        testNext("d", "abcdefg", 3);
        testNext("nan", "banana", 2);
        testNext("pan", "anpanman", 2);
        testNext("nnaaman", "anpanmanam", -1);
        testNext("abcd", "abc", -1);
        testNext("abcd", "bcd", -1);
        testNext("bcd", "abcd", 1);
        testNext("abc", "acca", -1);
        testNext("aa", "aaa", 0);
        testNext("baa", "aaaaa", -1);
        testNext("at that", "which finally halts.  at that point", 22);
    }

    @Test
    public void testNextWord() {
        testNextWord("", "", 0);
        testNextWord("", "abc", -1);
        testNextWord("abc", "", -1);
        testNextWord("abc", "abc", 0);
        testNextWord("d", "abcdefg", -1);
        testNextWord("d", "abc d efg", 4);
        testNextWord("nan", "banana", -1);
        testNextWord("nan", "ba nan a", 3);
        testNextWord("abcd", "abc", -1);
        testNextWord("abcd", "bcd", -1);
        testNextWord("bcd", "abcd", -1);
        testNextWord("bcd", "a bcd", 2);
        testNextWord("abc", "abc d", 0);
        testNextWord("aa", "aaa", -1);
        testNextWord("aa", "a aa", 2);
        testNextWord("aa", "aa a", 0);
    }

    @Test
    public void testMatchesTitleCase() {
        assertTrue(StringSearch.matchesTitleCase("FooBar", "fb"));
        assertTrue(StringSearch.matchesTitleCase("FooBar", "fob"));
        assertTrue(StringSearch.matchesTitleCase("AnyPrefixFooBar", "fb"));
        assertTrue(StringSearch.matchesTitleCase("AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(StringSearch.matchesTitleCase("UPPERFooBar", "fb"));
        assertFalse(StringSearch.matchesTitleCase("Foobar", "fb"));

        assertTrue(StringSearch.matchesTitleCase("Prefix FooBar", "fb"));
        assertTrue(StringSearch.matchesTitleCase("Prefix FooBar", "fob"));
        assertTrue(StringSearch.matchesTitleCase("Prefix AnyPrefixFooBar", "fb"));
        assertTrue(StringSearch.matchesTitleCase("Prefix AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(StringSearch.matchesTitleCase("Prefix UPPERFooBar", "fb"));
        assertFalse(StringSearch.matchesTitleCase("Foo Bar", "fb"));
    }

    @Test
    public void searchLargeFile() {
        var largeFile = Paths.get(FindResource.uri("/org/javacs/example/LargeFile.java"));
        assertTrue(StringSearch.containsWordMatching(largeFile, "removeMethodBodies"));
        assertFalse(StringSearch.containsWordMatching(largeFile, "removeMethodBodiez"));
    }

    @Test
    public void searchSmallFile() {
        var smallFile = Paths.get(FindResource.uri("/org/javacs/example/Goto.java"));
        assertTrue(StringSearch.containsWordMatching(smallFile, "nonDefaultConstructor"));
        assertFalse(StringSearch.containsWordMatching(smallFile, "removeMethodBodies"));
    }

    @Test
    public void searchOpenFile() {
        // Open file
        var smallFile = Paths.get(FindResource.uri("/org/javacs/example/Goto.java"));
        var open = new DidOpenTextDocumentParams();
        open.textDocument.text = FileStore.contents(smallFile);
        open.textDocument.uri = smallFile.toUri();
        FileStore.open(open);
        // Edit file
        var change = new DidChangeTextDocumentParams();
        change.textDocument.uri = smallFile.toUri();
        var evt = new TextDocumentContentChangeEvent();
        evt.text = "package org.javacs.example; class Foo { }";
        change.contentChanges.add(evt);
        FileStore.change(change);
        // Check that Parser sees the edits
        try {
            assertTrue(StringSearch.containsWordMatching(smallFile, "Foo"));
        } finally {
            // Close file
            var close = new DidCloseTextDocumentParams();
            close.textDocument.uri = smallFile.toUri();
            FileStore.close(close);
        }
    }

    @Test
    public void findAutocompleteBetweenLines() {
        var rel = Paths.get("src", "org", "javacs", "example", "AutocompleteBetweenLines.java");
        var file = LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve(rel);
        assertTrue(StringSearch.containsWordMatching(file, "ABetweenLines"));
    }

    @Test
    public void matchesPartialName() {
        assertTrue(StringSearch.matchesPartialName("foobar", "foo"));
        assertFalse(StringSearch.matchesPartialName("foo", "foobar"));
    }
}
