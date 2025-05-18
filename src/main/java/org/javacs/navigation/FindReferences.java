package org.javacs.navigation;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.List;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

class FindReferences extends TreePathScanner<Void, List<TreePath>> {
    final JavacTask task;
    final Element find;

    FindReferences(JavacTask task, Element find) {
        this.task = task;
        this.find = find;
    }

    @Override
    public Void visitIdentifier(IdentifierTree t, List<TreePath> list) {
        if (check()) {
            list.add(getCurrentPath());
        }
        return super.visitIdentifier(t, list);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree t, List<TreePath> list) {
        if (check()) {
            list.add(getCurrentPath());
        }
        return super.visitMemberSelect(t, list);
    }

    @Override
    public Void visitNewClass(NewClassTree t, List<TreePath> list) {
        if (check()) {
            list.add(getCurrentPath());
        }
        return super.visitNewClass(t, list);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree t, List<TreePath> list) {
        if (check()) {
            list.add(getCurrentPath());
        }
        return super.visitMemberReference(t, list);
    }

    private boolean check() {
        var path = getCurrentPath();
        var trees = Trees.instance(task);
        var candidate = trees.getElement(path);
        if (!find.equals(candidate)) {
            return false;
        }
        var pos = trees.getSourcePositions();
        // Skip elements without positions. This can happen, e.g. for var types.
        if (pos.getStartPosition(path.getCompilationUnit(), path.getLeaf()) == Diagnostic.NOPOS ||
            pos.getEndPosition(path.getCompilationUnit(), path.getLeaf()) == Diagnostic.NOPOS) {
            return false;
        }
        return true;
    }
}
