package org.javacs;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import javax.lang.model.element.TypeElement;
import org.javacs.lsp.DidChangeTextDocumentParams;
import org.javacs.lsp.DidCloseTextDocumentParams;
import org.javacs.lsp.DidOpenTextDocumentParams;
import org.javacs.lsp.TextDocumentContentChangeEvent;

public class FileStore {

    private static final Set<Path> workspaceRoots = new HashSet<>();

    private static final Map<Path, VersionedContent> activeDocuments = new HashMap<>();

    /** javaSources[file] is the javaSources time of a .java source file. */
    // TODO organize by package name for speed of list(...)
    private static final TreeMap<Path, Info> javaSources = new TreeMap<>();

    private static class Info {
        final Instant modified;
        final String packageName;

        Info(Instant modified, String packageName) {
            this.modified = modified;
            this.packageName = packageName;
        }
    }

    static void setWorkspaceRoots(Set<Path> newRoots) {
        newRoots = normalize(newRoots);
        for (var root : workspaceRoots) {
            if (!newRoots.contains(root)) {
                workspaceRoots.removeIf(f -> f.startsWith(root));
            }
        }
        for (var root : newRoots) {
            if (!workspaceRoots.contains(root)) {
                addFiles(root);
            }
        }
        workspaceRoots.clear();
        workspaceRoots.addAll(newRoots);
    }

    private static Set<Path> normalize(Set<Path> newRoots) {
        var normalize = new HashSet<Path>();
        for (var root : newRoots) {
            normalize.add(root.toAbsolutePath().normalize());
        }
        return normalize;
    }

    private static void addFiles(Path root) {
        try {
            Files.walkFileTree(root, new FindJavaSources());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class FindJavaSources extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
                LOG.warning("Don't check " + dir + " for java sources");
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes _attrs) {
            if (isJavaFile(file)) {
                readInfoFromDisk(file);
            }
            return FileVisitResult.CONTINUE;
        }
    }

    static Collection<Path> all() {
        return javaSources.keySet();
    }

    static List<Path> list(String packageName) {
        var list = new ArrayList<Path>();
        for (var file : javaSources.keySet()) {
            if (javaSources.get(file).packageName.equals(packageName)) {
                list.add(file);
            }
        }
        return list;
    }

    public static Set<Path> sourceRoots() {
        var roots = new HashSet<Path>();
        for (var file : javaSources.keySet()) {
            var root = sourceRoot(file);
            if (root != null) {
                roots.add(root);
            }
        }
        return roots;
    }

    private static Path sourceRoot(Path file) {
        var info = javaSources.get(file);
        var parts = info.packageName.split("\\.");
        var dir = file.getParent();
        for (var i = parts.length - 1; i >= 0; i--) {
            var end = parts[i];
            if (dir.endsWith(end)) {
                dir = dir.getParent();
            } else {
                return null;
            }
        }
        return dir;
    }

    static boolean contains(Path file) {
        return isJavaFile(file) && javaSources.containsKey(file);
    }

    static Instant modified(Path file) {
        // If file is open, use last in-memory modification time
        if (activeDocuments.containsKey(file)) {
            return activeDocuments.get(file).modified;
        }
        // If we've never checked before, look up modified time on disk
        if (!javaSources.containsKey(file)) {
            readInfoFromDisk(file);
        }
        // Look up modified time from cache
        return javaSources.get(file).modified;
    }

    static String packageName(Path file) {
        // If we've never checked before, look up package name on disk
        if (!javaSources.containsKey(file)) {
            readInfoFromDisk(file);
        }
        // Look up package name from cache
        return javaSources.get(file).packageName;
    }

    public static String suggestedPackageName(Path file) {
        // Look in each parent directory of file
        for (var dir = file.getParent(); dir != null; dir = dir.getParent()) {
            // Try to find a sibling with a package declaration
            for (var sibling : javaSourcesIn(dir)) {
                if (sibling.equals(file)) continue;
                var packageName = packageName(sibling);
                if (packageName.isBlank()) continue;
                var relativePath = dir.relativize(file.getParent());
                var relativePackage = relativePath.toString().replace(File.separatorChar, '.');
                if (!relativePackage.isEmpty()) {
                    packageName = packageName + "." + relativePackage;
                }
                return packageName;
            }
        }
        return "";
    }

    private static List<Path> javaSourcesIn(Path dir) {
        var tail = javaSources.tailMap(dir, false);
        var list = new ArrayList<Path>();
        for (var file : tail.keySet()) {
            if (!file.startsWith(dir)) break;
            list.add(file);
        }
        return list;
    }

    static void externalCreate(Path file) {
        readInfoFromDisk(file);
    }

    static void externalChange(Path file) {
        readInfoFromDisk(file);
    }

    static void externalDelete(Path file) {
        javaSources.remove(file);
    }

