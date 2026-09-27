package gsb.model;

/**
 * A timing exception (false_path / multicycle). Patterns may be exact object
 * names, globs, or null (matches everything). Override between exceptions is
 * decided by object-set specificity, never by textual order.
 */
public record ExceptionDef(
        String id,
        String type, // false_path | multicycle
        String fromPattern,
        String toPattern,
        Integer setupMult,
        Integer holdMult,
        String rawLine,
        String sourceId,
        int lineNo) {

    /** Specificity of one endpoint pattern: exact=2, glob=1, absent=0. */
    public static int patternScore(String pattern) {
        if (pattern == null) return 0;
        return pattern.contains("*") ? 1 : 2;
    }

    public int specificity() {
        return patternScore(fromPattern) + patternScore(toPattern);
    }
}
