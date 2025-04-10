package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.Map;
import org.javacs.CompilerProvider;
import org.javacs.ParseTask;
import org.javacs.imports.AutoImportProvider;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

public class AddImport implements Rewrite {
    final Path file;
    final String className;
    final AutoImportProvider autoImportProvider;

    public AddImport(Path file, String className, AutoImportProvider autoImportProvider) {
        this.file = file;
        this.className = className;
        this.autoImportProvider = autoImportProvider;
    }

    @Override
    public Map<Path, TextEdit[]> rewrite(CompilerProvider compiler) {
        var task = compiler.parse(file);
        var edits = autoImportProvider.addImport(className, task.root, Trees.instance(task.task).getSourcePositions());
        return Map.of(file, edits.toArray(new TextEdit[0]));
    }
}
