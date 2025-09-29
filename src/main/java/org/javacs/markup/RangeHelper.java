package org.javacs.markup;

import com.sun.source.tree.CompilationUnitTree;
import org.javacs.lsp.Position;
import org.javacs.lsp.Range;

class RangeHelper {
    static Range range(CompilationUnitTree root, long start, long end) {
        var lines = root.getLineMap();
        var startLine = (int) lines.getLineNumber(start);
        var startLineFrom = lines.getStartPosition(startLine);
        var startPos = new Position(startLine - 1, (int)(start - startLineFrom));
        var endLine = (int) lines.getLineNumber(end);
        var endLineFrom = lines.getStartPosition(endLine);
        var endPos = new Position(endLine - 1, (int)(end - endLineFrom));
        return new Range(startPos, endPos);
    }
}
