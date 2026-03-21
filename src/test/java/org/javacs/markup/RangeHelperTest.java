package org.javacs.markup;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import java.net.URI;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.Test;

public class RangeHelperTest {
    @Test
    public void usesCharacterOffsetsInsteadOfExpandedTabColumns() throws Exception {
        var source = "class Tabs {\n\tint foo;\n}\n";
        var start = source.indexOf("foo");
        var end = start + "foo".length();

        var range = RangeHelper.range(parse(source), start, end);

        assertThat(range.start.line, is(1));
        assertThat(range.start.character, is(5));
        assertThat(range.end.line, is(1));
        assertThat(range.end.character, is(8));
    }

    private static CompilationUnitTree parse(String source) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        var file =
                new SimpleJavaFileObject(URI.create("string:///Tabs.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return source;
                    }
                };
        var task = (JavacTask) compiler.getTask(null, null, null, List.of(), null, List.of(file));
        return task.parse().iterator().next();
    }
}
