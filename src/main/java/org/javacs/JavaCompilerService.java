package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import javax.tools.*;

// TODO eliminate uses of URI in favor of Path
public class JavaCompilerService {
    // Not modifiable! If you want to edit these, you need to create a new instance
    final Set<Path> sourcePath, classPath, docPath;
    final Supplier<Set<Path>> allJavaFiles;
    final JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
    final Docs docs;
    final ClassSource jdkClasses = Classes.jdkTopLevelClasses(), classPathClasses;
    // Diagnostics from the last compilation task
    final List<Diagnostic<? extends JavaFileObject>> diags = new ArrayList<>();
    // Use the same file manager for multiple tasks, so we don't repeatedly re-compile the same files
    // TODO intercept files that aren't in the batch and erase method bodies so compilation is faster
    final StandardJavaFileManager fileManager =
            new FileManagerWrapper(compiler.getStandardFileManager(diags::add, null, Charset.defaultCharset()));

    public JavaCompilerService(
            Set<Path> sourcePath, Supplier<Set<Path>> allJavaFiles, Set<Path> classPath, Set<Path> docPath) {
        System.err.println("Source path:");
        for (var p : sourcePath) {
            System.err.println("  " + p);
        }
        System.err.println("Class path:");
        for (var p : classPath) {
            System.err.println("  " + p);
        }
        System.err.println("Doc path:");
        for (var p : docPath) {
            System.err.println("  " + p);
        }
        // sourcePath and classPath can't actually be modified, because JavaCompiler remembers them from task to task
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.allJavaFiles = allJavaFiles;
        this.classPath = Collections.unmodifiableSet(classPath);
        this.docPath = Collections.unmodifiableSet(docPath);
        var docSourcePath = new HashSet<Path>();
        docSourcePath.addAll(sourcePath);
        docSourcePath.addAll(docPath);
        this.docs = new Docs(docSourcePath);
        this.classPathClasses = Classes.classPathTopLevelClasses(classPath);
    }

    /** Combine source path or class path entries using the system separator, for example ':' in unix */
    private static String joinPath(Collection<Path> classOrSourcePath) {
        return classOrSourcePath.stream().map(p -> p.toString()).collect(Collectors.joining(File.pathSeparator));
    }

    static List<String> options(Set<Path> sourcePath, Set<Path> classPath) {
        var list = new ArrayList<String>();

        Collections.addAll(list, "-classpath", joinPath(classPath));
        Collections.addAll(list, "-sourcepath", joinPath(sourcePath));
        // Collections.addAll(list, "-verbose");
        Collections.addAll(list, "-proc:none");
        Collections.addAll(list, "-g");
        // You would think we could do -Xlint:all,
        // but some lints trigger fatal errors in the presence of parse errors
        Collections.addAll(
                list,
                "-Xlint:cast",
                "-Xlint:deprecation",
                "-Xlint:empty",
                "-Xlint:fallthrough",
                "-Xlint:finally",
                "-Xlint:path",
                "-Xlint:unchecked",
                "-Xlint:varargs",
                "-Xlint:static");

        return list;
    }

    String pathBasedPackageName(Path javaFile) {
        if (!javaFile.getFileName().toString().endsWith(".java")) {
            LOG.warning(javaFile + " does not end in .java");
            return "???";
        }
        for (var dir : sourcePath) {
            if (!javaFile.startsWith(dir)) continue;
            var packageDir = javaFile.getParent();
            var relative = dir.relativize(packageDir);
            return relative.toString().replace('/', '.');
        }
        LOG.warning(javaFile + " is not in the source path " + sourcePath);
        return "???";
    }

    public Docs docs() {
        return docs;
    }

    public ParseFile parseFile(URI file, String contents) {
        return new ParseFile(this, file, contents);
    }

    public CompileFocus compileFocus(URI file, String contents, int line, int character) {
        return new CompileFocus(this, file, contents, line, character);
    }

    public CompileFile compileFile(URI file, String contents) {
        return new CompileFile(this, file, contents);
    }

    public CompileBatch compileBatch(Collection<URI> uris) {
        return compileBatch(uris, ReportProgress.EMPTY);
    }

    public CompileBatch compileBatch(Collection<URI> uris, ReportProgress progress) {
        var files = new ArrayList<File>();
        for (var p : uris) files.add(new File(p));
        var sources = fileManager.getJavaFileObjectsFromFiles(files);
        var list = new ArrayList<JavaFileObject>();
        for (var s : sources) list.add(s);
        return new CompileBatch(this, list, progress);
    }

    public CompileBatch compileBatch(List<? extends JavaFileObject> sources) {
        return new CompileBatch(this, sources, ReportProgress.EMPTY);
    }

