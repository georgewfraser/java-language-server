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
 * A common logic to implement AutoImportProvider that organizes imports into one or more sections.
 */
class SectionedImportOrderHelper {
    /**
     * Decides which section a class name belongs to.
     *
     * It should return an integer that represents a section. Sections are organized in the
     * increasing order.
     */
    public interface SectionFunction {
        int sectionOf(String className, boolean isStatic);
    }

    private final SectionFunction sectionFunction;

    public SectionedImportOrderHelper(SectionFunction sectionFunction) {
        this.sectionFunction = sectionFunction;
    }

    public List<TextEdit> addImport(String className, CompilationUnitTree root, SourcePositions sourcePositions) {
        String importCode = "import " + className + ";\n";
        var lineMap = root.getLineMap();
        var imports = root.getImports();

        // No need to import java.lang.*.
        if (className.startsWith("java.lang.")) {
            return List.of();
        }

        // No need to import classes in the same package.
        var packageTree = root.getPackage();
        if (packageTree != null) {
            String packageName = packageTree.getPackageName().toString();
            if (className.startsWith(packageName + ".") && !className.substring(packageName.length() + 1).contains(".")) {
                return List.of();
            }
        }

        // If there is already an import, do not insert one.
        for (var i : imports) {
            String importedClassName = i.getQualifiedIdentifier().toString();
            if (importedClassName.equals(className)) {
                return List.of();
            }
            if (importedClassName.endsWith(".*")) {
                String importedPackage = importedClassName.substring(0, importedClassName.length() - 1);
                if (className.startsWith(importedPackage)) {
                    return List.of();
                }
            }
        }

        // Special logic to handle the case of no imports yet.
        if (imports.isEmpty()) {
            if (packageTree == null) {
                // There is even no package declaration.
                return List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), importCode));
            }
            var packageStartPos = sourcePositions.getStartPosition(root, root.getPackage());
            var packageLine = (int) lineMap.getLineNumber(packageStartPos) - 1;
            var editPosition = new Position(packageLine + 1, 0);
            return List.of(new TextEdit(new Range(editPosition, editPosition), "\n" + importCode));
        }

        // Find the position to insert a new import.
        int newImportSection = sectionFunction.sectionOf(className, false);
        int insertPosition = imports.size();
        for (var i = 0; i < imports.size(); i++) {
            var currentImport = imports.get(i);
            String nextClassName = currentImport.getQualifiedIdentifier().toString();
            int nextImportSection = sectionFunction.sectionOf(nextClassName, currentImport.isStatic());
            if (nextImportSection > newImportSection || (nextImportSection == newImportSection && nextClassName.compareTo(className) > 0)) {
                insertPosition = i;
                break;
            }
        }

        if (insertPosition == imports.size()) {
            // Add to the end.
            var lastImport = imports.get(imports.size() - 1);
            int lastImportSection = sectionFunction.sectionOf(lastImport.getQualifiedIdentifier().toString(), lastImport.isStatic());
            String insertCode = importCode;
            if (lastImportSection < newImportSection) {
                insertCode = "\n" + insertCode;
            }
            var lastImportStartPos = sourcePositions.getStartPosition(root, lastImport);
            var lastImportLine = (int) lineMap.getLineNumber(lastImportStartPos) - 1;
            var editPosition = new Position(lastImportLine + 1, 0);
            return List.of(new TextEdit(new Range(editPosition, editPosition), insertCode));
        }

        // Insert to the beginning or the middle.
        var nextImport = imports.get(insertPosition);
        int nextImportSection = sectionFunction.sectionOf(nextImport.getQualifiedIdentifier().toString(), nextImport.isStatic());
        var nextImportStartPos = sourcePositions.getStartPosition(root, nextImport);
        var nextImportLine = (int) lineMap.getLineNumber(nextImportStartPos) - 1;
        var editPosition = new Position(nextImportLine, 0);
        String insertCode = importCode;
        if (newImportSection < nextImportSection) {
            var previousImport = insertPosition > 0 ? imports.get(insertPosition - 1) : null;
            if (previousImport != null && sectionFunction.sectionOf(previousImport.getQualifiedIdentifier().toString(), previousImport.isStatic()) == newImportSection) {
                // Append an import to the end of the previous section.
                editPosition.line--;
            } else {
                // Create a new section.
                insertCode += "\n";
            }
        }
        return List.of(new TextEdit(new Range(editPosition, editPosition), insertCode));
    }
}
