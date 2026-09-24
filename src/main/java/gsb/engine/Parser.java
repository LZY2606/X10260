package gsb.engine;

import gsb.model.Model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsers for the raw input kinds. Raw text is preserved untouched on the
 *  raw-input layer; parsing here only produces the normalized working model.
 *  Values that cannot be converted stay in place and produce diagnostics. */
public final class Parser {

    public record Diagnostic(int line, String message, String rawText) {}

    public static final class ParseException extends RuntimeException {
        public final List<Diagnostic> diags;
        public ParseException(List<Diagnostic> diags) {
            super(diags.isEmpty() ? "parse error" : diags.get(0).message());
            this.diags = diags;
        }
    }

    public static final class ClocksParsed {
        public final List<ClockDef> clocks = new ArrayList<>();
        public final List<GenClockDef> genClocks = new ArrayList<>();
        public final List<Diagnostic> diagnostics = new ArrayList<>();
    }

    private static long parsePs(String token, int line, List<Diagnostic> diags, String raw) {
        Matcher m = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)(ps|ns)?$").matcher(token.trim());
        if (!m.matches()) {
            diags.add(new Diagnostic(line, "unconvertible time value: " + token, raw));
            return -1;
        }
        double v = Double.parseDouble(m.group(1));
        String unit = m.group(2) == null ? "ps" : m.group(2);
        return Math.round(unit.equals("ns") ? v * 1000.0 : v);
    }

    /** Parse clock definitions:
     *  create_clock -name N -period 5000ps -waveform {0 2500} [get_ports P]
     *  create_generated_clock -name N -source M -master_pin pin -divide_by 2 [-multiply_by k] [-phase_shift 250ps] */
    public static ClocksParsed parseClocks(String text) {
        ClocksParsed out = new ClocksParsed();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int ln = i + 1;
            if (line.startsWith("create_clock ")) {
                String name = opt(line, "-name");
                String port = opt(line, "-port");
                String periodTok = opt(line, "-period");
                String wave = opt(line, "-waveform");
                if (name == null || periodTok == null || wave == null) {
                    out.diagnostics.add(new Diagnostic(ln, "create_clock requires -name/-period/-waveform", raw));
                    continue;
                }
                long period = parsePs(periodTok, ln, out.diagnostics, raw);
                List<Long> edges = new ArrayList<>();
                Matcher wm = Pattern.compile("\\{([^}]*)}").matcher(wave);
                if (wm.find()) {
                    for (String t : wm.group(1).trim().split("\\s+")) {
                        if (!t.isEmpty()) edges.add(parsePs(t, ln, out.diagnostics, raw));
                    }
                } else {
                    out.diagnostics.add(new Diagnostic(ln, "waveform must be {rise fall ...}", raw));
                }
                if (period >= 0 && !edges.contains(-1L)) {
                    out.clocks.add(new ClockDef("clk:" + name, name, port == null ? "" : port, period, edges));
                }
            } else if (line.startsWith("create_generated_clock ")) {
                String name = opt(line, "-name");
                String source = opt(line, "-source");
                String pin = opt(line, "-master_pin");
                String div = opt(line, "-divide_by");
                String mul = opt(line, "-multiply_by");
                String shift = opt(line, "-phase_shift");
                if (name == null || source == null || (div == null && mul == null)) {
                    out.diagnostics.add(new Diagnostic(ln, "generated clock requires -name/-source and -divide_by or -multiply_by", raw));
                    continue;
                }
                long shiftPs = shift == null ? 0 : parsePs(shift, ln, out.diagnostics, raw);
                out.genClocks.add(new GenClockDef("gclk:" + name, name, source, pin == null ? "" : pin,
                        div == null ? 1 : Integer.parseInt(div),
                        mul == null ? 1 : Integer.parseInt(mul), Math.max(shiftPs, 0)));
            } else {
                out.diagnostics.add(new Diagnostic(ln, "unrecognized clock statement", raw));
            }
        }
        return out;
    }

    /** Parse explicit clock relations:
     *  set_clock_relation -from A -to B -kind related|async */
    public static List<ClockRelation> parseRelations(String text, List<Diagnostic> diags) {
        List<ClockRelation> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int ln = i + 1;
            if (!line.startsWith("set_clock_relation ")) {
                diags.add(new Diagnostic(ln, "unrecognized relation statement", raw));
                continue;
            }
            String from = opt(line, "-from"), to = opt(line, "-to"), kind = opt(line, "-kind");
            if (from == null || to == null || kind == null) {
                diags.add(new Diagnostic(ln, "relation requires -from/-to/-kind", raw));
                continue;
            }
            out.add(new ClockRelation("rel:" + from + "->" + to, from, to, kind));
        }
        return out;
    }

    /** Parse exception constraints:
     *  set_false_path -from {a b} -to {c}
     *  set_multicycle_path -setup 2 [-hold 1] -from {a} -to {b} */
    public static List<ConstraintDef> parseConstraints(String text, List<Diagnostic> diags) {
        List<ConstraintDef> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int seq = 0;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int ln = i + 1;
            String type;
            if (line.startsWith("set_false_path ")) type = "false_path";
            else if (line.startsWith("set_multicycle_path ")) type = "multicycle_path";
            else { diags.add(new Diagnostic(ln, "unrecognized constraint statement", raw)); continue; }
            List<String> from = optSet(line, "-from");
            List<String> to = optSet(line, "-to");
            if (from.isEmpty() || to.isEmpty()) {
                diags.add(new Diagnostic(ln, "constraint requires non-empty -from and -to object sets", raw));
                continue;
            }
            Integer setup = null, hold = null;
            String s = opt(line, "-setup"), h = opt(line, "-hold");
            if (s != null) setup = Integer.parseInt(s);
            if (h != null) hold = Integer.parseInt(h);
            if (type.equals("multicycle_path") && setup == null) {
                diags.add(new Diagnostic(ln, "multicycle_path requires -setup N", raw));
                continue;
            }
            out.add(new ConstraintDef("cxn:" + type + ":" + (++seq), type, from, to, setup, hold, ln));
        }
        return out;
    }

    /** Parse the post-synthesis timing report excerpt. */
    public static List<TimingPath> parseReport(String text, List<Diagnostic> diags) {
        List<TimingPath> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        String id = null, start = null, end = null, sclk = null, eclk = null, check = null;
        Long arrival = null, required = null;
        List<PathSegment> segs = new ArrayList<>();
        Pattern segPat = Pattern.compile("^Segment:\\s*(\\d+)\\s+cell=(\\S+)\\s+net=(\\S+)\\s+delay=(\\S+)$");
        for (int i = 0; i <= lines.length; i++) {
            String raw = i < lines.length ? lines[i] : "Path:";
            String line = raw.trim();
            if (line.startsWith("Path:") || i == lines.length) {
                if (id != null) {
                    if (start == null || end == null || sclk == null || eclk == null || check == null
                            || arrival == null || required == null || segs.isEmpty()) {
                        diags.add(new Diagnostic(i, "incomplete path block: " + id, id));
                    } else {
                        out.add(new TimingPath(id, start, end, sclk, eclk, check,
                                List.copyOf(segs), arrival, required));
                    }
                }
                if (i == lines.length) break;
                id = line.substring("Path:".length()).trim();
                if (id.isEmpty()) diags.add(new Diagnostic(i + 1, "Path id missing", raw));
                start = end = sclk = eclk = check = null;
                arrival = required = null;
                segs = new ArrayList<>();
                continue;
            }
            if (id == null) {
                if (!line.isEmpty() && !line.startsWith("#"))
                    diags.add(new Diagnostic(i + 1, "content outside path block", raw));
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("Startpoint:")) {
                Matcher m = Pattern.compile("Startpoint:\\s*(\\S+)\\s*\\(clock\\s+(\\S+)\\)").matcher(line);
                if (m.find()) { start = m.group(1); sclk = m.group(2); }
                else diags.add(new Diagnostic(i + 1, "bad Startpoint line", raw));
            } else if (line.startsWith("Endpoint:")) {
                Matcher m = Pattern.compile("Endpoint:\\s*(\\S+)\\s*\\(clock\\s+(\\S+)\\)").matcher(line);
                if (m.find()) { end = m.group(1); eclk = m.group(2); }
                else diags.add(new Diagnostic(i + 1, "bad Endpoint line", raw));
            } else if (line.startsWith("Check:")) {
                check = line.substring("Check:".length()).trim();
            } else if (line.startsWith("Arrival:")) {
                long v = parsePs(line.substring("Arrival:".length()).trim(), i + 1, diags, raw);
                if (v >= 0) arrival = v;
            } else if (line.startsWith("Required:")) {
                long v = parsePs(line.substring("Required:".length()).trim(), i + 1, diags, raw);
                if (v >= 0) required = v;
            } else if (line.startsWith("Segment:")) {
                Matcher m = segPat.matcher(line);
                if (!m.matches()) { diags.add(new Diagnostic(i + 1, "bad Segment line", raw)); continue; }
                long d = parsePs(m.group(4), i + 1, diags, raw);
                if (d >= 0) segs.add(new PathSegment(Integer.parseInt(m.group(1)), m.group(2), m.group(3), d));
            } else {
                diags.add(new Diagnostic(i + 1, "unrecognized report line", raw));
            }
        }
        return out;
    }

    /** Netlist object list, one name per line. */
    public static List<String> parseObjects(String text, List<Diagnostic> diags) {
        List<String> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.contains(" ")) { diags.add(new Diagnostic(i + 1, "object name contains whitespace", lines[i])); continue; }
            out.add(line);
        }
        return out;
    }

    /** Rename map: old=new per line. */
    public static List<String[]> parseRenames(String text, List<Diagnostic> diags) {
        List<String[]> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] kv = line.split("=", -1);
            if (kv.length != 2 || kv[0].isBlank() || kv[1].isBlank()) {
                diags.add(new Diagnostic(i + 1, "rename line must be old=new", lines[i]));
                continue;
            }
            out.add(new String[]{kv[0].trim(), kv[1].trim()});
        }
        return out;
    }

    private static String opt(String line, String key) {
        Matcher m = Pattern.compile(Pattern.quote(key) + "\\s+(\\{[^}]*}|\\S+)").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> optSet(String line, String key) {
        String v = opt(line, key);
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        if (v.startsWith("{")) {
            for (String t : v.substring(1, v.length() - 1).trim().split("\\s+"))
                if (!t.isEmpty()) out.add(t);
        } else out.add(v);
        return out;
    }
}
