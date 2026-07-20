package pl.epsi;

public class DifferentTest {
    static void main() {
        TestWrapper wrapper = new TestWrapper(new Test());
        TestWrapperWrapper x2w = new TestWrapperWrapper(wrapper);
        TrippleWrapper x3w = new TrippleWrapper(x2w);
        //x3w.sayHi();
        //wrapper[0].sayHi();
        //System.out.println(x3w[0].a);
    }

    public static class Test {
        public String a = "HI";
        public void sayHi() {
            IO.println("hi!");
        }
    }

    public static class TestWrapper implements Deref<Test> {

        private final Test inst;

        public TestWrapper(Test inst) {
            this.inst = inst;
        }

        public void sayHiFromWrapper() {
        }

        @Override
        public Test deref() {
            return this.inst;
        }
    }

    public static class TestWrapperWrapper implements Deref<TestWrapper> {

        private final TestWrapper wrapper;

        public TestWrapperWrapper(TestWrapper wrapper) {
            this.wrapper = wrapper;
        }

        public void sayHiFrom2XWrapper() {
        }

        @Override
        public TestWrapper deref() {
            return wrapper;
        }
    }

    public static class TrippleWrapper implements Deref<TestWrapperWrapper> {
        private final TestWrapperWrapper wrapper;

        public TrippleWrapper(TestWrapperWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public TestWrapperWrapper deref() {
            return wrapper;
        }
    }
}
