package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.gson.JsonElement;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.javacs.lsp.LanguageClient;
import org.javacs.lsp.PublishDiagnosticsParams;
import org.javacs.lsp.Range;
import org.javacs.lsp.ShowMessageParams;
import org.javacs.markup.SemanticColors;
import org.junit.Test;

public class SemanticColorsTest {

    @Test
    public void colorType() {
        var found = colors("org/javacs/color/ColorExample.java");
        assertThat("colors field declaration", found, hasItem("virtualField:4:field"));
        assertThat("colors field reference", found, hasItem("virtualField:7:field"));
        assertThat("ignores method parameter declaration", found, not(hasItem("methodParameter:10:field")));
        assertThat("ignores method parameter reference", found, not(hasItem("methodParameter:11:field")));
        assertThat("colors static field declaration", found, hasItem("staticField:14:field"));
        assertThat("colors static field reference", found, hasItem("staticField:17:field"));
    }

    private SemanticColors colors;

    private final JavaLanguageServer server =
            LanguageServerFixture.getJavaLanguageServer(
                    LanguageServerFixture.DEFAULT_WORKSPACE_ROOT,
                    new LanguageClient() {
                        @Override
                        public void publishDiagnostics(PublishDiagnosticsParams params) {}

                        @Override
                        public void showMessage(ShowMessageParams params) {}

                        @Override
                        public void registerCapability(String method, JsonElement options) {}

                        @Override
                        public void customNotification(String method, JsonElement params) {
                            if (method.equals("java/colors")) {
                                colors = JsonHelper.GSON.fromJson(params, SemanticColors.class);
                            }
                        }
                    });

    protected List<String> colors(String file) {
        var path = Paths.get(FindResource.uri(file));
        colors = null;
        server.lint(Arrays.asList(path));
        var contents = FileStore.contents(path);
        var list = new ArrayList<String>();
        for (var range : colors.statics) {
            list.add(String.format("%s:%d:static", substring(contents, range), range.start.line + 1));
        }
        for (var range : colors.fields) {
            list.add(String.format("%s:%d:field", substring(contents, range), range.start.line + 1));
        }
        return list;
    }

    private String substring(String contents, Range range) {
        int start = 0, line = 0, character = 0;
        while (line < range.start.line || character < range.start.character) {
            if (contents.charAt(start) == '\n') {
                start++;
                line++;
                character = 0;
            } else {
                start++;
                character++;
            }
        }
        int end = start;
        while (line < range.end.line || character < range.end.character) {
            if (contents.charAt(end) == '\n') {
                end++;
                line++;
                character = 0;
            } else {
                end++;
                character++;
            }
        }
        return contents.substring(start, end);
    }
}
