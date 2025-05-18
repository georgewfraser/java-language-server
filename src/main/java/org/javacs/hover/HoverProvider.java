package org.javacs.hover;

import com.google.gson.JsonNull;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;
import javax.lang.model.element.*;
import org.javacs.CompileTask;
import org.javacs.CompilerProvider;
import org.javacs.CompletionData;
import org.javacs.FindHelper;
import org.javacs.JsonHelper;
import org.javacs.MarkdownHelper;
import org.javacs.ParseTask;
import org.javacs.lsp.CompletionItem;
import org.javacs.lsp.MarkedString;

public class HoverProvider {
    final CompilerProvider compiler;

    public static final List<MarkedString> NOT_SUPPORTED = List.of();

    public HoverProvider(CompilerProvider compiler) {
        this.compiler = compiler;
    }

    public List<MarkedString> hover(Path file, int line, int column) {
        try (var task = compiler.compile(file)) {
            var position = task.root().getLineMap().getPosition(line, column);
            var element = new FindHoverElement(task.task).scan(task.root(), position);
            if (element == null) return NOT_SUPPORTED;
            var list = new ArrayList<MarkedString>();
            var code = printType(element);
            list.add(new MarkedString("java", code));
            var docs = docs(task, element);
            if (!docs.isEmpty()) {
                list.add(new MarkedString(docs));
            }
            return list;
        }
    }

    public void resolveCompletionItem(CompletionItem item) {
        if (item.data == null || item.data == JsonNull.INSTANCE) return;
        var data = JsonHelper.GSON.fromJson(item.data, CompletionData.class);
        var source = compiler.findAnywhere(data.className);
        if (source.isEmpty()) return;
        var task = compiler.parse(source.get());
        var tree = findItem(task, data);
        resolveDetail(item, data, tree);
        var path = Trees.instance(task.task).getPath(task.root, tree);
        var docTree = DocTrees.instance(task.task).getDocCommentTree(path);
        if (docTree == null) return;
        item.documentation = MarkdownHelper.asMarkupContent(docTree);
    }

    // TODO consider showing actual source code instead of just types and names
    private void resolveDetail(CompletionItem item, CompletionData data, Tree tree) {
        if (tree instanceof MethodTree) {
            var method = (MethodTree) tree;
            var parameters = new StringJoiner(", ");
            for (var p : method.getParameters()) {
                parameters.add(p.getType() + " " + p.getName());
            }
            item.detail = method.getReturnType() + " " + method.getName() + "(" + parameters + ")";
            if (!method.getThrows().isEmpty()) {
                var exceptions = new StringJoiner(", ");
                for (var e : method.getThrows()) {
                    exceptions.add(e.toString());
                }
                item.detail += " throws " + exceptions;
            }
            if (data.plusOverloads != 0) {
                item.detail += " (+" + data.plusOverloads + " overloads)";
            }
        }
    }

    private Tree findItem(ParseTask task, CompletionData data) {
        if (data.erasedParameterTypes != null) {
            return FindHelper.findMethod(task, data.className, data.memberName, data.erasedParameterTypes);
        }
        if (data.memberName != null) {
            return FindHelper.findField(task, data.className, data.memberName);
        }
        if (data.className != null) {
            return FindHelper.findType(task, data.className);
        }
        throw new RuntimeException("no className");
    }

    private String docs(CompileTask task, Element element) {
        if (element instanceof TypeElement) {
            var type = (TypeElement) element;
            var className = type.getQualifiedName().toString();
            var file = compiler.findAnywhere(className);
            if (file.isEmpty()) return "";
            var parse = compiler.parse(file.get());
            var tree = FindHelper.findType(parse, className);
            return docs(parse, tree);
        } else if (element.getKind() == ElementKind.FIELD) {
            var field = (VariableElement) element;
            var type = (TypeElement) field.getEnclosingElement();
            var className = type.getQualifiedName().toString();
            var file = compiler.findAnywhere(className);
            if (file.isEmpty()) return "";
            var parse = compiler.parse(file.get());
            var tree = FindHelper.findField(parse, className, field.getSimpleName().toString());
            return docs(parse, tree);
        } else if (element instanceof ExecutableElement) {
            var method = (ExecutableElement) element;
            var type = (TypeElement) method.getEnclosingElement();
            var className = type.getQualifiedName().toString();
            var methodName = method.getSimpleName().toString();
            var erasedParameterTypes = FindHelper.erasedParameterTypes(task, method);
            var file = compiler.findAnywhere(className);
            if (file.isEmpty()) return "";
            var parse = compiler.parse(file.get());
            var tree = FindHelper.findMethod(parse, className, methodName, erasedParameterTypes);
            return docs(parse, tree);
        } else {
            return "";
        }
    }

    private String docs(ParseTask task, Tree tree) {
        var path = Trees.instance(task.task).getPath(task.root, tree);
        var docTree = DocTrees.instance(task.task).getDocCommentTree(path);
        if (docTree == null) return "";
        return MarkdownHelper.asMarkdown(docTree);
    }

    // TODO this should be merged with logic in CompletionProvider
    // TODO this should parameterize the type
    // TODO show more information about declarations---was this a parameter, a field? What were the modifiers?
    private String printType(Element e) {
        if (e instanceof ExecutableElement) {
            var m = (ExecutableElement) e;
            return ShortTypePrinter.DEFAULT.printMethod(m);
        } else if (e instanceof VariableElement) {
            var v = (VariableElement) e;
            return ShortTypePrinter.DEFAULT.print(v.asType()) + " " + v;
        } else if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            var lines = new StringJoiner("\n");
            lines.add(hoverTypeDeclaration(t) + " {");
            for (var member : t.getEnclosedElements()) {
                // TODO check accessibility
                if (member instanceof ExecutableElement || member instanceof VariableElement) {
                    lines.add("  " + printType(member) + ";");
                } else if (member instanceof TypeElement) {
                    lines.add("  " + hoverTypeDeclaration((TypeElement) member) + " { /* removed */ }");
                }
            }
            lines.add("}");
            return lines.toString();
        } else {
            return e.toString();
        }
    }

    private String hoverTypeDeclaration(TypeElement t) {
        var result = new StringBuilder();
        switch (t.getKind()) {
            case ANNOTATION_TYPE:
                result.append("@interface");
                break;
            case INTERFACE:
                result.append("interface");
                break;
            case CLASS:
                result.append("class");
                break;
            case ENUM:
                result.append("enum");
                break;
            default:
                LOG.warning("Don't know what to call type element " + t);
                result.append("_");
        }
        result.append(" ").append(ShortTypePrinter.DEFAULT.print(t.asType()));
        var superType = ShortTypePrinter.DEFAULT.print(t.getSuperclass());
        switch (superType) {
            case "Object":
            case "none":
                break;
            default:
                result.append(" extends ").append(superType);
        }
        return result.toString();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