    private static void readInfoFromDisk(Path file) {
        try {
            var time = Files.getLastModifiedTime(file).toInstant();
            var packageName = StringSearch.packageName(file);
            javaSources.put(file, new Info(time, packageName));
        } catch (NoSuchFileException e) {
            LOG.warning(e.getMessage());
            javaSources.remove(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void open(DidOpenTextDocumentParams params) {
        if (!isJavaFile(params.textDocument.uri)) return;
        var document = params.textDocument;
        var file = Paths.get(document.uri);
        activeDocuments.put(file, new VersionedContent(document.text, document.version));
    }

    static void change(DidChangeTextDocumentParams params) {
        if (!isJavaFile(params.textDocument.uri)) return;
        var document = params.textDocument;
        var file = Paths.get(document.uri);
        var existing = activeDocuments.get(file);
        if (document.version <= existing.version) {
            LOG.warning("Ignored change with version " + document.version + " <= " + existing.version);
            return;
        }
        var newText = existing.content;
        for (var change : params.contentChanges) {
            if (change.range == null) newText = change.text;
            else newText = patch(newText, change);
        }
        activeDocuments.put(file, new VersionedContent(newText, document.version));
    }

    static void close(DidCloseTextDocumentParams params) {
        if (!isJavaFile(params.textDocument.uri)) return;
        var file = Paths.get(params.textDocument.uri);
        activeDocuments.remove(file);
    }

    static Set<Path> activeDocuments() {
        return activeDocuments.keySet();
    }

    public static String contents(Path file) {
        if (!isJavaFile(file)) {
            throw new RuntimeException(file + " is not a java file");
        }
        if (activeDocuments.containsKey(file)) {
            return activeDocuments.get(file).content;
        }
        try {
            return Files.readString(file);
        } catch (NoSuchFileException e) {
            LOG.warning(e.getMessage());
            return "";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static InputStream inputStream(Path file) {
        var uri = file.toUri();
        if (activeDocuments.containsKey(uri)) {
            var string = activeDocuments.get(uri).content;
            var bytes = string.getBytes();
            return new ByteArrayInputStream(bytes);
        }
        try {
            return Files.newInputStream(file);
        } catch (NoSuchFileException e) {
            LOG.warning(e.getMessage());
            byte[] bs = {};
            return new ByteArrayInputStream(bs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static BufferedReader bufferedReader(Path file) {
        var uri = file.toUri();
        if (activeDocuments.containsKey(uri)) {
            var string = activeDocuments.get(uri).content;
            return new BufferedReader(new StringReader(string));
        }
        try {
            return Files.newBufferedReader(file);
        } catch (NoSuchFileException e) {
            LOG.warning(e.getMessage());
            return new BufferedReader(new StringReader(""));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static BufferedReader lines(Path file) {
        return bufferedReader(file);
    }

    /** Convert from line/column (1-based) to offset (0-based) */
    static int offset(String contents, int line, int column) {
        line--;
        column--;
        int cursor = 0;
        while (line > 0) {
            if (contents.charAt(cursor) == '\n') {
                line--;
            }
            cursor++;
        }
        return cursor + column;
    }

    private static String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            var range = change.range;
            var reader = new BufferedReader(new StringReader(sourceText));
            var writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.start.line) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.start.character; character++) {
                writer.write(reader.read());
            }

            // Write replacement text
            writer.write(change.text);

            // Skip replaced text
            if (change.range.start.line == change.range.end.line) {
                int chars = change.range.end.character - change.range.start.character;
                reader.skip(chars);
            } else {
                int lines = change.range.end.line - change.range.start.line;
                int chars = change.range.end.character;
                for (int lineSkip = 0; lineSkip < lines; lineSkip++) {
                    reader.readLine();                    
                }
                reader.skip(chars);
            }

            // Write remaining text
            while (true) {
                int next = reader.read();

                if (next == -1) return writer.toString();
                else writer.write(next);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isJavaFile(Path file) {
        var name = file.getFileName().toString();
        // We hide module-info.java from javac, because when javac sees module-info.java
        // it goes into "module mode" and starts looking for classes on the module class path.
        // This becomes evident when javac starts recompiling *way too much* on each task,
        // because it doesn't realize there are already up-to-date .class files.
        // The better solution would be for java-language server to detect the presence of module-info.java,
        // and go into its own "module mode" where it infers a module source path and a module class path.
        return name.endsWith(".java") && !Files.isDirectory(file) && !name.equals("module-info.java");
    }

    static boolean isJavaFile(URI uri) {
        return uri.getScheme().equals("file") && isJavaFile(Paths.get(uri));
    }

    static Optional<Path> findDeclaringFile(TypeElement el) {
        var qualifiedName = el.getQualifiedName().toString();
        var packageName = StringSearch.mostName(qualifiedName);
        var className = StringSearch.lastName(qualifiedName);
        // Fast path: look for text `class Foo` in file Foo.java
        for (var f : list(packageName)) {
            if (f.getFileName().toString().equals(className) && StringSearch.containsType(f, el)) {
                return Optional.of(f);
            }
        }
        // Slow path: look for text `class Foo` in any file in package
        for (var f : list(packageName)) {
            if (StringSearch.containsType(f, el)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    private static final Logger LOG = Logger.getLogger("main");
}

class VersionedContent {
    final String content;
    final int version;
    final Instant modified = Instant.now();

    VersionedContent(String content, int version) {
        Objects.requireNonNull(content, "content is null");
        this.content = content;
        this.version = version;
    }
}
