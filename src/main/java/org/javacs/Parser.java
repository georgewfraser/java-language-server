package org.javacs;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.lang.model.element.*;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import org.javacs.lsp.*;

class Parser {
    private static final JavaCompiler COMPILER = ServiceLoader.load(JavaCompiler.class).iterator().next();
    private static final SourceFileManager FILE_MANAGER = new SourceFileManager();

    /** Create a task that compiles a single file */
    private static JavacTask singleFileTask(JavaFileObject file) {
        return (JavacTask)
                COMPILER.getTask(null, FILE_MANAGER, Parser::ignoreError, List.of(), List.of(), List.of(file));
    }

    final JavaFileObject file;
    final String contents;
    final JavacTask task;
    final CompilationUnitTree root;
    final Trees trees;

    private Parser(JavaFileObject file) {
        this.file = file;
        try {
            this.contents = file.getCharContent(false).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.task = singleFileTask(file);
        try {
            this.root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.trees = Trees.instance(task);
    }

    static Parser parseFile(Path file) {
        return parseJavaFileObject(new SourceFileObject(file));
    }

    private static Parser cachedParse;
    private static long cachedModified = -1;

    private static boolean needsParse(JavaFileObject file) {
        if (cachedParse == null) return true;
        if (!cachedParse.file.equals(file)) return true;
        if (file.getLastModified() > cachedModified) return true;
        return false;
    }

    private static void loadParse(JavaFileObject file) {
        cachedParse = new Parser(file);
        cachedModified = file.getLastModified();
    }

    static Parser parseJavaFileObject(JavaFileObject file) {
        if (needsParse(file)) {
            loadParse(file);
        } else {
            LOG.info("...using cached parse");
        }
        return cachedParse;
    }

    Set<Name> packagePrivateClasses() {
        var result = new HashSet<Name>();
        for (var t : root.getTypeDecls()) {
            if (t instanceof ClassTree) {
                var c = (ClassTree) t;
                var isPublic = c.getModifiers().getFlags().contains(Modifier.PUBLIC);
                if (!isPublic) {
                    result.add(c.getSimpleName());
                }
            }
        }
        return result;
    }

    static Range range(JavacTask task, CharSequence contents, TreePath path) {
        // Find start position
        var trees = Trees.instance(task);
        var pos = trees.getSourcePositions();
        var root = path.getCompilationUnit();
        var lines = root.getLineMap();
        var start = (int) pos.getStartPosition(root, path.getLeaf());
        var end = (int) pos.getEndPosition(root, path.getLeaf());

        // If start is -1, give up
        if (start == -1) {
            LOG.warning(String.format("Couldn't locate `%s`", path.getLeaf()));
            return Range.NONE;
        }
        // If end is bad, guess based on start
        if (end == -1) {
            end = start + path.getLeaf().toString().length();
        }

        if (path.getLeaf() instanceof ClassTree) {
            var cls = (ClassTree) path.getLeaf();

            // If class has annotations, skip over them
            if (!cls.getModifiers().getAnnotations().isEmpty())
                start = (int) pos.getEndPosition(root, cls.getModifiers());

            // Find position of class name
            var name = cls.getSimpleName().toString();
            start = indexOf(contents, name, start);
            if (start == -1) {
                LOG.warning(String.format("Couldn't find identifier `%s` in `%s`", name, path.getLeaf()));
                return Range.NONE;
            }
            end = start + name.length();
        }
        if (path.getLeaf() instanceof MethodTree) {
            var method = (MethodTree) path.getLeaf();

            // If method has annotations, skip over them
            if (!method.getModifiers().getAnnotations().isEmpty())
                start = (int) pos.getEndPosition(root, method.getModifiers());

            // Find position of method name
            var name = method.getName().toString();
            if (name.equals("<init>")) {
                name = className(path);
            }
            start = indexOf(contents, name, start);
            if (start == -1) {
                LOG.warning(String.format("Couldn't find identifier `%s` in `%s`", name, path.getLeaf()));
                return Range.NONE;
            }
            end = start + name.length();
        }
        if (path.getLeaf() instanceof VariableTree) {
            var field = (VariableTree) path.getLeaf();

            // If field has annotations, skip over them
            if (!field.getModifiers().getAnnotations().isEmpty())
                start = (int) pos.getEndPosition(root, field.getModifiers());

            // Find position of method name
            var name = field.getName().toString();
            start = indexOf(contents, name, start);
            if (start == -1) {
                LOG.warning(String.format("Couldn't find identifier `%s` in `%s`", name, path.getLeaf()));
                return Range.NONE;
            }
            end = start + name.length();
        }
        if (path.getLeaf() instanceof MemberSelectTree) {
            var member = (MemberSelectTree) path.getLeaf();
            var name = member.getIdentifier().toString();
            start = indexOf(contents, name, start);
            if (start == -1) {
                LOG.warning(String.format("Couldn't find identifier `%s` in `%s`", name, path.getLeaf()));
                return Range.NONE;
            }
            end = start + name.length();
        }
        var startLine = (int) lines.getLineNumber(start);
        var startCol = (int) lines.getColumnNumber(start);
        var endLine = (int) lines.getLineNumber(end);
        var endCol = (int) lines.getColumnNumber(end);
        var range = new Range(new Position(startLine - 1, startCol - 1), new Position(endLine - 1, endCol - 1));

        return range;
    }

    private static int indexOf(CharSequence contents, String name, int start) {
        var matcher = Pattern.compile("\\b" + name + "\\b").matcher(contents);
        if (matcher.find(start)) {
            return matcher.start();
        }
        return -1;
    }

    private static final DocCommentTree EMPTY_DOC = makeEmptyDoc();

    private static DocCommentTree makeEmptyDoc() {
        var file = new SourceFileObject(Paths.get("/Foo.java"), "/** */ class Foo { }", Instant.now());
        var task =
                (JavacTask)
                        COMPILER.getTask(
                                null,
                                FILE_MANAGER,
                                Parser::ignoreError,
                                List.of(),
                                null,
                                Collections.singletonList(file));
        var docs = DocTrees.instance(task);
        CompilationUnitTree root;
        try {
            root = task.parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        class FindEmptyDoc extends TreePathScanner<Void, Void> {
            DocCommentTree found;

            @Override
            public Void visitClass(ClassTree t, Void __) {
                found = docs.getDocCommentTree(getCurrentPath());
                return null;
            }
        }
        var find = new FindEmptyDoc();
        find.scan(root, null);
        return Objects.requireNonNull(find.found);
    }

    private static void ignoreError(javax.tools.Diagnostic<? extends JavaFileObject> __) {
        // Too noisy, this only comes up in parse tasks which tend to be less important
        // LOG.warning(err.getMessage(Locale.getDefault()));
    }

    static String describeTree(Tree leaf) {
        if (leaf instanceof MethodTree) {
            var method = (MethodTree) leaf;
            var params = new StringJoiner(", ");
            for (var p : method.getParameters()) {
                params.add(p.getType() + " " + p.getName());
            }
            return method.getName() + "(" + params + ")";
        }
        if (leaf instanceof ClassTree) {
            var cls = (ClassTree) leaf;
            return "class " + cls.getSimpleName();
        }
        if (leaf instanceof BlockTree) {
            var block = (BlockTree) leaf;
            return String.format("{ ...%d lines... }", block.getStatements().size());
        }
        return leaf.toString();
    }

    List<String> accessibleClasses(String partialName, String fromPackage) {
        var toPackage = Objects.toString(root.getPackageName(), "");
        var samePackage = fromPackage.equals(toPackage) || toPackage.isEmpty();
        var result = new ArrayList<String>();
        for (var t : root.getTypeDecls()) {
            if (!(t instanceof ClassTree)) continue;
            var cls = (ClassTree) t;
            // If class is not accessible, skip it
            var isPublic = cls.getModifiers().getFlags().contains(Modifier.PUBLIC);
            if (!samePackage && !isPublic) continue;
            // If class doesn't match partialName, skip it
            var name = cls.getSimpleName().toString();
            if (!StringSearch.matchesPartialName(name, partialName)) continue;
            if (root.getPackageName() != null) {
                name = root.getPackageName() + "." + name;
            }
            result.add(name);
        }
        return result;
    }

    private static String prune(
            CompilationUnitTree root,
            SourcePositions pos,
            StringBuilder buffer,
            long[] offsets,
            boolean eraseAfterCursor) {
        class Scan extends TreeScanner<Void, Void> {
            boolean erasedAfterCursor = !eraseAfterCursor;

            boolean containsCursor(Tree node) {
                var start = pos.getStartPosition(root, node);
                var end = pos.getEndPosition(root, node);
                for (var cursor : offsets) {
                    if (start <= cursor && cursor <= end) {
                        return true;
                    }
                }
                return false;
            }

            boolean anyContainsCursor(Collection<? extends Tree> nodes) {
                for (var n : nodes) {
                    if (containsCursor(n)) return true;
                }
                return false;
            }

            long lastCursorIn(Tree node) {
                var start = pos.getStartPosition(root, node);
                var end = pos.getEndPosition(root, node);
                long last = -1;
                for (var cursor : offsets) {
                    if (start <= cursor && cursor <= end) {
                        last = cursor;
                    }
                }
                if (last == -1) {
                    throw new RuntimeException(
                            String.format(
                                    "No cursor in %s is between %d and %d", Arrays.toString(offsets), start, end));
                }
                return last;
            }

            @Override
            public Void visitImport(ImportTree node, Void __) {
                // Erase 'static' keyword so autocomplete works better
                if (containsCursor(node) && node.isStatic()) {
                    var start = (int) pos.getStartPosition(root, node);
                    start = buffer.indexOf("static", start);
                    var end = start + "static".length();
                    erase(buffer, start, end);
                }
                return super.visitImport(node, null);
            }

            @Override
            public Void visitSwitch(SwitchTree node, Void __) {
                if (containsCursor(node)) {
                    // Prevent the enclosing block from erasing the closing } of the switch
                    erasedAfterCursor = true;
                }
                return super.visitSwitch(node, null);
            }

            @Override
            public Void visitBlock(BlockTree node, Void __) {
                if (containsCursor(node)) {
                    super.visitBlock(node, null);
                    // When we find the deepest block that includes the cursor
                    if (!erasedAfterCursor) {
                        var cursor = lastCursorIn(node);
                        var start = cursor;
                        var end = pos.getEndPosition(root, node);
                        if (end >= buffer.length()) end = buffer.length() - 1;
                        // Find the next line
                        while (start < end && buffer.charAt((int) start) != '\n') start++;
                        // Find the end of the block
                        while (end > start && buffer.charAt((int) end) != '}') end--;
                        // Erase from next line to end of block
                        erase(buffer, start, end - 1);
                        erasedAfterCursor = true;
                    }
                } else if (!node.getStatements().isEmpty()) {
                    var first = node.getStatements().get(0);
                    var last = node.getStatements().get(node.getStatements().size() - 1);
                    var start = pos.getStartPosition(root, first);
                    var end = pos.getEndPosition(root, last);
                    if (end >= buffer.length()) end = buffer.length() - 1;
                    erase(buffer, start, end);
                }
                return null;
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void nothing) {
                return super.scan(node.getErrorTrees(), nothing);
            }
        }

        new Scan().scan(root, null);

        var pruned = buffer.toString();
        // For debugging:
        if (false) {
            var file = Paths.get(root.getSourceFile().toUri());
            var out = file.resolveSibling(file.getFileName() + ".pruned");
            try {
                Files.writeString(out, pruned);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return pruned;
    }

    private static void erase(StringBuilder buffer, long start, long end) {
        for (int i = (int) start; i < end; i++) {
            switch (buffer.charAt(i)) {
                case '\r':
                case '\n':
                    break;
                default:
                    buffer.setCharAt(i, ' ');
            }
        }
    }

    String prune(long cursor) {
        var pos = Trees.instance(task).getSourcePositions();
        var buffer = new StringBuilder(contents);
        long[] cursors = {cursor};
        return prune(root, pos, buffer, cursors, true);
    }

    static Optional<Path> declaringFile(Element e) {
        // Find top-level type surrounding `to`
        LOG.info(String.format("...looking up declaring file of `%s`...", e));
        var top = topLevelDeclaration(e);
        if (!top.isPresent()) {
            LOG.warning("...no top-level type!");
            return Optional.empty();
        }
        // Find file by looking at package and class name
        LOG.info(String.format("...top-level type is %s", top.get()));
        var file = FileStore.findDeclaringFile(top.get());
        if (!file.isPresent()) {
            LOG.info(String.format("...couldn't find declaring file for type"));
            return Optional.empty();
        }
        return file;
    }

    private static Optional<TypeElement> topLevelDeclaration(Element e) {
        if (e == null) return Optional.empty();
        var parent = e;
        TypeElement result = null;
        while (parent.getEnclosingElement() != null) {
            if (parent instanceof TypeElement) result = (TypeElement) parent;
            parent = parent.getEnclosingElement();
        }
        return Optional.ofNullable(result);
    }

    static String className(TreePath t) {
        while (t != null) {
            if (t.getLeaf() instanceof ClassTree) {
                var cls = (ClassTree) t.getLeaf();
                return cls.getSimpleName().toString();
            }
            t = t.getParentPath();
        }
        return "";
    }

    private static final Logger LOG = Logger.getLogger("main");
}
