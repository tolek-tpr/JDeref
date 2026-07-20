package pl.epsi;

public class MainTest {

    static void main() {
        var wrapper = new TestWrapper(new Test[]{new Test("first"), new Test("second")});
//        wrapper.sayBye();
//        wrapper.sayHi();
//        System.out.println(wrapper[0]);
//        wrapper[1].sayHi();
//
//        wrapper[1] = new Test("newely assigned");
//        wrapper[1].sayHi();
//        var otherWrapper = new OtherWrapper(new Test("other stuff"));
//        otherWrapper.sayHi();
//        sayHiFromAnything(otherWrapper);
//        OtherWrapper.sayHi(otherWrapper);
    }

    public static class OtherWrapper implements Deref<Test> {

        private final Test a;

        public OtherWrapper(Test a) {
            this.a = a;
        }

        public void sayHi() {
            System.out.println("Hi from other wrapper!");
        }

        @Override
        public Test deref() {
            return this.a;
        }
    }

    public static class Test {

        public String toPrint;

        public Test(String toPrint) {
            this.toPrint = toPrint;
        }

        public void sayHi() {
            System.out.println(toPrint);
        }
    }

    public static class TestWrapper implements Deref<Test[]> {

        private final Test[] a;

        public TestWrapper(Test[] a) {
            this.a = a;
        }

        public void sayBye() {
            System.out.println("BYE!");
        }

        public void sayHi() {
            System.out.println("HI FROM WRAPPER!");
        }

        @Override
        public Test[] deref() {
            return this.a;
        }
    }

    public static <D extends Deref<Test>> void sayHiFromAnything(D d) {
        d.sayHi();
    }

}
