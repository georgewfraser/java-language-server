package org.javacs;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class SourceFileObject implements JavaFileObject {
    /** path is the absolute path to this file on disk */
    final Path path;
    /** contents is the text in this file, or null if we should use the text in FileStore */
    final String contents;
    /** if contents is set, the modified version of contents in monotonic nanoseconds */
    final long modified;
    private static final AtomicLong LAST_NOW = new AtomicLong();

    public SourceFileObject(Path path) {
        this(path, null, 0);
    }

    public SourceFileObject(Path path, String contents, long modified) {
        if (!FileStore.isJavaFile(path)) throw new RuntimeException(path + " is not a java source");
        this.path = path;
        this.contents = contents;
        this.modified = modified;
    }

    public static long now() {
        while (true) {
            var previous = LAST_NOW.get();
            var candidate = Math.max(System.nanoTime(), previous + 1);
            if (LAST_NOW.compareAndSet(previous, candidate)) {
                return candidate;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other.getClass() != SourceFileObject.class) return false;
        var that = (SourceFileObject) other;
        return this.path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

    @Override
    public Kind getKind() {
        var name = path.getFileName().toString();
        return kindFromExtension(name);
    }

    private static Kind kindFromExtension(String name) {
        for (var candidate : Kind.values()) {
            if (name.endsWith(candidate.extension)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return path.getFileName().toString().equals(simpleName + kind.extension);
    }

    @Override
    public NestingKind getNestingKind() {
        return null;
    }

    @Override
    public Modifier getAccessLevel() {
        return null;
    }

    @Override
    public URI toUri() {
        return path.toUri();
    }

    @Override
    public String getName() {
        return path.toString();
    }

    @Override
    public InputStream openInputStream() {
        if (contents != null) {
            var bytes = contents.getBytes();
            return new ByteArrayInputStream(bytes);
        }
        return FileStore.inputStream(path);
    }

    @Override
    public OutputStream openOutputStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) {
        if (contents != null) {
            return new StringReader(contents);
        }
        return FileStore.bufferedReader(path);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        if (contents != null) {
            return contents;
        }
        return FileStore.contents(path);
    }

    @Override
    public Writer openWriter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLastModified() {
        if (contents != null) {
            return modified;
        }
        var fileModified = FileStore.modified(path);
        if (fileModified == null) return 0;
        return fileModified.toEpochMilli();
    }

    @Override
    public boolean delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
