package sta.domain;

import sta.domain.Model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the raw report/constraint text into a structured bundle.
 *  Raw input is immutable in the store; this only builds a projection.
 *  Format errors become diagnostics; offending lines stay in place. */
public final class Parser {

    private static final Pattern SECTION = Pattern.compile("^##\\s*section:\\s*(\\S+)\\s*$");
    private static final Pattern KEYVAL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)=([^|]+)");

    public static ParsedBundle parse(String rawText, String sourceInputId) {
        ParsedBundle out = new ParsedBundle();
        String section = null;
        int clkSeq = 0, genSeq = 0, conSeq = 0, order = 0;
        String[] lines = rawText.split("\r?\n");
        for (int ln = 0; ln < lines.length; ln++) {
            String line = lines[ln].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                Matcher m = SECTION.matcher(line);
                if (m.matches()) section = m.group(1).toLowerCase();
                continue;
            }
            try {
                switch (section == null ? "" : section) {
                    case "tool" -> parseTool(line, out);
                    case "clocks" -> {
                        if (line.startsWith("create_generated_clock")) { genSeq++; parseGenClock(line, out, genSeq, sourceInputId); }
                        else if (line.startsWith("create_clock")) { clkSeq++; parseClock(line, out, clkSeq, sourceInputId); }
                        else out.diagnostics.add("line " + (ln + 1) + ": unknown clocks entry: " + line);
                    }
                    case "constraints" -> { order++; parseConstraint(line, out, ++conSeq, order, sourceInputId, ln); }
                    case "report" -> parsePath(line, out, sourceInputId, ln);
                    default -> out.diagnostics.add("line " + (ln + 1) + ": content outside any known section: " + line);
                }
            } catch (Exception e) {
                out.diagnostics.add("line " + (ln + 1) + ": " + e.getMessage() + " [line kept: " + line + "]");
            }
        }
        return out;
    }

    private static void parseTool(String line, ParsedBundle out) {
        Matcher m = KEYVAL.matcher(line);
        while (m.find()) {
            if (m.group(1).equals("tool")) out.tool.tool = m.group(2).trim();
            if (m.group(1).equals("version")) out.tool.version = m.group(2).trim();
        }
        if (out.tool.tool == null) out.diagnostics.add("tool section line without tool=... : " + line);
    }

    private static String opt(String line, String key) {
        Matcher m = Pattern.compile("-?" + key + "\\s+(\\{[^}]*}|\\S+)").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private static List<Quantity> parseWaveform(String w) {
        List<Quantity> edges = new ArrayList<>();
        if (w == null) return edges;
        String inner = w.startsWith("{") ? w.substring(1, w.length() - 1) : w;
        for (String tok : inner.trim().split("\\s+")) if (!tok.isEmpty()) edges.add(Quantity.parse(tok));
        return edges;
    }

    private static void parseClock(String line, ParsedBundle out, int seq, String src) {
        ClockDef c = new ClockDef();
        c.id = "clk-" + seq;
        c.name = opt(line, "name");
        c.attach = opt(line, "on");
        c.period = Quantity.parse(opt(line, "period"));
        c.waveform = parseWaveform(opt(line, "waveform"));
        c.rawLine = line;
        c.sourceInputId = src;
        if (c.name == null) throw new IllegalArgumentException("create_clock missing -name");
        if (c.period == null || c.period.value == null) {
            if (c.period == null) c.period = new Quantity(null, null, null);
            c.period.diagnostic = "clock period missing or unparseable; kept in place";
        }
        if (c.waveform.isEmpty() && c.period.value != null) {
            c.waveform.add(new Quantity("0", "ps", 0.0));
            c.waveform.add(new Quantity("(period/2)", "ps", c.period.value / 2));
        }
        out.clocks.add(c);
    }

    private static void parseGenClock(String line, ParsedBundle out, int seq, String src) {
        GenClock g = new GenClock();
        g.id = "gclk-" + seq;
        g.name = opt(line, "name");
        g.sourceClock = opt(line, "source");
        g.attach = opt(line, "on");
        String div = opt(line, "divide_by");
        String mul = opt(line, "multiply_by");
        String ph = opt(line, "phase");
        if (div != null) g.divideBy = Integer.parseInt(div);
        if (mul != null) g.multiplyBy = Integer.parseInt(mul);
        g.phase = ph != null ? Quantity.parse(ph) : new Quantity("0deg", "deg", 0.0);
        g.rawLine = line;
        g.sourceInputId = src;
        if (g.name == null || g.sourceClock == null)
            throw new IllegalArgumentException("create_generated_clock missing -name or -source");
        out.genClocks.add(g);
    }

    private static List<String> parseSet(String spec) {
        List<String> out = new ArrayList<>();
        if (spec == null) return out;
        String inner = spec.startsWith("{") ? spec.substring(1, spec.length() - 1) : spec;
        for (String tok : inner.trim().split("\\s+")) if (!tok.isEmpty()) out.add(tok);
        return out;
    }

    private static void parseConstraint(String line, ParsedBundle out, int seq, int order, String src, int ln) {
        Constraint c = new Constraint();
        c.id = "con-" + seq;
        c.rawLine = line;
        c.sourceInputId = src;
        c.textOrder = order;
        if (line.startsWith("set_false_path")) {
            c.kind = ConstraintKind.FALSE_PATH;
            c.fromSet = parseSet(opt(line, "from"));
            c.toSet = parseSet(opt(line, "to"));
        } else if (line.startsWith("set_multicycle_path")) {
            c.kind = ConstraintKind.MULTICYCLE;
            String setup = opt(line, "setup");
            String hold = opt(line, "hold");
            if (setup != null) c.setupMult = Integer.parseInt(setup);
            if (hold != null) c.holdMult = Integer.parseInt(hold);
            if (c.setupMult == null && c.holdMult == null)
                throw new IllegalArgumentException("set_multicycle_path needs -setup N and/or -hold N");
            c.fromSet = parseSet(opt(line, "from"));
            c.toSet = parseSet(opt(line, "to"));
        } else if (line.startsWith("set_clock_groups")) {
            c.kind = ConstraintKind.CLOCK_GROUPS;
            Matcher m = Pattern.compile("-group\\s+\\{([^}]*)}").matcher(line);
            while (m.find()) c.groups.add(parseSet(m.group(1)));
            if (c.groups.size() < 2) throw new IllegalArgumentException("set_clock_groups needs >= 2 -group entries");
        } else {
            out.diagnostics.add("line " + (ln + 1) + ": unsupported constraint (kept verbatim): " + line);
            return;
        }
        out.constraints.add(c);
    }

    private static void parsePath(String line, ParsedBundle out, String src, int ln) {
        if (!line.startsWith("path ")) {
            out.diagnostics.add("line " + (ln + 1) + ": report entry not starting with 'path ' (kept verbatim): " + line);
            return;
        }
        TimingPath p = new TimingPath();
        p.rawLine = line;
        p.sourceInputId = src;
        String[] parts = line.split("\\|");
        p.id = parts[0].trim().substring("path ".length()).trim();
        for (int i = 1; i < parts.length; i++) {
            String kv = parts[i].trim();
            int eq = kv.indexOf('=');
            if (eq < 0) { out.diagnostics.add("path " + p.id + ": malformed field '" + kv + "'"); continue; }
            String k = kv.substring(0, eq).trim(), v = kv.substring(eq + 1).trim();
            switch (k) {
                case "start" -> p.startpoint = v;
                case "end" -> p.endpoint = v;
                case "launch" -> p.launchClock = v;
                case "capture" -> p.captureClock = v;
                case "arrival" -> p.arrival = Quantity.parse(v);
                case "required" -> p.reportedRequired = Quantity.parse(v);
                case "seg" -> {
                    int at = v.lastIndexOf('@');
                    if (at < 0) { out.diagnostics.add("path " + p.id + ": segment missing @delay: '" + v + "'"); break; }
                    p.segments.add(new PathSeg(v.substring(0, at).trim(), Quantity.parse(v.substring(at + 1).trim())));
                }
                default -> out.diagnostics.add("path " + p.id + ": unknown field '" + k + "' (kept verbatim)");
            }
        }
        if (p.startpoint == null || p.endpoint == null)
            out.diagnostics.add("path " + p.id + ": missing start=/end= (kept verbatim)");
        out.paths.add(p);
    }

    private Parser() {}
}
