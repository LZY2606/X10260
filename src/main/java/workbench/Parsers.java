package workbench;

import workbench.Model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsers for the small copy-pasteable text formats. Raw text is always preserved by the
 *  caller; values that cannot be converted stay in place and produce diagnostics. */
public final class Parsers {
    private Parsers() {}

    public static final class Parsed<T> {
        public final List<T> items = new ArrayList<>();
        public final List<Diagnostic> diagnostics = new ArrayList<>();
    }

    // ---------------- clocks ----------------
    public static Parsed<ClockDef> parseClocks(String text) {
        Parsed<ClockDef> out = new Parsed<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNo = i + 1;
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                if (line.startsWith("create_generated_clock")) {
                    String name = opt(line, "-name");
                    String source = bracket(line, "-source");
                    String target = firstBracket(line);
                    Integer div = intOpt(line, "-divide_by");
                    if (name == null || source == null || div == null || target == null) {
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                                "generated clock needs -name, -source, -divide_by and a target object", line));
                        continue;
                    }
                    out.items.add(new ClockDef(name, "GENERATED", target, null, null,
                            stripCmd(source), div, line, lineNo));
                } else if (line.startsWith("create_clock")) {
                    String name = opt(line, "-name");
                    String periodS = opt(line, "-period");
                    String target = firstBracket(line);
                    if (name == null || periodS == null) {
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                                "create_clock needs -name and -period", line));
                        continue;
                    }
                    Double period = parseDouble(periodS);
                    if (period == null) {
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                                "period '" + periodS + "' is not a number of ns; value left unparsed", line));
                        out.items.add(new ClockDef(name, "BASE", target == null ? null : stripCmd(target),
                                null, null, null, null, line, lineNo));
                        continue;
                    }
                    List<Double> waveform = parseWaveform(line, period);
                    out.items.add(new ClockDef(name, "BASE", target == null ? null : stripCmd(target),
                            period, waveform, null, null, line, lineNo));
                } else {
                    out.diagnostics.add(new Diagnostic("WARN", "line " + lineNo,
                            "unrecognized clock statement; kept as raw only", line));
                }
            } catch (Exception e) {
                out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                        "parse failure: " + e.getMessage(), line));
            }
        }
        if (out.items.isEmpty() && out.diagnostics.isEmpty())
            out.diagnostics.add(new Diagnostic("ERROR", "document", "no clock statements recognized", null));
        return out;
    }

    // ---------------- constraints ----------------
    public static Parsed<ExceptionRule> parseConstraints(String text) {
        Parsed<ExceptionRule> out = new Parsed<>();
        String[] lines = text.split("\\R");
        int seq = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNo = i + 1;
            if (line.isEmpty() || line.startsWith("#")) continue;
            seq++;
            try {
                if (line.startsWith("set_false_path")) {
                    String[] from = objSpec(line, "-from");
                    String[] to = objSpec(line, "-to");
                    out.items.add(new ExceptionRule("EX-" + seq, "FALSE_PATH", 1,
                            from[0], from[1], to[0], to[1], null, lineNo, line));
                } else if (line.startsWith("set_multicycle_path")) {
                    Matcher m = Pattern.compile("set_multicycle_path\\s+(\\S+)").matcher(line);
                    if (!m.find()) {
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                                "multicycle path needs an integer multiplier", line));
                        continue;
                    }
                    Integer mult = parseInt(m.group(1));
                    if (mult == null) {
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                                "multiplier '" + m.group(1) + "' is not an integer; value left unparsed", line));
                        continue;
                    }
                    String type = line.contains("-hold") ? "MULTICYCLE_HOLD" : "MULTICYCLE_SETUP";
                    String[] from = objSpec(line, "-from");
                    String[] to = objSpec(line, "-to");
                    out.items.add(new ExceptionRule("EX-" + seq, type, mult,
                            from[0], from[1], to[0], to[1], null, lineNo, line));
                } else if (line.startsWith("set_clock_groups")) {
                    List<String> groups = new ArrayList<>();
                    Matcher gm = Pattern.compile("-group\\s+\\{([^}]*)}").matcher(line);
                    while (gm.find()) groups.add(gm.group(1).trim());
                    out.items.add(new ExceptionRule("EX-" + seq, "CLOCK_GROUPS", 1,
                            null, null, null, null, groups, lineNo, line));
                } else {
                    out.diagnostics.add(new Diagnostic("WARN", "line " + lineNo,
                            "unrecognized constraint statement; kept as raw only", line));
                    seq--;
                }
            } catch (Exception e) {
                out.diagnostics.add(new Diagnostic("ERROR", "line " + lineNo,
                        "parse failure: " + e.getMessage(), line));
            }
        }
        if (out.items.isEmpty() && out.diagnostics.isEmpty())
            out.diagnostics.add(new Diagnostic("ERROR", "document", "no constraint statements recognized", null));
        return out;
    }

    // ---------------- timing report ----------------
    private static final Pattern PATH_HEAD = Pattern.compile("^Path:\\s*(\\S+)");
    private static final Pattern STARTPOINT = Pattern.compile("Startpoint:\\s*(\\S+)\\s*\\(clock\\s+(\\S+)\\)");
    private static final Pattern ENDPOINT = Pattern.compile("Endpoint:\\s*(\\S+)\\s*\\(clock\\s+(\\S+)\\)");
    private static final Pattern ARRIVAL = Pattern.compile("Data Arrival:\\s*(\\S+)\\s*ns");
    private static final Pattern REQUIRED = Pattern.compile("Data Required:\\s*(\\S+)\\s*ns");

    public static Parsed<PathRec> parseReport(String text) {
        Parsed<PathRec> out = new Parsed<>();
        String[] lines = text.split("\\R");
        String label = null, start = null, end = null, launch = null, capture = null;
        Double arrival = null, required = null;
        int startLine = 0;
        StringBuilder seg = new StringBuilder();
        List<Runnable> flush = new ArrayList<>();
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : "Path: __END__";
            Matcher head = PATH_HEAD.matcher(line.trim());
            if (head.find()) {
                if (label != null) {
                    String l = label, s = start, e = end, lc = launch, cc = capture, raw = seg.toString();
                    Double ar = arrival, rq = required;
                    int ln = startLine;
                    if (s == null || e == null || lc == null || cc == null) {
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + ln,
                                "path " + l + " missing startpoint/endpoint/clock fields; kept raw", raw));
                    } else {
                        out.items.add(new PathRec(l, s, e, lc, cc, ar, rq, raw, ln));
                        if (ar == null || rq == null)
                            out.diagnostics.add(new Diagnostic("WARN", "line " + ln,
                                    "path " + l + " missing arrival/required value; slack left unknown", raw));
                    }
                }
                label = head.group(1);
                start = end = launch = capture = null;
                arrival = required = null;
                startLine = i + 1;
                seg = new StringBuilder();
                if (label.equals("__END__")) break;
            } else {
                Matcher m;
                if ((m = STARTPOINT.matcher(line)).find()) { start = m.group(1); launch = m.group(2); }
                else if ((m = ENDPOINT.matcher(line)).find()) { end = m.group(1); capture = m.group(2); }
                else if ((m = ARRIVAL.matcher(line)).find()) {
                    arrival = parseDouble(m.group(1));
                    if (arrival == null)
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + (i + 1),
                                "arrival '" + m.group(1) + "' is not a number of ns; left unparsed", line.trim()));
                } else if ((m = REQUIRED.matcher(line)).find()) {
                    required = parseDouble(m.group(1));
                    if (required == null)
                        out.diagnostics.add(new Diagnostic("ERROR", "line " + (i + 1),
                                "required '" + m.group(1) + "' is not a number of ns; left unparsed", line.trim()));
                }
            }
            seg.append(line).append('\n');
        }
        flush.forEach(Runnable::run);
        if (out.items.isEmpty() && out.diagnostics.isEmpty())
            out.diagnostics.add(new Diagnostic("ERROR", "document", "no Path blocks recognized", null));
        return out;
    }

    // ---------------- renames ----------------
    public static Parsed<RenameEntry> parseRenames(String text) {
        Parsed<RenameEntry> out = new Parsed<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length == 3 && parts[0].equals("rename")) {
                out.items.add(new RenameEntry(parts[1], parts[2], i + 1));
            } else {
                out.diagnostics.add(new Diagnostic("ERROR", "line " + (i + 1),
                        "expected 'rename <old> <new>'", line));
            }
        }
        return out;
    }

    // ---------------- tool env ----------------
    public static ToolEnv parseToolEnv(String text, List<Diagnostic> diags) {
        String tool = null, version = null, build = null;
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int c = line.indexOf(':');
            if (c < 0) {
                diags.add(new Diagnostic("WARN", "line " + (i + 1), "expected 'key: value'", line));
                continue;
            }
            String k = line.substring(0, c).trim(), v = line.substring(c + 1).trim();
            switch (k) {
                case "tool" -> tool = v;
                case "version" -> version = v;
                case "build" -> build = v;
                default -> diags.add(new Diagnostic("WARN", "line " + (i + 1), "unknown key '" + k + "'", line));
            }
        }
        if (tool == null || version == null)
            diags.add(new Diagnostic("ERROR", "document", "tool env needs at least tool: and version:", null));
        return new ToolEnv(tool, version, build);
    }

    // ---------------- helpers ----------------
    static Double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    static Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static String opt(String line, String flag) {
        Matcher m = Pattern.compile(Pattern.quote(flag) + "\\s+(\\S+)").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private static Integer intOpt(String line, String flag) {
        String v = opt(line, flag);
        return v == null ? null : parseInt(v);
    }

    private static String firstBracket(String line) {
        Matcher m = Pattern.compile("\\[[^]]*]").matcher(line);
        return m.find() ? m.group() : null;
    }

    private static String bracket(String line, String flag) {
        Matcher m = Pattern.compile(Pattern.quote(flag) + "\\s+(\\[[^]]*])").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** "[get_ports CLK_A]" -> "CLK_A" */
    static String stripCmd(String bracketExpr) {
        if (bracketExpr == null) return null;
        String s = bracketExpr;
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        String[] parts = s.trim().split("\\s+", 2);
        return parts.length == 2 ? parts[1].trim() : s.trim();
    }

    /** returns [kind, pattern] from "-from [get_pins u_core/*]" */
    static String[] objSpec(String line, String flag) {
        String b = bracket(line, flag);
        if (b == null) return new String[]{null, null};
        String inner = b.substring(1, b.length() - 1).trim();
        String[] parts = inner.split("\\s+", 2);
        if (parts.length < 2) return new String[]{inner, null};
        return new String[]{parts[0], parts[1]};
    }

    private static List<Double> parseWaveform(String line, double period) {
        Matcher m = Pattern.compile("-waveform\\s+\\{([^}]*)}").matcher(line);
        List<Double> edges = new ArrayList<>();
        if (m.find()) {
            for (String tok : m.group(1).trim().split("\\s+")) {
                Double d = parseDouble(tok);
                if (d != null) edges.add(d);
            }
        }
        if (edges.size() < 2) {
            edges = new ArrayList<>();
            edges.add(0.0);
            edges.add(period / 2.0);
        }
        return edges;
    }

    /** glob with '*' and '?' */
    public static boolean globMatch(String pattern, String value) {
        if (pattern == null || value == null) return false;
        StringBuilder rx = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*' -> rx.append(".*");
                case '?' -> rx.append('.');
                default -> rx.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(rx.toString());
    }
}
