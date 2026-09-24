package sta.store;

/** Virtual time source. Tests inject a fixed/manual clock; the app uses system time. */
public interface Clock {
    long tick();

    Clock SYSTEM = System::currentTimeMillis;

    final class Manual implements Clock {
        private long now;
        public Manual(long start) { this.now = start; }
        @Override public long tick() { return now; }
        public void advance(long delta) { now += delta; }
    }
}
