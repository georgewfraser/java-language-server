package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.javacs.completion.CompletionProvider;
import org.javacs.lsp.*;
import org.junit.Ignore;
import org.junit.Test;

public class CompletionsTest extends CompletionsBase {

    // TODO rename Autocomplete Complete because Autocomplete is long and ugly

    @Test
    public void completeClass() {
        refreshServer(); // TODO figure out how to get rid of this
        var file = "/org/javacs/example/CompleteClass.java";
        var suggestions = filterText(file, 5, 23);
        assertThat(suggestions, hasItems("staticMethod", "staticField", "class"));
        String[] not = {"instanceMethod", "instanceField"};
        for (var n : not) {
            assertThat(suggestions, not(hasItem(n)));
        }
    }

    @Test
    public void identifiers() {
        var file = "/org/javacs/example/CompleteIdentifiers.java";
        var suggestions = filterText(file, 15, 21);
        String[] expect = {
            "completeLocal",
            "completeParam",
            "completeOtherMethod",
            "completeInnerField",
            "completeOuterField",
            "completeOuterStatic",
        };
        assertThat(suggestions, hasItems(expect));
    }

    @Test
    public void identifiersInMiddle() {
        var file = "/org/javacs/example/CompleteInMiddle.java";
        var suggestions = filterText(file, 15, 21);
        String[] expect = {
            "completeLocal",
            "completeParam",
            "completeOtherMethod",
            "completeInnerField",
            "completeOuterField",
            "completeOuterStatic",
        };
        assertThat(suggestions, hasItems(expect));
    }

    @Test
    public void expression() {
        var file = "/org/javacs/example/CompleteExpression.java";
        var suggestions = filterText(file, 5, 37);
        assertThat(suggestions, hasItems("instanceMethod", "equals"));
        assertThat(suggestions, not(hasItem("create")));
        assertThat(suggestions, not(hasItem("<init>")));
    }

    @Test
    public void imports() {
        var file = "/org/javacs/example/CompleteImports.java";
        var suggestions = filterText(file, 3, 18);
        assertThat(suggestions, hasSize(greaterThan(CompletionProvider.MAX_COMPLETION_ITEMS)));
    }

