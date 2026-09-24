package tbench.domain;

import java.util.Objects;

/**
 * An explicit relationship between two clock domains. Without one, a
 * launch/capture cross-domain path is reported as unconstrained rather than
 * assumed synchronous.
 */
public final class ClockRelation {
    public enum Type { SYNCHRONOUS, ASYNCHRONOUS, LOGICALLY_EXCLUSIVE, PHYSICALLY_EXCLUSIVE }

    public final String id;
    public final String fromClock;
    public final String toClock;
    public final Type type;
    public final String rawText;
    public final String revisionId;

    public ClockRelation(String id, String fromClock, String toClock, Type type,
            String rawText, String revisionId) {
        this.id = id;
        this.fromClock = fromClock;
        this.toClock = toClock;
        this.type = type;
        this.rawText = rawText;
        this.revisionId = revisionId;
    }

    public boolean relates(String a, String b) {
        if (type == Type.ASYNCHRONOUS || type == Type.LOGICALLY_EXCLUSIVE
                || type == Type.PHYSICALLY_EXCLUSIVE) {
            return false;
        }
        return (fromClock.equals(a) && toClock.equals(b))
                || (fromClock.equals(b) && toClock.equals(a));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) && o instanceof ClockRelation;
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
