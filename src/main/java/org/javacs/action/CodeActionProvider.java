package org.javacs.action;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.lang.model.element.*;
import org.javacs.*;
import org.javacs.FindTypeDeclarationAt;
import org.javacs.imports.AutoImportProvider;
import org.javacs.lsp.*;
import org.javacs.rewrite.*;

public class CodeActionProvider {
    private final CompilerProvider compiler;
    private final AutoImportProvider autoImportProvider;

    public CodeActionProvider(CompilerProvider compiler, AutoImportProvider autoImportProvider) {
        this.compiler = compiler;
        this.autoImportProvider = autoImportProvider;
    }

    public List<CodeAction> codeActionsForCursor(CodeActionParams params) {
        LOG.info(
                String.format(
                        "Find code actions at %s(%d)...",
                        params.textDocument.uri.getPath(), params.range.start.line + 1));
        var started = Instant.now();
        var file = Paths.get(params.textDocument.uri);
        // TODO this get-map / convert-to-CodeAction split is an ugly workaround of the fact that we need a new compile
        // task to generate the code actions
        // If we switch to resolving code actions asynchronously using Command, that will fix this problem.
        var rewrites = new TreeMap<String, Rewrite>();
        try (var task = compiler.compile(file)) {
            var elapsed = Duration.between(started, Instant.now()).toMillis();
            LOG.info(String.format("...compiled in %d ms", elapsed));
            var lines = task.root().getLineMap();
            var cursor = lines.getPosition(params.range.start.line + 1, params.range.start.character + 1);
            rewrites.putAll(overrideInheritedMethods(task, file, cursor));
        }
        var actions = new ArrayList<CodeAction>();
        for (var title : rewrites.keySet()) {
            // TODO are these all quick fixes?
            actions.addAll(createQuickFix(title, rewrites.get(title)));
        }
        var elapsed = Duration.between(started, Instant.now()).toMillis();
        LOG.info(String.format("...created %d actions in %d ms", actions.size(), elapsed));
        return actions;
    }

    private Map<String, Rewrite> overrideInheritedMethods(CompileTask task, Path file, long cursor) {
        if (!isBlankLine(task.root(), cursor)) return Map.of();
        if (isInMethod(task, cursor)) return Map.of();
        var methodTree = new FindMethodDeclarationAt(task.task).scan(task.root(), cursor);
        if (methodTree != null) return Map.of();
        var actions = new TreeMap<String, Rewrite>();
        var trees = Trees.instance(task.task);
        var classTree = new FindTypeDeclarationAt(task.task).scan(task.root(), cursor);
        if (classTree == null) return Map.of();
        var classPath = trees.getPath(task.root(), classTree);
        var elements = task.task.getElements();
        var classElement = (TypeElement) trees.getElement(classPath);
        for (var member : elements.getAllMembers(classElement)) {
            if (member.getModifiers().contains(Modifier.FINAL)) continue;
            if (member.getKind() != ElementKind.METHOD) continue;
            var method = (ExecutableElement) member;
            var methodSource = (TypeElement) member.getEnclosingElement();
            if (methodSource.getQualifiedName().contentEquals("java.lang.Object")) continue;
            if (methodSource.equals(classElement)) continue;
            var ptr = new MethodPtr(task.task, method);
            var rewrite =
                    new OverrideInheritedMethod(
                            ptr.className, ptr.methodName, ptr.erasedParameterTypes, file, (int) cursor);
            var title = "Override '" + method.getSimpleName() + "' from " + ptr.className;
            actions.put(title, rewrite);
        }
        return actions;
    }

    private boolean isInMethod(CompileTask task, long cursor) {
        var method = new FindMethodDeclarationAt(task.task).scan(task.root(), cursor);
        return method != null;
    }

