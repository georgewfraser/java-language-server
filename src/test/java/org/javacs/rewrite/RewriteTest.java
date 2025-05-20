package org.javacs.rewrite;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import org.javacs.CompilerProvider;
import org.javacs.LanguageServerFixture;
import org.junit.Test;

public class RewriteTest {
    static final CompilerProvider compiler = LanguageServerFixture.getCompilerProvider();

    private Path file(String name) {
        return LanguageServerFixture.DEFAULT_WORKSPACE_ROOT
                .resolve("src/org/javacs/rewrite")
                .resolve(name)
                .toAbsolutePath();
    }

    @Test
    public void renameVariable() {
        var file = file("TestRenameVariable.java");
        var edits = new RenameVariable(file, 82, "bar").rewrite(compiler);
        assertThat(edits.keySet(), hasSize(1));
        assertThat(edits, hasKey(file));
    }

    @Test
    public void renameField() {
        var className = "org.javacs.rewrite.TestRenameField";
        var fieldName = "foo";
        var renamer = new RenameField(className, fieldName, "bar");
        var edits = renamer.rewrite(compiler);
        assertThat(edits.keySet(), hasSize(1));
        assertThat(edits, hasKey(file("TestRenameField.java")));
    }

    @Test
    public void renameMethod() {
        var className = "org.javacs.rewrite.TestRenameMethod";
        var methodName = "foo";
        String[] erasedParameterTypes = {};
        var renamer = new RenameMethod(className, methodName, erasedParameterTypes, "bar");
        var edits = renamer.rewrite(compiler);
        assertThat(edits.keySet(), hasSize(1));
        assertThat(edits, hasKey(file("TestRenameMethod.java")));
    }

    @Test
    public void fixImports() {
        var file = file("TestFixImports.java");
        var edits = new AutoFixImports(file).rewrite(compiler);
        assertThat(edits, hasKey(file));
        for (var edit : edits.get(file)) {
            if (edit.newText.contains("java.util.List")) {
                return;
            }
        }
        fail();
    }

    @Test
    public void ignoreStaticImport() {
        var file = file("StaticImport.java");
        var edits = new AutoFixImports(file).rewrite(compiler);
        assertThat(edits, hasKey(file));
        for (var edit : edits.get(file)) {
            if (edit.newText.contains("java.util.Arrays.asList")) {
                fail();
            }
        }
    }

    @Test
    public void importAnnotation() {
        var file = file("ImportAnnotation.java");
        var edits = new AutoFixImports(file).rewrite(compiler);
        assertThat(edits, hasKey(file));
        for (var edit : edits.get(file)) {
            if (edit.newText.contains("java.lang.annotation.Native")) {
                return;
            }
        }
        fail("didn't re-create import java.lang.annotation.Native");
    }

    @Test
    public void importNotFound() {
        var file = file("ImportNotFound.java");
        var edits = new AutoFixImports(file).rewrite(compiler);
        assertThat(edits, hasKey(file));
        for (var edit : edits.get(file)) {
            if (edit.newText.contains("foo.bar.Doh")) {
                return;
            }
        }
        fail("didn't re-create import foo.bar.Doh");
    }

    @Test
    public void dontImportEnum() {
        var file = file("DontImportEnum.java");
        var edits = new AutoFixImports(file).rewrite(compiler);
        assertThat(edits, hasKey(file));
        for (var edit : edits.get(file)) {
            if (edit.newText.contains("READ")) {
                fail();
            }
        }
    }

    @Test
    public void addOverride() {
        var file = file("TestAddOverride.java");
        var edits = new AutoAddOverrides(file).rewrite(compiler);
        assertThat(edits, hasKey(file));
    }
}
