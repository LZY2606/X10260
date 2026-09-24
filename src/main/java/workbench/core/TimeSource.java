package workbench.core;

/** 虚拟时间源：应用用系统时间，测试用可推进的虚拟时间。 */
public interface TimeSource {
    long nowMillis();

    static TimeSource system() {
        return System::currentTimeMillis;
    }

    final class Virtual implements TimeSource {
        private long now;

        public Virtual(long startMillis) {
            this.now = startMillis;
        }

        @Override
        public long nowMillis() {
            return now;
        }

        public void advance(long millis) {
            now += millis;
        }
    }
}
