package workbench.util;

/** Virtual-time capable clock. Tests use {@link Fixed}; the server uses system time. */
public interface TimeSource {
    long nowMillis();

    static TimeSource system() { return System::currentTimeMillis; }

    final class Fixed implements TimeSource {
        private long now;
        public Fixed(long startMillis) { this.now = startMillis; }
        public void advance(long ms) { now += ms; }
        @Override public long nowMillis() { return now; }
    }
}
