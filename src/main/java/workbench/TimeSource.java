package workbench;

import java.time.Instant;

public interface TimeSource {
    Instant now();

    static TimeSource system() {
        return Instant::now;
    }

    /** Deterministic clock for tests and case replay. */
    final class Virtual implements TimeSource {
        private Instant current;

        public Virtual(Instant start) {
            this.current = start;
        }

        @Override
        public synchronized Instant now() {
            return current;
        }

        public synchronized void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
