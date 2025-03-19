public class DeepVariables {
    public static void main(String[] args) {
        new DeepVariables().run();
    }

    public void run() {
        Inner object = new Inner();
        System.out.println(object.value);
    }

    private static class Inner {
        public final int value = 42;
    }
}
