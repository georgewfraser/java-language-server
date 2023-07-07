package org.javacs;

import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.javacs.lsp.*;

public class LanguageServerFixture {

    public static Path DEFAULT_WORKSPACE_ROOT = Paths.get("src/test/examples/maven-project").normalize();
    public static Path SIMPLE_WORKSPACE_ROOT = Paths.get("src/test/examples/simple-project").normalize();

    static {
        Main.setRootFormat();
    }

    public static CompilerProvider getCompilerProvider() {
        return getJavaLanguageServer().compiler();
    }

    static JavaLanguageServer getJavaLanguageServer() {
        return getJavaLanguageServer(DEFAULT_WORKSPACE_ROOT, diagnostic -> LOG.info(diagnostic.message));
    }

    static JavaLanguageServer getJavaLanguageServer(Consumer<Diagnostic> onError) {
        return getJavaLanguageServer(DEFAULT_WORKSPACE_ROOT, onError);
    }

    static JavaLanguageServer getJavaLanguageServer(Path workspaceRoot, Consumer<Diagnostic> onError) {
        return getJavaLanguageServer(
                workspaceRoot,
                new LanguageClient() {
                    @Override
                    public void publishDiagnostics(PublishDiagnosticsParams params) {
                        params.diagnostics.forEach(onError);
                    }

                    @Override
                    public void showMessage(ShowMessageParams params) {}

                    @Override
                    public void registerCapability(String method, JsonElement options) {}

                    @Override
                    public void customNotification(String method, JsonElement params) {}
                });
    }

    static JavaLanguageServer getJavaLanguageServer(Path workspaceRoot, LanguageClient client) {
        FileStore.reset();

        var server = new JavaLanguageServer(client);
        var init = new InitializeParams();

        init.rootUri = workspaceRoot.toUri();
        server.initialize(init);
        server.initialized();

        return server;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