    @Test
    public void staticMember() {
        var file = "/org/javacs/example/AutocompleteStaticMember.java";
        var suggestions = filterText(file, 5, 38);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "class"));
        String[] nots = {"testField", "testMethod", "getClass"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void staticReference() {
        var file = "/org/javacs/example/AutocompleteStaticReference.java";
        var suggestions = filterText(file, 7, 48);
        assertThat(suggestions, hasItems("testMethod", "testMethodStatic", "new"));
        assertThat(suggestions, not(hasItem("class")));
    }

    @Test
    public void member() {
        var file = "/org/javacs/example/AutocompleteMember.java";
        var suggestions = filterText(file, 5, 14);
        String[] statics = {
            "testFieldStatic",
            "testMethodStatic",
            "testFieldStaticPrivate",
            "testMethodStaticPrivate",
            "class",
            "AutocompleteMember"
        };
        for (var not : statics) {
            assertThat("excludes static members", suggestions, not(hasItem(not)));
        }
        String[] virtuals = {"testFields", "testMethods", "testFieldsPrivate", "testMethodsPrivate", "getClass"};
        assertThat("includes non-static members", suggestions, hasItems(virtuals));
        assertThat("excludes constructors", suggestions, not(hasItem(startsWith("AutocompleteMember"))));
    }

    @Test
    public void inheritedMembers() {
        var file = "/org/javacs/example/CompleteInheritedMembers.java";
        var suggestions = filterText(file, 5, 15);
        assertThat(suggestions, hasItems("superMethod", "subMethod"));
    }

    @Test
    public void enumMapMembers() {
        var suggestions = filterText("/org/javacs/example/CompleteEnumMap.java", 9, 13);
        String[] expect = {
            "clear",
            "clone",
            "containsKey",
            "containsValue",
            "entrySet",
            "equals",
            "get",
            "hashCode",
            "keySet",
            "put",
            "putAll",
            "remove",
            "size",
            "values"
        };
        assertThat(suggestions, hasItems(expect));
    }

    @Test
    public void fieldFromInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 8, 10);
        assertThat(suggestions, hasItems("testFields", "testFieldStatic", "testMethods", "testMethodStatic"));
    }

    @Test
    public void thisDotFieldFromInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 9, 15);
        assertThat(suggestions, hasItems("testFields", "testMethods"));
        String[] nots = {"testFieldStatic", "testMethodStatic"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classDotFieldFromInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 10, 30);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        String[] nots = {"testFields", "testMethods"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void fieldFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 22, 10);
        String[] expect = {"testFields", "testFieldStatic", "testMethods", "testMethodStatic", "testArguments"};
        assertThat(suggestions, hasItems(expect));
    }

    @Test
    public void thisDotFieldFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 23, 15);
        assertThat(suggestions, hasItems("testFields", "testMethods"));
        String[] nots = {"testFieldStatic", "testMethodStatic", "testArguments"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classDotFieldFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 24, 30);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        String[] nots = {"testFields", "testMethods", "testArguments"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void thisRefMethodFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 25, 59);
        assertThat(suggestions, hasItems("testMethods"));
        String[] nots = {"testFields", "testFieldStatic", "testMethodStatic"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classRefMethodFromMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 26, 74);
        assertThat(suggestions, hasItems("testMethodStatic", "testMethods"));
        String[] nots = {"testFields", "testFieldStatic"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    @Ignore // javac doesn't give us helpful info about the fact that static initializers are static
    public void fieldFromStaticInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 16, 10);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        String[] nots = {"testFields", "testMethods"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classDotFieldFromStaticInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 17, 30);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        String[] nots = {"testFields", "testMethods"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classRefFieldFromStaticInitBlock() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 17, 30);
        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        String[] nots = {"testFields", "testMethods"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void fieldFromStaticMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 30, 10);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "testArguments"));
        String[] nots = {"testFields", "testMethods"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classDotFieldFromStaticMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 31, 30);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic"));
        String[] nots = {"testFields", "testMethods", "testArguments"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void classRefFieldFromStaticMethod() {
        var file = "/org/javacs/example/AutocompleteMembers.java";
        var suggestions = filterText(file, 17, 30);
        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        String[] nots = {"testFields", "testMethods"};
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    private static String sortText(CompletionItem i) {
        if (i.sortText != null) return i.sortText;
        else return i.label;
    }

    @Test
    public void otherMethod() {
        var file = "/org/javacs/example/AutocompleteOther.java";
        var suggestions = filterText(file, 5, 34);
        String[] nots = {
            "testFieldStatic",
            "testMethodStatic",
            "class",
            "testFieldStaticPrivate",
            "testMethodStaticPrivate",
            "testFieldsPrivate",
            "testMethodsPrivate"
        };
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
        assertThat(suggestions, hasItems("testFields", "testMethods", "getClass"));
    }

    @Test
    public void otherStatic() {
        var file = "/org/javacs/example/AutocompleteOther.java";
        var suggestions = filterText(file, 7, 28);
        assertThat(suggestions, hasItems("testFieldStatic", "testMethodStatic", "class"));
        String[] nots = {
            "testFieldStaticPrivate",
            "testMethodStaticPrivate",
            "testFieldsPrivate",
            "testMethodsPrivate",
            "testFields",
            "testMethods",
            "getClass"
        };
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void otherDotClassDot() {
        var file = "/org/javacs/example/AutocompleteOther.java";
        var suggestions = filterText(file, 8, 33);
        assertThat(suggestions, hasItems("getName", "getClass"));
        String[] nots = {
            "testFieldStatic",
            "testMethodStatic",
            "class",
            "testFieldStaticPrivate",
            "testMethodStaticPrivate",
            "testFieldsPrivate",
            "testMethodsPrivate",
            "testFields",
            "testMethods"
        };
        for (var not : nots) {
            assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void otherClass() {
        var file = "/org/javacs/example/AutocompleteOther.java";
        var suggestions = filterText(file, 6, 13);
        assertThat(suggestions, hasItems("AutocompleteOther", "AutocompleteMember"));
    }

    @Test
    public void arrayLength() {
        var file = "/org/javacs/example/AutocompleteArray.java";
        var suggestions = filterText(file, 7, 11);
        assertThat(suggestions, hasItems("length"));
    }

    @Test
    public void indirectSuper() {
        var file = "/org/javacs/example/CompleteIndirectSuper.java";
        var suggestions = filterText(file, 5, 14);
        assertThat(suggestions, hasItems("selfMethod", "super1Method", "super2Method"));
    }

    @Test
    public void fromClasspath() {
        var file = "/org/javacs/example/AutocompleteFromClasspath.java";
        var suggestions = filterText(file, 9, 18);
        assertThat(suggestions, hasItems("add", "addAll"));
    }

    @Test
    public void betweenLines() {
        var file = "/org/javacs/example/AutocompleteBetweenLines.java";
        var suggestions = filterText(file, 9, 18);
        assertThat(suggestions, hasItems("add"));
    }

    @Test
    public void reference() {
        var file = "/org/javacs/example/AutocompleteReference.java";
        var suggestions = insertTemplate(file, 7, 21);
        assertThat(suggestions, not(hasItem("testMethodStatic")));
        assertThat(suggestions, hasItems("testMethods", "getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void docstring() {
        var file = "/org/javacs/example/AutocompleteDocstring.java";
        var docstrings = documentation(file, 8, 14);
        assertThat(docstrings, hasItems("A testMethods", "A testFields"));

        docstrings = documentation(file, 12, 31);
        assertThat(docstrings, hasItems("A testFieldStatic", "A testMethodStatic"));
    }

    @Test
    public void classes() {
        var file = "/org/javacs/example/AutocompleteClasses.java";
        var suggestions = filterText(file, 5, 12);
        assertThat(suggestions, hasItems("FixParseErrorAfter"));

        // Some?
        suggestions = filterText(file, 6, 13);
        assertThat(suggestions, hasItems("SomeInnerClass"));

        // List?
        suggestions = filterText(file, 7, 12);
        assertThat(suggestions, hasItems("List"));
    }

    @Test
    public void editMethodName() {
        var file = "/org/javacs/example/AutocompleteEditMethodName.java";
        var suggestions = filterText(file, 5, 21);
        assertThat(suggestions, hasItems("getClass"));
    }

    @Test
    @Ignore // This has been subsumed by Javadocs
    public void restParams() {
        var file = "/org/javacs/example/AutocompleteRest.java";
        var items = items(file, 5, 18);
        var suggestions = items.stream().map(i -> i.label).collect(Collectors.toSet());
        var details = items.stream().map(i -> i.detail).collect(Collectors.toSet());
        assertThat(suggestions, hasItems("restMethod"));
        assertThat(details, hasItems("void (String... params)"));
    }

    @Test
    public void constructor() {
        var file = "/org/javacs/example/AutocompleteConstructor.java";
        var suggestions = filterText(file, 5, 25);
        assertThat(suggestions, hasItem(startsWith("AutocompleteConstructor")));
        assertThat(suggestions, hasItem(startsWith("AutocompleteMember")));
    }

    @Ignore // We are now managing imports with AutoFixImports
    @Test
    public void autoImportConstructor() {
        var file = "/org/javacs/example/AutocompleteConstructor.java";
        var items = items(file, 6, 19);
        var suggestions = items.stream().map(i -> i.insertText).collect(Collectors.toList());
        assertThat(suggestions, hasItems("ArrayList<>($0)"));

        for (var each : items) {
            if (each.insertText.equals("ArrayList<>"))
                assertThat("new ? auto-imports", each.additionalTextEdits, both(not(empty())).and(not(nullValue())));
        }
    }

    @Ignore
    @Test
    public void importFromSource() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 3, 12);
        assertThat("Does not have own package class", suggestions, hasItems("javacs"));
    }

    @Test
    public void importFromClasspath() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 5, 13);
        assertThat("Has class from classpath", suggestions, hasItems("util"));
    }

    // TODO top level of import
    @Ignore
    @Test
    public void importFirstId() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 7, 9);
        assertThat("Has class from classpath", suggestions, hasItems("com", "org"));
    }

    @Test
    public void emptyClasspath() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 6, 12);
        assertThat(suggestions, not(hasItem("google.common.collect.Lists")));
    }

    @Test
    public void importClass() {
        var file = "/org/javacs/example/AutocompletePackage.java";
        var suggestions = filterText(file, 4, 25);
        assertThat(suggestions, hasItems("OtherPackagePublic"));
        // For performance, we assume OtherPackagePrivate.java contains a public class OtherPackagePrivate
        // assertThat(suggestions, not(hasItem("OtherPackagePrivate")));
    }

    @Test
    public void otherPackageId() {
        var file = "/org/javacs/example/AutocompleteOtherPackage.java";
        var items = items(file, 5, 14);
        var suggestions = items.stream().map(i -> i.label).collect(Collectors.toList());
        assertThat(suggestions, hasItems("OtherPackagePublic"));
        // For performance, we assume OtherPackagePrivate.java contains a public class OtherPackagePrivate
        // assertThat(suggestions, not(hasItem("OtherPackagePrivate")));
    }

    @Test
    public void fieldFromStaticInner() {
        var file = "/org/javacs/example/AutocompleteOuter.java";
        var suggestions = filterText(file, 12, 14);
        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        // TODO this is not visible
        String[] nots = {"testMethods", "testFields"};
        for (var not : nots) {
            // assertThat(suggestions, not(hasItem(not)));
        }
    }

    @Test
    public void fieldFromInner() {
        var file = "/org/javacs/example/AutocompleteOuter.java";
        var suggestions = filterText(file, 18, 14);
        assertThat(suggestions, hasItems("testMethodStatic", "testFieldStatic"));
        assertThat(suggestions, hasItems("testMethods", "testFields"));
    }

    @Test
    public void classDotClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = filterText(file, 5, 29);
        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = filterText(file, 6, 10);
        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void newClassDotInnerClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = filterText(file, 10, 33);
        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        // TODO you can't actually make an inner enum
        // assertThat("does not suggest enum", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void newInnerClassFromMethod() {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = filterText(file, 11, 18);
        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        // TODO you can't actually make an inner enum
        // assertThat("does not suggest enum", suggestions, not(hasItem("InnerEnum")));
    }

    @Test
    public void innerEnum() {
        var file = "/org/javacs/example/AutocompleteInners.java";
        var suggestions = filterText(file, 15, 40);
        assertThat("suggests enum constants", suggestions, hasItems("Foo"));
    }

    @Test
    public void enumConstantFromSourcePath() {
        var file = "/org/javacs/example/AutocompleteCase.java";
        var suggestions = filterText(file, 6, 18);
        assertThat("suggests enum options", suggestions, containsInAnyOrder("Foo", "Bar"));
    }

    @Test
    public void enumConstantFromSourcePathPartial() {
        var file = "/org/javacs/example/AutocompleteCasePartial.java";
        var suggestions = filterText(file, 6, 19);
        assertThat("suggests enum options", suggestions, hasItem("Foo"));
    }

    @Test
    public void enumConstantFromClassPath() {
        var file = "/org/javacs/example/AutocompleteCaseFromClasspath.java";
        var suggestions = filterText(file, 8, 18);
        assertThat("suggests enum options", suggestions, containsInAnyOrder("FULL", "LONG", "MEDIUM", "SHORT"));
    }

    @Test
    public void enumConstantFromClassPathPartial() {
        var file = "/org/javacs/example/AutocompleteCaseFromClasspathPartial.java";
        var suggestions = filterText(file, 8, 19);
        assertThat("suggests enum options", suggestions, hasItem("FULL"));
    }

    @Test
    public void staticStarImport() {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = filterText(file, 9, 15);
        assertThat("suggests star-imported static method", suggestions, hasItems("emptyList"));
    }

    @Test
    public void staticImport() {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = filterText(file, 10, 10);
        assertThat("suggests star-imported static field", suggestions, hasItems("BC"));
    }

    @Test
    public void staticImportSourcePath() {
        var file = "/org/javacs/example/AutocompleteStaticImport.java";
        var suggestions = filterText(file, 11, 10);
        assertThat(
                "suggests star-imported public static field from source path",
                suggestions,
                hasItems("publicStaticFinal"));
        assertThat(
                "suggests star-imported package-private static field from source path",
                suggestions,
                hasItems("packagePrivateStaticFinal"));
    }

    @Test
    public void withinConstructor() {
        var file = "/org/javacs/example/AutocompleteContext.java";
        var suggestions = filterText(file, 8, 38);
        assertThat("suggests local variable", suggestions, hasItems("length"));
    }

    @Test
    @Ignore
    public void onlySuggestOnce() {
        var file = "/org/javacs/example/AutocompleteOnce.java";
        var suggestions = insertCount(file, 5, 18);
        assertThat("suggests Signatures", suggestions, hasKey("Signatures"));
        assertThat("suggests Signatures only once", suggestions, hasEntry("Signatures", 1));
    }

    @Test
    public void overloadedOnSourcePath() {
        var file = "/org/javacs/example/OverloadedMethod.java";
        var labels = detail(file, 9, 13);
        assertThat("suggests overloads", labels, hasItem("void overloaded() (+2 overloads)"));
    }

    @Test
    public void overloadedOnClassPath() {
        var file = "/org/javacs/example/OverloadedMethod.java";
        var labels = detail(file, 10, 26);
        assertThat("suggests overloads", labels, hasItem("List<E> of() (+11 overloads)"));
    }

    @Test
    public void packageName() {
        var file = "/org/javacs/example/AutocompletePackageName.java";
        var suggestions = insertText(file, 1, 5);
        assertThat(suggestions, hasItem(startsWith("package org.javacs.example;")));
    }

    @Test
    public void className() {
        var file = "/org/javacs/example/AutocompleteClassName.java";
        var suggestions = filterText(file, 1, 2);
        assertThat(suggestions, hasItem(startsWith("class AutocompleteClassName")));
    }

    @Test
    public void annotationInInnerClass() {
        var file = "/org/javacs/example/AnnotationInInnerClass.java";
        var suggestions = filterText(file, 6, 17);
        assertThat(suggestions, hasItem(startsWith("Override")));
    }

    @Test
    public void stringBuilderLength() {
        var file = "/org/javacs/example/CompleteStringBuilderLength.java";
        var suggestions = filterText(file, 6, 12);
        assertThat(suggestions, hasItem(containsString("length")));
    }

    @Test
    public void implementsKeyword() {
        var file = "/org/javacs/example/AutocompleteImplements.java";
        var suggestions = filterText(file, 3, 34);
        assertThat(suggestions, hasItem(containsString("implements")));
    }

    @Test
    public void importStaticPackage() {
        var file = "/org/javacs/example/AutocompleteImportStatic.java";
        var suggestions = filterText(file, 3, 20);
        assertThat(suggestions, hasItem(containsString("util")));
    }

    @Test
    public void newlyCreatedClass() throws IOException { // TODO this does throw IOException
        var file = FindResource.path("/org/javacs/example/NewlyCreatedFile.java");
        try {
            // Create a file that didn't exist when we created the server
            try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) {
                writer.write("package org.javacs.example;\nclass NewlyCreatedFile { }");
            }
            // Send a 'file created' notification
            var created = new FileEvent();
            created.uri = file.toUri();
            created.type = FileChangeType.Created;
            var changes = new DidChangeWatchedFilesParams();
            changes.changes = List.of(created);
            server.didChangeWatchedFiles(changes);
            // Autocomplete `New`
            var suggestions = filterText("/org/javacs/example/AutocompleteNewFile.java", 5, 12);
            assertThat(suggestions, hasItem(containsString("NewlyCreatedFile")));
        } finally {
            Files.delete(file);
            // Send a 'file deleted' notification
            var deleted = new FileEvent();
            deleted.uri = file.toUri();
            deleted.type = FileChangeType.Deleted;
            var changes = new DidChangeWatchedFilesParams();
            changes.changes = List.of(deleted);
            server.didChangeWatchedFiles(changes);
        }
    }

    @Test
    public void completeParens() {
        var inserts = insertText("/org/javacs/example/CompleteParens.java", 5, 12);
        assertThat(inserts, hasItem("returnsVoid()$0"));
        assertThat(inserts, hasItem("returnsString()$0"));
        assertThat(inserts, hasItem("returnsArg($0)"));
    }

    @Test
    public void dontCompleteParens() {
        var inserts = insertText("/org/javacs/example/DontCompleteParens.java", 5, 19);
        assertThat(inserts, hasItem("returnsString"));
    }

    @Test
    public void multilineChain() {
        var inserts = filterText("/org/javacs/example/MultilineChain.java", 6, 14);
        assertThat(inserts, hasItem("concat"));
    }

    @Test
    public void removeCompiledClassShouldPublishCorrectDiagnostic() throws IOException {
        var xClass = FindResource.path("/org/javacs/example/X.java");
        var yClass = FindResource.path("/org/javacs/example/Y.java");
        try {
            try (var writer = Files.newBufferedWriter(xClass, StandardOpenOption.CREATE_NEW)) {
                writer.write(
                        "package org.javacs.example;\n"
                                + "class X {\n "
                                + "static void test() {\n"
                                + "Y.test();\n"
                                + "}}");
            }
            try (var writer = Files.newBufferedWriter(yClass, StandardOpenOption.CREATE_NEW)) {
                writer.write(
                        "package org.javacs.example;\nclass Y {\n" + "static int test() {\n" + "return 1;\n" + "}}");
            }
            List<String> lintErrors = new ArrayList<>();
            var server = LanguageServerFixture.getJavaLanguageServer(diagnostic -> lintErrors.add(diagnostic.message));
            server.compiler().compile(xClass, yClass).close();

            var deleteEvent = new FileEvent();
            deleteEvent.uri = yClass.toUri();
            deleteEvent.type = FileChangeType.Deleted;
            var deleteFileParams = new DidChangeWatchedFilesParams();
            deleteFileParams.changes = List.of(deleteEvent);
            server.didChangeWatchedFiles(deleteFileParams);

            assertEquals(1, lintErrors.size());
            assertTrue(lintErrors.stream().anyMatch(e -> e.contains("cannot find symbol")));
        } finally {
            Files.delete(xClass);
            Files.delete(yClass);
        }
    }
}
