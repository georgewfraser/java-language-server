package org.javacs.streams;

import java.io.IOException;
import java.io.Reader;

/**
 * A class to be able to send data to LSP
 */
public class IOReader extends Reader {

    private StringBuilder sb = new StringBuilder();

    /**
     * Send data into the reader
     * @param ch one character
     */
    public synchronized void write(char ch) {
        sb.append(ch);
        notify();
    }

    /**
     * Write a string to the reader
     * @param s string
     */
    public synchronized void write(String s) {
        sb.append(s);
        notify();
    }

    @Override
    public synchronized int read(char[] cbuf, int off, int len) throws IOException {
        try {
            if (sb.isEmpty()) {
                wait();
            }
            len = Math.min(len, sb.length());
            String s = sb.substring(off, len);
            sb.delete(off, len);
            System.arraycopy(s.toCharArray(), 0, cbuf, 0, s.length());
            return s.length();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        //NO-OP
    }
}
