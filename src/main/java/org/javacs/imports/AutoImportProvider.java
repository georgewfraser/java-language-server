package org.javacs.imports;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import org.javacs.lsp.TextEdit;

/**
 * Provides the functionality to auto-import classes.
 */
public interface AutoImportProvider {
    /**
     * Computes edits to add an import statement of the given class name to the Java file.
     */
    List<TextEdit> addImport(String className, CompilationUnitTree root, SourcePositions sourcePositions);
}
