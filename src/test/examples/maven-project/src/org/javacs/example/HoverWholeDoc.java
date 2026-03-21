package org.javacs.example;

class HoverWholeDoc {
    /**
     * First sentence.
     *
     * Second paragraph with {@code code}.
     *
     * @param value the input value
     * @return the same value
     */
    String documentMe(String value) {
        return value;
    }

    void test() {
        documentMe("x");
    }
}
