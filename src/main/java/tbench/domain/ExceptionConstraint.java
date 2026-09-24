package tbench.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A false path or multicycle exception bound to concrete object sets.
 * Coverage is decided by set containment of endpoints, never by "the last
 * textual line wins".
 */
public final class ExceptionConstraint {
    public enum Kind { FALSE_PATH, MULTICYCLE }

    public final String id;
    public final Kind kind;
    public final Set<String> fromRefs;
    public final Set<String> throughRefs;
    public final Set<String> toRefs;
    public final Integer setupCycles;
    public final Integer holdCycles;
    public final int ordinal;
    public final String rawText;
    public final String revisionId;

    public ExceptionConstraint(String id, Kind kind, Set<String> fromRefs, Set<String> throughRefs,
            Set<String> toRefs, Integer setupCycles, Integer holdCycles, int ordinal,
            String rawText, String revisionId) {
        this.id = id;
        this.kind = kind;
        this.fromRefs = new LinkedHashSet<>(fromRefs);
        this.throughRefs = new LinkedHashSet<>(throughRefs);
        this.toRefs = new LinkedHashSet<>(toRefs);
        this.setupCycles = setupCycles;
        this.holdCycles = holdCycles;
        this.ordinal = ordinal;
        this.rawText = rawText;
        this.revisionId = revisionId;
    }

    public List<String> dimensions() {
        List<String> d = new ArrayList<>();
        if (!fromRefs.isEmpty()) {
            d.add("from");
        }
        if (!throughRefs.isEmpty()) {
            d.add("through");
        }
        if (!toRefs.isEmpty()) {
            d.add("to");
        }
        return d;
    }

    /** A constraint matches a path when every non-empty object set contains. */
    public boolean matches(String startKey, List<String> segKeys, String endKey) {
        if (!fromRefs.isEmpty() && !fromRefs.contains(startKey)) {
            return false;
        }
        if (!toRefs.isEmpty() && !toRefs.contains(endKey)) {
            return false;
        }
        if (!throughRefs.isEmpty()) {
            boolean any = false;
            for (String s : segKeys) {
                if (throughRefs.contains(s)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    public boolean covers(ExceptionConstraint other) {
        return coversSet(fromRefs, other.fromRefs)
                && coversSet(throughRefs, other.throughRefs)
                && coversSet(toRefs, other.toRefs);
    }

    private static boolean coversSet(Set<String> a, Set<String> b) {
        if (a.isEmpty()) {
            return true;
        }
        return a.containsAll(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExceptionConstraint)) {
            return false;
        }
        ExceptionConstraint that = (ExceptionConstraint) o;
        return ordinal == that.ordinal
                && kind == that.kind
                && Objects.equals(id, that.id)
                && Objects.equals(fromRefs, that.fromRefs)
                && Objects.equals(throughRefs, that.throughRefs)
                && Objects.equals(toRefs, that.toRefs)
                && Objects.equals(setupCycles, that.setupCycles)
                && Objects.equals(holdCycles, that.holdCycles)
                && Objects.equals(rawText, that.rawText)
                && Objects.equals(revisionId, that.revisionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, fromRefs, throughRefs, toRefs, setupCycles,
                holdCycles, ordinal, rawText, revisionId);
    }
}