    public List<Diagnostic<? extends JavaFileObject>> reportErrors(Collection<URI> uris) {
        LOG.info(String.format("Report errors in %d files...", uris.size()));

        var options = options(sourcePath, classPath);
        // Construct list of sources
        var files = new ArrayList<File>();
        for (var p : uris) files.add(new File(p));
        var sources = fileManager.getJavaFileObjectsFromFiles(files);
        // Create task
        var task =
                (JavacTask) compiler.getTask(null, fileManager, diags::add, options, Collections.emptyList(), sources);
        // Print timing information for optimization
        var profiler = new Profiler();
        task.addTaskListener(profiler);
        // Run compilation
        diags.clear();
        try {
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        profiler.print();

        LOG.info(String.format("...found %d errors", diags.size()));

        return Collections.unmodifiableList(new ArrayList<>(diags));
    }

    private static class ContainsImportKey {
        final Path file;
        final String toPackage, toClass;

        ContainsImportKey(Path file, String toPackage, String toClass) {
            this.file = file;
            this.toPackage = toPackage;
            this.toClass = toClass;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ContainsImportKey)) return false;
            var that = (ContainsImportKey) other;
            if (!Objects.equals(this.file, that.file)) return false;
            if (!Objects.equals(this.toPackage, that.toPackage)) return false;
            if (!Objects.equals(this.toClass, that.toClass)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, toPackage, toClass);
        }
    }

    private static Cache<ContainsImportKey, Boolean> cacheContainsImport =
            new Cache<>(k -> containsImport(k.toPackage, k.toClass, k.file), k -> k.file);

    static boolean containsImport(String toPackage, String toClass, Path file) {
        if (toPackage.isEmpty()) return true;
        var samePackage = Pattern.compile("^package +" + toPackage + ";");
        var importClass = Pattern.compile("^import +" + toPackage + "\\." + toClass + ";");
        var importStar = Pattern.compile("^import +" + toPackage + "\\.\\*;");
        var importStatic = Pattern.compile("^import +static +" + toPackage + "\\." + toClass);
        var startOfClass = Pattern.compile("^[\\w ]*class +\\w+");
        // TODO this needs to use open text if available
        try (var read = Files.newBufferedReader(file)) {
            while (true) {
                var line = read.readLine();
                if (line == null) return false;
                if (startOfClass.matcher(line).find()) return false;
                if (samePackage.matcher(line).find()) return true;
                if (importClass.matcher(line).find()) return true;
                if (importStar.matcher(line).find()) return true;
                if (importStatic.matcher(line).find()) return true;
                if (importClass.matcher(line).find()) return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ContainsWordKey {
        final Path file;
        final String word;

        ContainsWordKey(Path file, String word) {
            this.file = file;
            this.word = word;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ContainsWordKey)) return false;
            var that = (ContainsWordKey) other;
            if (!Objects.equals(this.file, that.file)) return false;
            if (!Objects.equals(this.word, that.word)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(file, word);
        }
    }

    private static Cache<ContainsWordKey, Boolean> cacheContainsWord =
            new Cache<>(k -> containsWord(k.word, k.file), k -> k.file);

    static boolean containsWord(String name, Path file) {
        if (!name.matches("\\w*")) throw new RuntimeException(String.format("`%s` is not a word", name));
        // TODO this needs to use open text if available
        return Parser.containsWord(file, name);
    }

    // TODO if we change this to Ptr, we could cache on a per-file basis
    public Set<URI> potentialDefinitions(Element to) {
        if (to instanceof ExecutableElement) {
            // TODO this needs to use open text if available
            // Check if the file imports `to` and contains the name of `to`
            var hasWord = matchesName(to);
            // Parse each file and check if the syntax tree is consistent with a definition of `to`
            // This produces some false positives, but parsing is much faster than compiling,
            // so it's an effective optimization
            var findName = simpleName(to);
            var checkTree = new HashSet<URI>();
            class FindMethod extends TreePathScanner<Void, Void> {
                @Override
                public Void visitMethod(MethodTree t, Void __) {
                    // TODO try to disprove that this is a reference by looking at obvious special cases, like is the
                    // simple name of the type different?
                    if (t.getName().contentEquals(findName)) {
                        var uri = getCurrentPath().getCompilationUnit().getSourceFile().toUri();
                        checkTree.add(uri);
                    }
                    return super.visitMethod(t, null);
                }
            }
            for (var f : hasWord) {
                var root = Parser.parse(Paths.get(f));
                new FindMethod().scan(root, null);
            }
            LOG.info(String.format("...%d files contain method `%s`", checkTree.size(), findName));
            return checkTree;
        } else {
            var files = new HashSet<URI>();
            declaringFile(to).ifPresent(files::add);
            return files;
        }
    }

    // TODO if we change this to Ptr, we could cache on a per-file basis
    public Set<URI> potentialReferences(Element to) {
        var findName = simpleName(to);
        var isField = to instanceof VariableElement && to.getEnclosingElement() instanceof TypeElement;
        var isType = to instanceof TypeElement;
        if (isField || isType) {
            class FindVar extends TreePathScanner<Void, Set<URI>> {
                void add(Set<URI> found) {
                    var uri = getCurrentPath().getCompilationUnit().getSourceFile().toUri();
                    found.add(uri);
                }

                boolean method() {
                    return getCurrentPath().getParentPath().getLeaf() instanceof MethodInvocationTree;
                }

                @Override
                public Void visitIdentifier(IdentifierTree t, Set<URI> found) {
                    // TODO try to disprove that this is a reference by looking at obvious special cases, like is the
                    // simple name of the type different?
                    if (t.getName().contentEquals(findName) && !method()) add(found);
                    return super.visitIdentifier(t, found);
                }

                @Override
                public Void visitMemberSelect(MemberSelectTree t, Set<URI> found) {
                    if (t.getIdentifier().contentEquals(findName) && !method()) add(found);
                    return super.visitMemberSelect(t, found);
                }
            }
            return scanForPotentialReferences(to, new FindVar());
        } else if (to instanceof ExecutableElement) {
            class FindMethod extends TreePathScanner<Void, Set<URI>> {
                void add(Set<URI> found) {
                    var uri = getCurrentPath().getCompilationUnit().getSourceFile().toUri();
                    found.add(uri);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree t, Set<URI> found) {
                    // TODO try to disprove that this is a reference by looking at obvious special cases, like is the
                    // simple name of the type different?
                    var method = t.getMethodSelect();
                    // outer.method()
                    if (method instanceof MemberSelectTree) {
                        var select = (MemberSelectTree) method;
                        if (select.getIdentifier().contentEquals(findName)) add(found);
                    }
                    // method()
                    if (method instanceof IdentifierTree) {
                        var id = (IdentifierTree) method;
                        if (id.getName().contentEquals(findName)) add(found);
                    }
                    // Check other parts
                    return super.visitMethodInvocation(t, found);
                }

                @Override
                public Void visitMemberReference(MemberReferenceTree t, Set<URI> found) {
                    if (t.getName().contentEquals(findName)) add(found);
                    return super.visitMemberReference(t, found);
                }
            }
            return scanForPotentialReferences(to, new FindMethod());
        } else {
            var files = new HashSet<URI>();
            declaringFile(to).ifPresent(files::add);
            return files;
        }
    }

    private static CharSequence simpleName(Element e) {
        if (e.getSimpleName().contentEquals("<init>")) {
            return e.getEnclosingElement().getSimpleName();
        }
        return e.getSimpleName();
    }

    private Set<URI> scanForPotentialReferences(Element to, TreePathScanner<Void, Set<URI>> scan) {
        // TODO this needs to use open text if available
        // Check if the file imports `to` and contains the name of `to`
        var hasWord = matchesName(to);
        // Parse each file and check if the syntax tree is consistent with a definition of `to`
        // This produces some false positives, but parsing is much faster than compiling,
        // so it's an effective optimization
        var found = new HashSet<URI>();
        for (var f : hasWord) {
            var root = Parser.parse(Paths.get(f));
            scan.scan(root, found);
        }
        LOG.info(
                String.format(
                        "...%d files contain syntax that might be a reference to `%s`",
                        found.size(), to.getSimpleName()));
        return found;
    }

    private Optional<URI> declaringFile(Element e) {
        // Find top-level type surrounding `to`
        LOG.info(String.format("Lookup up declaring file of `%s`...", e));
        var top = topLevelDeclaration(e);
        if (!top.isPresent()) {
            LOG.warning("...no top-level type!");
            return Optional.empty();
        }
        // Find file by looking at package and class name
        LOG.info(String.format("...top-level type is %s", top.get()));
        var file = findDeclaringFile(top.get());
        if (!file.isPresent()) {
            LOG.info(String.format("...couldn't find declaring file for type"));
            return Optional.empty();
        }
        return file;
    }

    private Optional<TypeElement> topLevelDeclaration(Element e) {
        if (e == null) return Optional.empty();
        var parent = e;
        TypeElement result = null;
        while (parent.getEnclosingElement() != null) {
            if (parent instanceof TypeElement) result = (TypeElement) parent;
            parent = parent.getEnclosingElement();
        }
        return Optional.ofNullable(result);
    }

    /** Find the file `e` was declared in */
    private Optional<URI> findDeclaringFile(TypeElement e) {
        var name = e.getQualifiedName().toString();
        var lastDot = name.lastIndexOf('.');
        var packageName = lastDot == -1 ? "" : name.substring(0, lastDot);
        var className = name.substring(lastDot + 1);
        // First, look for a file named [ClassName].java
        var packagePath = Paths.get(packageName.replace('.', File.separatorChar));
        var publicClassPath = packagePath.resolve(className + ".java");
        for (var root : sourcePath) {
            var absPath = root.resolve(publicClassPath);
            if (Files.exists(absPath) && containsTopLevelDeclaration(absPath, className)) {
                return Optional.of(absPath.toUri());
            }
        }
        // Then, look for a secondary declaration in all java files in the package
        var isPublic = e.getModifiers().contains(Modifier.PUBLIC);
        if (!isPublic) {
            for (var root : sourcePath) {
                var absDir = root.resolve(packagePath);
                try {
                    var foundFile =
                            Files.list(absDir).filter(f -> containsTopLevelDeclaration(f, className)).findFirst();
                    if (foundFile.isPresent()) return foundFile.map(Path::toUri);
                } catch (IOException err) {
                    throw new RuntimeException(err);
                }
            }
        }
        return Optional.empty();
    }

    private boolean containsTopLevelDeclaration(Path file, String simpleClassName) {
        var find = Pattern.compile("\\b(class|interface|enum) +" + simpleClassName + "\\b");
        try (var lines = Files.newBufferedReader(file)) {
            var line = lines.readLine();
            while (line != null) {
                if (find.matcher(line).find()) return true;
                line = lines.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private List<URI> matchesName(Element to) {
        LOG.info(String.format("Find potential references to `%s`...", to));

        // Check all files on source path
        var allFiles = allJavaFiles.get();
        LOG.info(String.format("...check %d files on the source path", allFiles.size()));

        // Figure out which files import `to`, explicitly or implicitly
        var toPackage = packageName(to);
        var toClass = className(to);
        var hasImport = new ArrayList<Path>();
        for (var file : allFiles) {
            if (cacheContainsImport.get(new ContainsImportKey(file, toPackage, toClass))) {
                hasImport.add(file);
            }
        }
        LOG.info(String.format("...%d files import %s.%s", hasImport.size(), toPackage, toClass));

        // Figure out which of those files have the word `to`
        var name = to.getSimpleName().toString();
        if (name.equals("<init>")) name = to.getEnclosingElement().getSimpleName().toString();
        var hasWord = new ArrayList<URI>();
        for (var file : hasImport) {
            if (cacheContainsWord.get(new ContainsWordKey(file, name))) {
                hasWord.add(file.toUri());
            }
        }
        LOG.info(String.format("...%d files contain the word `%s`", hasWord.size(), name));

        return hasWord;
    }

    public static String packageName(Element e) {
        while (e != null) {
            if (e instanceof PackageElement) {
                var pkg = (PackageElement) e;
                return pkg.getQualifiedName().toString();
            }
            e = e.getEnclosingElement();
        }
        return "";
    }

    public static String className(Element e) {
        while (e != null) {
            if (e instanceof TypeElement) {
                var type = (TypeElement) e;
                return type.getSimpleName().toString();
            }
            e = e.getEnclosingElement();
        }
        return "";
    }

    public static String className(TreePath t) {
        while (t != null) {
            if (t.getLeaf() instanceof ClassTree) {
                var cls = (ClassTree) t.getLeaf();
                return cls.getSimpleName().toString();
            }
            t = t.getParentPath();
        }
        return "";
    }

    public static Optional<String> memberName(TreePath t) {
        while (t != null) {
            if (t.getLeaf() instanceof ClassTree) {
                return Optional.empty();
            } else if (t.getLeaf() instanceof MethodTree) {
                var method = (MethodTree) t.getLeaf();
                var name = method.getName().toString();
                return Optional.of(name);
            } else if (t.getLeaf() instanceof VariableTree) {
                var field = (VariableTree) t.getLeaf();
                var name = field.getName().toString();
                return Optional.of(name);
            }
            t = t.getParentPath();
        }
        return Optional.empty();
    }

    public List<TreePath> findSymbols(String query, int limit) {
        LOG.info(String.format("Searching for `%s`...", query));

        var result = new ArrayList<TreePath>();
        var files = allJavaFiles.get();
        var checked = 0;
        var parsed = 0;
        for (var file : files) {
            checked++;
            // First do a fast check if the query matches anything in a file
            if (!Parser.containsWordMatching(file, query)) continue;
            // Parse the file and check class members for matches
            LOG.info(String.format("...%s contains text matches", file.getFileName()));
            var parse = Parser.parse(file);
            var symbols = Parser.findSymbolsMatching(parse, query);
            parsed++;
            // If we confirm matches, add them to the results
            if (symbols.size() > 0) LOG.info(String.format("...found %d occurrences", symbols.size()));
            result.addAll(symbols);
            // If results are full, stop
            if (result.size() >= limit) break;
        }
        LOG.info(String.format("Found %d matches in %d/%d/%d files", result.size(), checked, parsed, files.size()));

        return result;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
