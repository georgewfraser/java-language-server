package org.javacs.imports;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import javax.tools.Diagnostic;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;
import org.javacs.lsp.TextEdit;

/**
 * The default import order that simply sorts imports.
 */
public class SimpleAutoImportProvider implements AutoImportProvider {
    public static final SimpleAutoImportProvider INSTANCE = new SimpleAutoImportProvider();

    private final SectionedImportOrderHelper helper = new SectionedImportOrderHelper((className, isStatic) -> 0);

    private SimpleAutoImportProvider() {}

    @Override
    public List<TextEdit> addImport(String className, CompilationUnitTree root, SourcePositions sourcePositions) {
        return helper.addImport(className, root, sourcePositions);
    }
}
