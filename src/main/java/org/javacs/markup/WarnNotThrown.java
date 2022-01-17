package org.javacs.markup;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

class WarnNotThrown extends TreePathScanner<Void, Map<TreePath, String>> {
    private final JavacTask task;
    private CompilationUnitTree root;
    private Map<String, TreePath> declaredExceptions = new HashMap<>();
    private Set<String> observedExceptions = new HashSet<>();

    WarnNotThrown(JavacTask task) {
        this.task = task;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree t, Map<TreePath, String> notThrown) {
        root = t;
        return super.visitCompilationUnit(t, notThrown);
    }

    @Override
    public Void visitMethod(MethodTree t, Map<TreePath, String> notThrown) {
        // Create a new method scope
        var pushDeclared = declaredExceptions;
        var pushObserved = observedExceptions;
        declaredExceptions = declared(t);
        observedExceptions = new HashSet<>();
        // Recursively scan for 'throw' and method calls
        super.visitMethod(t, notThrown);
        // Check for exceptions that were never thrown
        for (var exception : declaredExceptions.keySet()) {
            if (!observedExceptions.contains(exception)) {
                notThrown.put(declaredExceptions.get(exception), exception);
            }
        }
        declaredExceptions = pushDeclared;
        observedExceptions = pushObserved;
        return null;
    }

    private Map<String, TreePath> declared(MethodTree t) {
        var trees = Trees.instance(task);
        var names = new HashMap<String, TreePath>();
        for (var e : t.getThrows()) {
            var path = new TreePath(getCurrentPath(), e);
            var to = trees.getElement(path);
            if (!(to instanceof TypeElement)) continue;
            var type = (TypeElement) to;
            var name = type.getQualifiedName().toString();
            names.put(name, path);
        }
        return names;
    }

    @Override
    public Void visitThrow(ThrowTree t, Map<TreePath, String> notThrown) {
        var path = new TreePath(getCurrentPath(), t.getExpression());
        var type = Trees.instance(task).getTypeMirror(path);
        addThrown(type);
        return super.visitThrow(t, notThrown);
    }

    @Override
    public Void visitNewClass(NewClassTree t, Map<TreePath, String> notThrown) {
        var trees = Trees.instance(task);
        var target = trees.getElement(getCurrentPath());
        if (target instanceof ExecutableElement) {
            var method = (ExecutableElement) target;
            for (var type : method.getThrownTypes()) {
                addThrown(type);
            }
        }
        return super.visitNewClass(t, notThrown);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree t, Map<TreePath, String> notThrown) {
        var trees = Trees.instance(task);
        var target = trees.getElement(getCurrentPath());
        if (target instanceof ExecutableElement) {
            var method = (ExecutableElement) target;
            for (var type : method.getThrownTypes()) {
                addThrown(type);
            }
        }
        return super.visitMethodInvocation(t, notThrown);
    }

    private void addThrown(TypeMirror type) {
        if (type instanceof DeclaredType) {
            var declared = (DeclaredType) type;
            var el = (TypeElement) declared.asElement();
            var name = el.getQualifiedName().toString();
            observedExceptions.add(name);
        }
    }
}
