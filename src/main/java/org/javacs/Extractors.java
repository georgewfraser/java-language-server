package org.javacs;

import java.util.regex.Pattern;

public class Extractors {

    private static final Pattern PACKAGE_EXTRACTOR = Pattern.compile("^([a-z][_a-zA-Z0-9]*\\.)*[a-z][_a-zA-Z0-9]*");

    public static String packageName(String className) {
        var m = PACKAGE_EXTRACTOR.matcher(className);
        if (m.find()) {
            return m.group();
        }
        return "";
    }

    private static final Pattern SIMPLE_EXTRACTOR = Pattern.compile("[A-Z][_a-zA-Z0-9]*$");

    public static String simpleName(String className) {
        var m = SIMPLE_EXTRACTOR.matcher(className);
        if (m.find()) {
            return m.group();
        }
        return "";
    }
}
