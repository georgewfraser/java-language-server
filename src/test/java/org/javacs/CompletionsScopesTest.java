package org.javacs;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class CompletionsScopesTest extends CompletionsBase {
    @Test
    public void staticSub() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // Static method
        var suggestions = filterText(file, 15, 14);

        // Locals
        assertThat(suggestions, hasItems("testLocalVariables", "testArguments"));
        // Static methods in enclosing scopes
        assertThat(suggestions, hasItems("testStatic"));
        assertThat(suggestions, hasItems("testOuterStaticMethod"));
        // Virtual methods in enclosing scopes
        assertThat(suggestions, not(hasItems("testInner")));
        assertThat(suggestions, hasItems("test"));
        assertThat(suggestions, not(hasItems("testOuterMethods")));
        // Inherited static methods
        assertThat(suggestions, hasItems("testInheritedStaticMethod"));
        // Inherited virtual methods
        assertThat(suggestions, hasItems("testInheritedMethods"));
        // this/super in enclosing scopes
        assertThat(suggestions, hasItems("this"));
    }

    @Test
    public void staticSubThisSuper() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // StaticSub.this, StaticSub.super
        assertThat(filterText(file, 37, 23), hasItems("this"));
        // AutocompleteScopes.this, AutocompleteScopes.super
        assertThat(filterText(file, 39, 32), not(hasItems("this")));
        // Super.this, Super.super
        assertThat(filterText(file, 41, 19), not(hasItems("this")));
    }

    @Test
    public void staticSubInner() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // Static method
        var suggestions = filterText(file, 45, 22);

        // Locals
        assertThat(suggestions, hasItems("testLocalVariables", "testArguments"));
        // Static methods in enclosing scopes
        assertThat(suggestions, hasItems("testStatic"));
        assertThat(suggestions, hasItems("testOuterStaticMethod"));
        // Virtual methods in enclosing scopes
        assertThat(suggestions, hasItems("testInner"));
        assertThat(suggestions, hasItems("test"));
        assertThat(suggestions, not(hasItems("testOuterMethods")));
        // Inherited static methods
        assertThat(suggestions, hasItems("testInheritedStaticMethod"));
        // Inherited virtual methods
        assertThat(suggestions, hasItems("testInheritedMethods"));
        // this/super in enclosing scopes
        assertThat(suggestions, hasItems("this"));
    }

    @Test
    public void staticSubInnerThisSuper() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // StaticSub.this, StaticSub.super
        assertThat(filterText(file, 67, 31), hasItems("this"));
        // AutocompleteScopes.this, AutocompleteScopes.super
        assertThat(filterText(file, 69, 40), not(hasItems("this")));
        // Super.this, Super.super
        assertThat(filterText(file, 71, 27), not(hasItems("this")));
    }

    @Test
    public void staticSubStaticMethod() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // Static method
        var suggestions = filterText(file, 78, 14);

        // Locals
        assertThat(suggestions, hasItems("testLocalVariables", "testArguments"));
        // Static methods in enclosing scopes
        assertThat(suggestions, hasItems("testStatic"));
        assertThat(suggestions, hasItems("testOuterStaticMethod"));
        // Virtual methods in enclosing scopes
        assertThat(suggestions, not(hasItems("testInner")));
        assertThat(suggestions, not(hasItems("test")));
        assertThat(suggestions, not(hasItems("testOuterMethods")));
        // Inherited static methods
        assertThat(suggestions, hasItems("testInheritedStaticMethod"));
        // Inherited virtual methods
        assertThat(suggestions, not(hasItems("testInheritedMethods")));
        // this/super in enclosing scopes
        assertThat(suggestions, not(hasItems("this")));
    }

    @Test
    public void staticSubStaticMethodThisSuper() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // StaticSub.this, StaticSub.super
        assertThat(filterText(file, 100, 23), not(hasItems("this")));
        // AutocompleteScopes.this, AutocompleteScopes.super
        assertThat(filterText(file, 102, 32), not(hasItems("this")));
        // Super.this, Super.super
        assertThat(filterText(file, 104, 19), not(hasItems("this")));
    }

    @Test
    public void staticSubStaticMethodInner() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // Static method
        var suggestions = filterText(file, 108, 22);

        // Locals
        assertThat(suggestions, hasItems("testLocalVariables", "testArguments"));
        // Static methods in enclosing scopes
        assertThat(suggestions, hasItems("testStatic"));
        assertThat(suggestions, hasItems("testOuterStaticMethod"));
        // Virtual methods in enclosing scopes
        assertThat(suggestions, hasItems("testInner"));
        assertThat(suggestions, not(hasItems("test")));
        assertThat(suggestions, not(hasItems("testOuterMethods")));
        // Inherited static methods
        assertThat(suggestions, hasItems("testInheritedStaticMethod"));
        // Inherited virtual methods
        assertThat(suggestions, not(hasItems("testInheritedMethods")));
        // this/super in enclosing scopes
        assertThat(suggestions, hasItems("this"));
    }

    @Test
    public void staticSubStaticMethodInnerThisSuper() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // StaticSub.this, StaticSub.super
        assertThat(filterText(file, 130, 31), not(hasItems("this")));
        // AutocompleteScopes.this, AutocompleteScopes.super
        assertThat(filterText(file, 132, 40), not(hasItems("this")));
        // Super.this, Super.super
        assertThat(filterText(file, 134, 27), not(hasItems("this")));
    }

    @Test
    public void sub() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // Static method
        var suggestions = filterText(file, 143, 14);

        // Locals
        assertThat(suggestions, hasItems("testLocalVariables", "testArguments"));
        // Static methods in enclosing scopes
        assertThat(suggestions, not(hasItems("testStatic")));
        assertThat(suggestions, hasItems("testOuterStaticMethod"));
        // Virtual methods in enclosing scopes
        assertThat(suggestions, not(hasItems("testInner")));
        assertThat(suggestions, hasItems("test"));
        assertThat(suggestions, hasItems("testOuterMethods"));
        // Inherited static methods
        assertThat(suggestions, hasItems("testInheritedStaticMethod"));
        // Inherited virtual methods
        assertThat(suggestions, hasItems("testInheritedMethods"));
        // this/super in enclosing scopes
        assertThat(suggestions, hasItems("this"));
    }

    @Test
    public void subThisSuper() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // sub.this, sub.super
        assertThat(filterText(file, 158, 17), hasItems("this"));
        // AutocompleteScopes.this, AutocompleteScopes.super
        assertThat(filterText(file, 160, 32), hasItems("this"));
        // Super.this, Super.super
        assertThat(filterText(file, 162, 19), not(hasItems("this")));
    }

    @Test
    public void subInner() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // Static method
        var suggestions = filterText(file, 166, 22);

        // Locals
        assertThat(suggestions, hasItems("testLocalVariables", "testArguments"));
        // Static methods in enclosing scopes
        assertThat(suggestions, not(hasItems("testStatic")));
        assertThat(suggestions, hasItems("testOuterStaticMethod"));
        // Virtual methods in enclosing scopes
        assertThat(suggestions, hasItems("testInner"));
        assertThat(suggestions, hasItems("test"));
        assertThat(suggestions, hasItems("testOuterMethods"));
        // Inherited static methods
        assertThat(suggestions, hasItems("testInheritedStaticMethod"));
        // Inherited virtual methods
        assertThat(suggestions, hasItems("testInheritedMethods"));
        // this/super in enclosing scopes
        assertThat(suggestions, hasItems("this"));
    }

    @Test
    public void subInnerThisSuper() {
        var file = "/org/javacs/example/AutocompleteScopes.java";

        // sub.this, sub.super
        assertThat(filterText(file, 181, 25), hasItems("this"));
        // AutocompleteScopes.this, AutocompleteScopes.super
        assertThat(filterText(file, 183, 40), hasItems("this"));
        // Super.this, Super.super
        assertThat(filterText(file, 185, 27), not(hasItems("this")));
    }

    @Test
    public void blockLocal() {
        var file = "/org/javacs/example/AutocompleteScopes.java";
        var suggestions = filterText(file, 196, 14);
        assertThat(suggestions, hasItems("testArg", "testMethodLocal", "testBlockLocal"));
    }
}
