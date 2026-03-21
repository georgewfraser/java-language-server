package org.javacs;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.javacs.lsp.*;
import org.junit.Assert;

public class CompletionsBase {
    protected static JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    protected void refreshServer() {
        server = LanguageServerFixture.getJavaLanguageServer();
    }

    protected List<String> insertTemplate(String file, int row, int column) {
        var items = items(file, row, column);

        return items.stream().map(CompletionsBase::itemInsertTemplate).collect(Collectors.toList());
    }

    static String itemInsertTemplate(CompletionItem i) {
        var text = i.insertText;

        if (text == null) text = i.label;

        assert text != null : "Either insertText or label must be defined";

        return text;
    }

    static String itemFilterText(CompletionItem i) {
        var text = i.filterText;

        if (text == null) text = i.label;

        assert text != null : "Either insertText or label must be defined";

        return text;
    }

    protected List<String> label(String file, int row, int column) {
        var items = items(file, row, column);

        return items.stream().map(i -> i.label).collect(Collectors.toList());
    }

    protected List<String> insertText(String file, int row, int column) {
        var items = items(file, row, column);

        return items.stream().map(CompletionsBase::itemInsertText).collect(Collectors.toList());
    }

    protected List<String> filterText(String file, int row, int column) {
        var items = items(file, row, column);

        return items.stream().map(CompletionsBase::itemFilterText).collect(Collectors.toList());
    }

    protected List<String> detail(String file, int row, int column) {
        var items = items(file, row, column);
        var result = new ArrayList<String>();
        for (var i : items) {
            var resolved = resolve(i);
            result.add(resolved.detail);
        }
        return result;
    }

    protected Map<String, Integer> insertCount(String file, int row, int column) {
        var items = items(file, row, column);
        var result = new HashMap<String, Integer>();

        for (var each : items) {
            var key = itemInsertText(each);
            var count = result.getOrDefault(key, 0) + 1;

            result.put(key, count);
        }

        return result;
    }

    static String itemInsertText(CompletionItem i) {
        var text = i.insertText;

        if (text == null) text = i.label;

        assert text != null : "Either insertText or label must be defined";

        return text;
    }

    protected List<String> documentation(String file, int row, int column) {
        var items = items(file, row, column);

        return items.stream()
                .flatMap(
                        i -> {
                            if (i.documentation != null) return Stream.of(i.documentation.value.trim());
                            else return Stream.empty();
                        })
                .collect(Collectors.toList());
    }

    protected List<? extends CompletionItem> items(String file, int row, int column) {
        var uri = FindResource.uri(file);
        var position =
                new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(row - 1, column - 1));
        var maybe = server.completion(position);
        if (!maybe.isPresent()) {
            Assert.fail("no items");
        }
        return maybe.get().items;
    }

    protected CompletionItem resolve(CompletionItem item) {
        return server.resolveCompletionItem(item);
    }
}
