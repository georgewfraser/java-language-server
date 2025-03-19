package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePathScanner;
import java.nio.file.Paths;
import java.time.Instant;
import org.junit.Test;

public class MarkdownHelperTest {
    private static final CompilerProvider compiler = LanguageServerFixture.getCompilerProvider();

    private String asMarkdown(String s) {
        var code = "/**\n * " + String.join("\n * ", s.split("\n")) + "\n */\nclass A {}\n";
        var task = compiler.parse(new SourceFileObject(Paths.get("/A.java"), code, Instant.now()));

        var docs = DocTrees.instance(task.task);

        class FindClassDoc extends TreePathScanner<Void, Void> {
            DocCommentTree docTree;

            @Override
            public Void visitClass(ClassTree classTree, Void p) {
                docTree = docs.getDocCommentTree(getCurrentPath());
                return null;
            }
        }
        var find = new FindClassDoc();
        find.scan(task.root, null);

        return MarkdownHelper.asMarkdown(find.docTree);
    }

    @Test
    public void formatSimpleTags() {
        assertThat(asMarkdown("<i>foo</i>"), equalTo("*foo*"));
        assertThat(asMarkdown("<b>foo</b>"), equalTo("**foo**"));
        assertThat(asMarkdown("hi\n\n<pre>foo</pre>"), equalTo("hi\n\n\n```\nfoo\n```\n"));
        assertThat(asMarkdown("<code>foo</code>"), equalTo("`foo`"));
        assertThat(asMarkdown("{@code foo}"), equalTo("`foo`"));
        assertThat(
                asMarkdown("<a href=\"#bar\">foo</a>"),
                equalTo("foo")); // TODO it would be nice if this converted to a link
    }

    @Test
    public void formatMultipleTags() {
        assertThat(asMarkdown("<code>foo</code> <code>bar</code>"), equalTo("`foo` `bar`"));
        assertThat(asMarkdown("{@code foo} {@code bar}"), equalTo("`foo` `bar`"));
    }

    @Test
    public void unmatchedBraces() {
        assertThat(asMarkdown("{@code foo}}}}"), equalTo("`foo`}}}"));
    }

    @Test
    public void paragraphs() {
        assertThat(asMarkdown("header\n\na\nb\n<p>c\nd"), equalTo("header\n\na\nb\n\n\n\nc\nd"));
    }

    @Test
    public void blockTags() {
        // TODO: Investigate why the symbol reference in @throws is not rendered.
        assertThat(
                asMarkdown("This is a method.\n@param a aaa\n@param b bbb\n@return ccc\n@throws d ddd"),
                equalTo(
                        "This is a method.\n\n\n\n*@param* `a` aaa\n\n\n\n*@param* `b` bbb\n\n\n\n*@return* ccc\n\n\n\n*@throws* ddd\n\n"));
    }

    @Test
    public void list() {
        assertThat(
                asMarkdown("This is a method.\n<ul><li>aaa<li>bbb</ul>"),
                equalTo("This is a method.\n\n\n\n\n- aaa\n- bbb"));
    }
}
