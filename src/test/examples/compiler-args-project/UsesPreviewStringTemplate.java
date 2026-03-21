class UsesPreviewStringTemplate {
    void test(String name) {
        String message = STR."hello \{name}";
        System.out.println(message);
    }
}
