package org.javacs.warn;

import java.util.function.Consumer;

class Unused {
    void test(int unusedParam) {
        int unusedLocal = 1;
    }

    private int unusedPrivate;

    Consumer<Integer> lambda = unusedLambdaParam -> {
        int unusedLocalInLambda;
    };

    private int unusedMethod() {
        return 0;
    }

    private Unused() { }

    private Unused(int i) { }

    private class UnusedClass { }

    private void unusedSelfReference() {
        unusedSelfReference();
    }

    private void unusedMutuallyRecursive1() {
        unusedMutuallyRecursive2();
    }

    private void unusedMutuallyRecursive2() {
        unusedMutuallyRecursive1();
    }

    private int usedByUnusedVar() {
        return 1;
    }

    void referenceUsedByUnusedVar() {
        var x = usedByUnusedVar();
    }

    void notActuallyThrown() throws Exception { }

    interface Throws {
        void interfaceThrows() throws Exception;
    }

    abstract class AbstractThrows {
        abstract void abstractThrows() throws Exception;
    }

    native void nativeThrows() throws Exception;
}
