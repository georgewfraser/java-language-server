package org.javacs.warn;

import java.io.IOException;

class NotThrownConstructor {
    void isThrown() throws IOException {
        new Inner();
    }

    private static class Inner {
        private Inner() throws IOException {
            throw new IOException();
        }
    }
}
