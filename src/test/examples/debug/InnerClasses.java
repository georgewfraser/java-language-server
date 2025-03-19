public class InnerClasses {
    public static void main(String[] args) {
        System.out.println("at main");
        new Inner().run();
    }

    // An inner class:
    private static class Inner {
        public void run() {
            System.out.println("at Inner.run");
            // An anonymous class:
            new Runnable() {
                @Override
                public void run() {
                    System.out.println("at Runnable.run");
                }
            }.run();
        }
    }
}
