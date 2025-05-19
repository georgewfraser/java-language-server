package org.javacs.imports;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.SourcePositions;
import org.javacs.lsp.TextEdit;

/**
 * The factory of AutoImportProvider.
 */
public class AutoImportProviderFactory {
    public static AutoImportProvider getByName(String name) {
        switch (name) {
            case "", "default", "simple":
                return SimpleAutoImportProvider.INSTANCE;
            case "chromium":
                return ChromiumAutoImportProvider.INSTANCE;
            default:
                throw new IllegalArgumentException("Unknown import order: " + name);
        }
    }
}
