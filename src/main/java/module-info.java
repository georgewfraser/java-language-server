open module javacs {
    requires jdk.compiler;
    requires jdk.zipfs;
    requires java.logging;
    requires java.xml;
    requires gson;

    uses javax.tools.JavaCompiler;
    exports org.javacs.lsp;
}
