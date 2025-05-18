package org.javacs.example;

public class VarTypeReferences {
    private static class Foo {
        public void foo() {}
    }

    public void run() {
        var foo = new Foo();
        foo.foo();
    }
}
