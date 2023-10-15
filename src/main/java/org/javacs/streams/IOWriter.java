package org.javacs.streams;

import java.io.IOException;
import java.io.Writer;
import java.util.function.Consumer;

/**
 * Callback based writer that sends data to a callback
 */
public class IOWriter extends Writer {

    private final Consumer<String> onWrite;

    public IOWriter(Consumer<String> onWrite) {
        this.onWrite = onWrite;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        String s = new String(cbuf, off, len);
        onWrite.accept(s);
    }

    @Override
    public void flush() throws IOException {
        //NO-OP
    }

    @Override
    public void close() throws IOException {
        //NO-OP
    }
}
