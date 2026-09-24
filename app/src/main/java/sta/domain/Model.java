package sta.domain;

import java.util.ArrayList;
import java.util.List;

/** Domain model. Every quantity keeps its raw text plus unit; normalization to
 *  picoseconds happens only in the derived layer. Unconvertible values stay in
 *  place with a diagnostic attached. */
public final class Model {

    /** A quantity with unit. raw is the original representation; normalizedPs
     *  is filled by the derived layer (null when not convertible). */
    public static final class Quantity {
        public final String raw;
        public final String unit;          // ns, ps, deg, ratio ...
        public final Double value;         // numeric part as written
        public Double normalizedPs;        // derived layer only
        public String diagnostic;          // set when not convertible

        public Quantity(String raw, String unit, Double value) {
            this.raw = raw; this.unit = unit; this.value = value;
        }

        public static Quantity parse(String token) {
            if (token == null) return new Quantity(null, null, null);
            String t = token.trim();
            StringBuilder num = new StringBuilder();
            int i = 0;
            if (i < t.length() && (t.charAt(i) == '-' || t.charAt(i) == '+')) { num.append(t.charAt(i)); i++; }
            while (i < t.length() && (Character.isDigit(t.charAt(i)) || t.charAt(i) == '.')) { num.append(t.charAt(i)); i++; }
            String unit = t.substring(i);
            Double v = null;
            try { if (num.length() > 0 && !num.toString().equals("-") && !num.toString().equals("+")) v = Double.parseDouble(num.toString()); }
            catch (NumberFormatException ignored) { }
            Quantity q = new Quantity(t, unit.isEmpty() ? null : unit, v);
            if (v == null) q.diagnostic = "unparseable numeric value: '" + t + "'";
            return q;
        }

        /** Derived-layer normalization. Returns ps, or null with diagnostic. */
        public Double toPs() {
            if (value == null) { if (diagnostic == null) diagnostic = "no numeric value"; return null; }
            if (unit == null || unit.equals("ns")) return value * 1000.0;
            if (unit.equals("ps")) return value;
            if (unit.equals("us")) return value * 1_000_000.0;
            diagnostic = "unsupported unit for time: '" + unit + "'";
            return null;
        }

        public String display() {
            return raw + (diagnostic != null ? " [!" + diagnostic + "]" : "");
        }
    }

    public static final class ClockDef {
        public String id;            // stable identity, e.g. clk-1
        public String name;
        public String attach;        // port/pin the clock is defined on
        public Quantity period;
        public List<Quantity> waveform = new ArrayList<>(); // edge times
        public String rawLine;       // original report segment line
        public String sourceInputId; // provenance: input version id
    }

    public static final class GenClock {
        public String id;
        public String name;
        public String sourceClock;   // master clock name
        public String attach;
        public int multiplyBy = 1;
        public int divideBy = 1;
        public Quantity phase;       // degrees
        public String rawLine;
        public String sourceInputId;
    }

    public enum ConstraintKind { FALSE_PATH, MULTICYCLE, CLOCK_GROUPS }

    public static final class Constraint {
        public String id;
        public ConstraintKind kind;
        public List<String> fromSet = new ArrayList<>();  // object patterns (exact or glob with *)
        public List<String> toSet = new ArrayList<>();
        public List<List<String>> groups = new ArrayList<>(); // for CLOCK_GROUPS
        public Integer setupMult;    // multicycle setup
        public Integer holdMult;     // multicycle hold (explicit)
        public String rawLine;
        public String sourceInputId;
        public int textOrder;        // position in source, kept for provenance (NOT for precedence)
    }

    public static final class PathSeg {
        public String node;          // netlist object name
        public Quantity delay;
        public PathSeg(String node, Quantity delay) { this.node = node; this.delay = delay; }
    }

    public static final class TimingPath {
        public String id;            // stable identity, e.g. P1
        public String startpoint;
        public String endpoint;
        public String launchClock;
        public String captureClock;
        public Quantity arrival;     // as reported
        public Quantity reportedRequired; // as reported
        public List<PathSeg> segments = new ArrayList<>();
        public String rawLine;
        public String sourceInputId;
    }

    public static final class ToolInfo {
        public String tool;
        public String version;
    }

    /** Parsed view of one input version. Raw text is always preserved verbatim
     *  in the store; this is the structured projection. */
    public static final class ParsedBundle {
        public ToolInfo tool = new ToolInfo();
        public List<ClockDef> clocks = new ArrayList<>();
        public List<GenClock> genClocks = new ArrayList<>();
        public List<Constraint> constraints = new ArrayList<>();
        public List<TimingPath> paths = new ArrayList<>();
        public List<String> diagnostics = new ArrayList<>(); // format errors, kept in place
    }

    private Model() {}
}
