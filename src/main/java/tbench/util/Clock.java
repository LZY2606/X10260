package tbench.util;

/**
 * Injectable clock. Production code uses {@link #system()}; tests use a fixed
 * virtual clock so event timestamps and fingerprints are deterministic.
 */
public interface Clock {
    long nowMillis();

    static Clock system() {
        return System::currentTimeMillis;
    }

    /** Virtual clock that only advances when asked. Seed is fixed by tests. */
    final class Virtual implements Clock {
        private long current;

        public Virtual(long seedMillis) {
            this.current = seedMillis;
        }

        @Override
        public long nowMillis() {
            return current;
        }

        public long advance(long deltaMillis) {
            this.current += deltaMillis;
        }

        public long set(long millis) {
            this.current = millis;
            return this.current;
        }
    }
}