    private boolean isBlankLine(CompilationUnitTree root, long cursor) {
        var lines = root.getLineMap();
        var line = lines.getLineNumber(cursor);
        var start = lines.getStartPosition(line);
        CharSequence contents;
        try {
            contents = root.getSourceFile().getCharContent(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (var i = start; i < cursor; i++) {
            if (!Character.isWhitespace(contents.charAt((int) i))) {
                return false;
            }
        }
        return true;
    }

    public List<CodeAction> codeActionForDiagnostics(CodeActionParams params) {
        LOG.info(String.format("Check %d diagnostics for quick fixes...", params.context.diagnostics.size()));
        var started = Instant.now();
        var file = Paths.get(params.textDocument.uri);
        try (var task = compiler.compile(file)) {
            var actions = new ArrayList<CodeAction>();
            for (var d : params.context.diagnostics) {
                var newActions = codeActionForDiagnostic(task, file, d);
                actions.addAll(newActions);
            }
            var elapsed = Duration.between(started, Instant.now()).toMillis();
            LOG.info(String.format("...created %d quick fixes in %d ms", actions.size(), elapsed));
            return actions;
        }
    }

    private List<CodeAction> codeActionForDiagnostic(CompileTask task, Path file, Diagnostic d) {
        // TODO this should be done asynchronously using executeCommand
        switch (d.code) {
            case "unused_local":
                var toStatement = new ConvertVariableToStatement(file, findPosition(task, d.range.start));
                return createQuickFix("Convert to statement", toStatement);
            case "unused_field":
                var toBlock = new ConvertFieldToBlock(file, findPosition(task, d.range.start));
                return createQuickFix("Convert to block", toBlock);
            case "unused_class":
                var removeClass = new RemoveClass(file, findPosition(task, d.range.start));
                return createQuickFix("Remove class", removeClass);
            case "unused_method":
                var unusedMethod = findMethod(task, d.range);
                var removeMethod =
                        new RemoveMethod(
                                unusedMethod.className, unusedMethod.methodName, unusedMethod.erasedParameterTypes);
                return createQuickFix("Remove method", removeMethod);
            case "unused_throws":
                var shortExceptionName = extractRange(task, d.range);
                var notThrown = extractNotThrownExceptionName(d.message);
                var methodWithExtraThrow = findMethod(task, d.range);
                var removeThrow =
                        new RemoveException(
                                methodWithExtraThrow.className,
                                methodWithExtraThrow.methodName,
                                methodWithExtraThrow.erasedParameterTypes,
                                notThrown);
                return createQuickFix("Remove '" + shortExceptionName + "'", removeThrow);
            case "compiler.warn.unchecked.call.mbr.of.raw.type":
                var warnedMethod = findMethod(task, d.range);
                var suppressWarning =
                        new AddSuppressWarningAnnotation(
                                warnedMethod.className, warnedMethod.methodName, warnedMethod.erasedParameterTypes);
                return createQuickFix("Suppress 'unchecked' warning", suppressWarning);
            case "compiler.err.unreported.exception.need.to.catch.or.throw":
                var needsThrow = findMethod(task, d.range);
                var exceptionName = extractExceptionName(d.message);
                var addThrows =
                        new AddException(
                                needsThrow.className,
                                needsThrow.methodName,
                                needsThrow.erasedParameterTypes,
                                exceptionName);
                return createQuickFix("Add 'throws'", addThrows);
            case "compiler.err.cant.resolve":
            case "compiler.err.cant.resolve.location":
                var simpleName = extractRange(task, d.range);
                var allImports = new ArrayList<CodeAction>();
                for (var qualifiedName : compiler.publicTopLevelTypes()) {
                    if (qualifiedName.endsWith("." + simpleName)) {
                        var title = "Import '" + qualifiedName + "'";
                        var addImport = new AddImport(file, qualifiedName, autoImportProvider);
                        allImports.addAll(createQuickFix(title, addImport));
                    }
                }
                return allImports;
            case "compiler.err.var.not.initialized.in.default.constructor":
                var needsConstructor = findClassNeedingConstructor(task, d.range);
                if (needsConstructor == null) return List.of();
                var generateConstructor = new GenerateRecordConstructor(needsConstructor);
                return createQuickFix("Generate constructor", generateConstructor);
            case "compiler.err.does.not.override.abstract":
                var missingAbstracts = findClass(task, d.range);
                var implementAbstracts = new ImplementAbstractMethods(missingAbstracts);
                return createQuickFix("Implement abstract methods", implementAbstracts);
            case "compiler.err.cant.resolve.location.args":
                var missingMethod = new CreateMissingMethod(file, findPosition(task, d.range.start));
                return createQuickFix("Create missing method", missingMethod);
            default:
                return List.of();
        }
    }

    private int findPosition(CompileTask task, Position position) {
        var lines = task.root().getLineMap();
        return (int) lines.getPosition(position.line + 1, position.character + 1);
    }

    private String findClassNeedingConstructor(CompileTask task, Range range) {
        var type = findClassTree(task, range);
        if (type == null || hasConstructor(task, type)) return null;
        return qualifiedName(task, type);
    }

    private String findClass(CompileTask task, Range range) {
        var type = findClassTree(task, range);
        if (type == null) return null;
        return qualifiedName(task, type);
    }

    private ClassTree findClassTree(CompileTask task, Range range) {
        var position = task.root().getLineMap().getPosition(range.start.line + 1, range.start.character + 1);
        return new FindTypeDeclarationAt(task.task).scan(task.root(), position);
    }

    private String qualifiedName(CompileTask task, ClassTree tree) {
        var trees = Trees.instance(task.task);
        var path = trees.getPath(task.root(), tree);
        var type = (TypeElement) trees.getElement(path);
        return type.getQualifiedName().toString();
    }

    private boolean hasConstructor(CompileTask task, ClassTree type) {
        for (var member : type.getMembers()) {
            if (member instanceof MethodTree) {
                var method = (MethodTree) member;
                if (isConstructor(task, method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConstructor(CompileTask task, MethodTree method) {
        return method.getName().contentEquals("<init>") && !synthentic(task, method);
    }

    private boolean synthentic(CompileTask task, MethodTree method) {
        return Trees.instance(task.task).getSourcePositions().getStartPosition(task.root(), method) != -1;
    }

    private MethodPtr findMethod(CompileTask task, Range range) {
        var trees = Trees.instance(task.task);
        var position = task.root().getLineMap().getPosition(range.start.line + 1, range.start.character + 1);
        var tree = new FindMethodDeclarationAt(task.task).scan(task.root(), position);
        var path = trees.getPath(task.root(), tree);
        var method = (ExecutableElement) trees.getElement(path);
        return new MethodPtr(task.task, method);
    }

    class MethodPtr {
        String className, methodName;
        String[] erasedParameterTypes;

        MethodPtr(JavacTask task, ExecutableElement method) {
            var types = task.getTypes();
            var parent = (TypeElement) method.getEnclosingElement();
            className = parent.getQualifiedName().toString();
            methodName = method.getSimpleName().toString();
            erasedParameterTypes = new String[method.getParameters().size()];
            for (var i = 0; i < erasedParameterTypes.length; i++) {
                var param = method.getParameters().get(i);
                var type = param.asType();
                var erased = types.erasure(type);
                erasedParameterTypes[i] = erased.toString();
            }
        }
    }

    private static final Pattern NOT_THROWN_EXCEPTION = Pattern.compile("^'((\\w+\\.)*\\w+)' is not thrown");

    private String extractNotThrownExceptionName(String message) {
        var matcher = NOT_THROWN_EXCEPTION.matcher(message);
        if (!matcher.find()) {
            LOG.warning(String.format("`%s` doesn't match `%s`", message, NOT_THROWN_EXCEPTION));
            return "";
        }
        return matcher.group(1);
    }

    private static final Pattern UNREPORTED_EXCEPTION = Pattern.compile("unreported exception ((\\w+\\.)*\\w+)");

    private String extractExceptionName(String message) {
        var matcher = UNREPORTED_EXCEPTION.matcher(message);
        if (!matcher.find()) {
            LOG.warning(String.format("`%s` doesn't match `%s`", message, UNREPORTED_EXCEPTION));
            return "";
        }
        return matcher.group(1);
    }

    private CharSequence extractRange(CompileTask task, Range range) {
        CharSequence contents;
        try {
            contents = task.root().getSourceFile().getCharContent(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var start = (int) task.root().getLineMap().getPosition(range.start.line + 1, range.start.character + 1);
        var end = (int) task.root().getLineMap().getPosition(range.end.line + 1, range.end.character + 1);
        return contents.subSequence(start, end);
    }

    private List<CodeAction> createQuickFix(String title, Rewrite rewrite) {
        var edits = rewrite.rewrite(compiler);
        if (edits == Rewrite.CANCELLED) {
            return List.of();
        }
        var a = new CodeAction();
        a.kind = CodeActionKind.QuickFix;
        a.title = title;
        a.edit = new WorkspaceEdit();
        for (var file : edits.keySet()) {
            a.edit.changes.put(file.toUri(), List.of(edits.get(file)));
        }
        return List.of(a);
    }

    private static final Logger LOG = Logger.getLogger("main");
}
