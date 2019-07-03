package org.javacs;

import org.javacs.lsp.*;

import javax.json.JsonValue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.logging.Logger;

class LanguageServerFixture {

    public static Path DEFAULT_WORKSPACE_ROOT = Paths.get("src/test/test-project/workspace").normalize();
    public static Path SIMPLE_WORKSPACE_ROOT = Paths.get("src/test/test-project/simple").normalize();

    static {
        Main.setRootFormat();
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
                    public void registerCapability(String method, JsonValue options) {}

                    @Override
                    public void customNotification(String method, JsonValue params) {}
                });
    }

    private static JavaLanguageServer getJavaLanguageServer(Path workspaceRoot, LanguageClient client) {
        var server = new JavaLanguageServer(client);
        var init = new InitializeParams();

        init.rootUri = workspaceRoot.toUri();
        server.initialize(init);
        server.initialized();

        return server;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
