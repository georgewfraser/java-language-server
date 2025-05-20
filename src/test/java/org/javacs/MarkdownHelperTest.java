package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class MarkdownHelperTest {
    @Test
    public void formatSimpleTags() {
        assertThat(MarkdownHelper.asMarkdown("<i>foo</i>"), equalTo("*foo*"));
        assertThat(MarkdownHelper.asMarkdown("<b>foo</b>"), equalTo("**foo**"));
        assertThat(MarkdownHelper.asMarkdown("<pre>foo</pre>"), equalTo("`foo`"));
        assertThat(MarkdownHelper.asMarkdown("<code>foo</code>"), equalTo("`foo`"));
        assertThat(MarkdownHelper.asMarkdown("{@code foo}"), equalTo("`foo`"));
        assertThat(
                MarkdownHelper.asMarkdown("<a href=\"#bar\">foo</a>"),
                equalTo("foo")); // TODO it would be nice if this converted to a link
    }

    @Test
    public void formatMultipleTags() {
        assertThat(MarkdownHelper.asMarkdown("<code>foo</code> <code>bar</code>"), equalTo("`foo` `bar`"));
        assertThat(MarkdownHelper.asMarkdown("{@code foo} {@code bar}"), equalTo("`foo` `bar`"));
    }
}
