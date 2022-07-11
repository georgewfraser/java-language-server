package org.javacs.action;

class TestAddImportAnonymousClass {
    void test() {
        new Object() {
            List<Integer> list;
        };
    }
}
