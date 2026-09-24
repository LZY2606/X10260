package stawb;

import stawb.Model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import-layer parsers. They accept small copy-pasteable samples and raise
 * ParseException with line numbers for clear format errors. Raw text is never
 * modified; parsed forms live only in the derived layer.
 */
public final class Parsers {
    private Parsers() {}

    public static final class ParseException extends RuntimeException {
        public ParseException(String message) { super(message); }
    }

    private static final Pattern TOKEN =
            Pattern.compile("\\{[^}]*\\}|\\[[^\\]]*\\]|\\S+");

    static List<String> tokens(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(line);
        while (m.find()) out.add(m.group());
        return out;
    }

    static String stripBrackets(String tok) {
        if (tok.startsWith("[") && tok.endsWith("]")) {
            String inner = tok.substring(1, tok.length() - 1).trim();
            String[] parts = inner.split("\\s+");
            return parts[parts.length - 1];
        }
        if (tok.startsWith("{") && tok.endsWith("}")) {
            return tok.substring(1, tok.length() - 1).trim();
        }
        return tok;
    }

    static List<String> objectSet(String tok) {
        String inner = stripBrackets(tok);
        List<String> out = new ArrayList<>();
        for (String s : inner.split("\\s+")) {
            if (!s.isBlank()) out.add(stripBrackets(s));
        }
        return out;
    }

    private static String opt(List<String> toks, String flag, int lineNo) {
        int i = toks.indexOf(flag);
        if (i < 0) return null;
        if (i + 1 >= toks.size()) {
            throw new ParseException("line " + lineNo + ": flag '" + flag + "' needs a value");
        }
        return toks.get(i + 1);
    }

    public static List<ClockDef> parseClocks(String content) {
        List<ClockDef> clocks = new ArrayList<>();
        List<GeneratedClock> gens = new ArrayList<>();
        parseClockLines(content, clocks, gens);
        return clocks;
    }

    public static List<GeneratedClock> parseGeneratedClocks(String content) {
        List<ClockDef> clocks = new ArrayList<>();
        List<GeneratedClock> gens = new ArrayList<>();
        parseClockLines(content, clocks, gens);
        return gens;
    }

    private static void parseClockLines(String content, List<ClockDef> clocks,
                                        List<GeneratedClock> gens) {
        String[] lines = content.split("\\R");
        int clockIdx = 0, genIdx = 0;
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            List<String> t = tokens(line);
            String head = t.get(0);
            int lineNo = n + 1;
            switch (head) {
                case "create_clock" -> {
                    String name = opt(t, "-name", lineNo);
                    String period = opt(t, "-period", lineNo);
                    String waveform = opt(t, "-waveform", lineNo);
                    if (name == null || period == null) {
                        throw new ParseException("line " + lineNo + ": create_clock needs -name and -period");
                    }
                    String port = null;
                    for (String tok : t) {
                        if (tok.startsWith("[get_ports")) port = stripBrackets(tok);
                    }
                    List<Quantity> edges = new ArrayList<>();
                    if (waveform != null) {
                        for (String e : objectSet(waveform)) edges.add(Quantity.parse(e));
                    }
                    clocks.add(new ClockDef("clk:" + name, name, port,
                            Quantity.parse(period), edges));
                    clockIdx++;
                }
                case "create_generated_clock" -> {
                    String name = opt(t, "-name", lineNo);
                    String source = opt(t, "-source", lineNo);
                    if (name == null || source == null) {
                        throw new ParseException("line " + lineNo + ": create_generated_clock needs -name and -source");
                    }
                    String divide = opt(t, "-divide_by", lineNo);
                    String multiply = opt(t, "-multiply_by", lineNo);
                    String phase = opt(t, "-phase_shift", lineNo);
                    gens.add(new GeneratedClock("gen:" + name, name, stripBrackets(source),
                            divide == null ? 1 : Integer.parseInt(divide),
                            multiply == null ? 1 : Integer.parseInt(multiply),
                            phase == null ? Quantity.ofPs(0) : Quantity.parse(phase)));
                    genIdx++;
                }
                default -> throw new ParseException(
                        "line " + lineNo + ": unknown clock directive '" + head + "'");
            }
        }
    }

    public static List<ConstraintCmd> parseConstraints(String content) {
        List<ConstraintCmd> out = new ArrayList<>();
        String[] lines = content.split("\\R");
        int idx = 0;
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            List<String> t = tokens(line);
            String head = t.get(0);
            int lineNo = n + 1;
            idx++;
            String id = "cstr:" + idx;
            switch (head) {
                case "set_false_path" -> out.add(new ConstraintCmd(id, idx,
                        ConstraintKind.FALSE_PATH,
                        objectSetOrEmpty(opt(t, "-from", lineNo)),
                        objectSetOrEmpty(opt(t, "-to", lineNo)),
                        objectSetOrEmpty(opt(t, "-through", lineNo)),
                        null, null, line));
                case "set_multicycle_path" -> {
                    String setup = opt(t, "-setup", lineNo);
                    String hold = opt(t, "-hold", lineNo);
                    if ((setup == null) == (hold == null)) {
                        throw new ParseException("line " + lineNo
                                + ": set_multicycle_path needs exactly one of -setup / -hold");
                    }
                    out.add(new ConstraintCmd(id, idx,
                            setup != null ? ConstraintKind.MULTICYCLE_SETUP : ConstraintKind.MULTICYCLE_HOLD,
                            objectSetOrEmpty(opt(t, "-from", lineNo)),
                            objectSetOrEmpty(opt(t, "-to", lineNo)),
                            objectSetOrEmpty(opt(t, "-through", lineNo)),
                            Integer.parseInt(setup != null ? setup : hold), null, line));
                }
                case "set_clock_relation" -> {
                    String from = opt(t, "-from", lineNo);
                    String to = opt(t, "-to", lineNo);
                    String type = opt(t, "-type", lineNo);
                    if (from == null || to == null || type == null) {
                        throw new ParseException("line " + lineNo
                                + ": set_clock_relation needs -from, -to and -type");
                    }
                    out.add(new ConstraintCmd(id, idx, ConstraintKind.CLOCK_RELATION,
                            List.of(stripBrackets(from)), List.of(stripBrackets(to)),
                            List.of(), null, type, line));
                }
                default -> throw new ParseException(
                        "line " + lineNo + ": unknown constraint directive '" + head + "'");
            }
        }
        return out;
    }

    private static List<String> objectSetOrEmpty(String tok) {
        return tok == null ? List.of() : objectSet(tok);
    }

    public static List<TimingPath> parseReport(String content) {
        List<TimingPath> out = new ArrayList<>();
        String[] lines = content.split("\\R");
        String id = null;
        String startpoint = null, endpoint = null, launch = null, capture = null, corner = null;
        Quantity arrival = null, required = null;
        List<Segment> segments = new ArrayList<>();
        StringBuilder rawBlock = new StringBuilder();
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            String trim = line.trim();
            if (trim.isEmpty() || trim.startsWith("#")) continue;
            int lineNo = n + 1;
            if (trim.startsWith("path ")) {
                if (id != null) throw new ParseException("line " + lineNo + ": 'path' inside unterminated block");
                id = trim.substring(5).trim();
                if (id.isEmpty()) throw new ParseException("line " + lineNo + ": path needs an id");
                startpoint = endpoint = launch = capture = corner = null;
                arrival = required = null;
                segments = new ArrayList<>();
                rawBlock = new StringBuilder();
                rawBlock.append(line).append('\n');
                continue;
            }
            if (id == null) {
                throw new ParseException("line " + lineNo + ": content outside of a 'path ... end' block");
            }
            rawBlock.append(line).append('\n');
            if (trim.equals("end")) {
                if (startpoint == null || endpoint == null || launch == null || capture == null) {
                    throw new ParseException("line " + lineNo + ": path " + id
                            + " misses startpoint/endpoint/launch_clock/capture_clock");
                }
                out.add(new TimingPath(id, startpoint, endpoint, launch, capture,
                        corner == null ? "typ" : corner, arrival, required,
                        List.copyOf(segments), rawBlock.toString()));
                id = null;
                continue;
            }
            String[] parts = trim.split("\\s+", 2);
            if (parts.length < 2) throw new ParseException("line " + lineNo + ": expected '<key> <value>'");
            String key = parts[0], val = parts[1];
            switch (key) {
                case "startpoint" -> startpoint = val;
                case "endpoint" -> endpoint = val;
                case "launch_clock" -> launch = val;
                case "capture_clock" -> capture = val;
                case "corner" -> corner = val;
                case "arrival" -> arrival = Quantity.parse(val);
                case "required" -> required = Quantity.parse(val);
                case "segment" -> {
                    String[] sp = val.split("\\s+");
                    if (sp.length != 2) throw new ParseException("line " + lineNo + ": segment needs '<object> <delay>'");
                    segments.add(new Segment(sp[0], Quantity.parse(sp[1])));
                }
                default -> throw new ParseException("line " + lineNo + ": unknown path key '" + key + "'");
            }
        }
        if (id != null) throw new ParseException("end of input: path " + id + " block not closed with 'end'");
        return out;
    }

    public static List<String> parseNetlist(String content) {
        List<String> out = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (int n = 0; n < lines.length; n++) {
            String trim = lines[n].trim();
            if (trim.isEmpty() || trim.startsWith("#")) continue;
            if (trim.contains(" ")) {
                throw new ParseException("line " + (n + 1) + ": netlist object names must not contain spaces");
            }
            out.add(trim);
        }
        return out;
    }

    public static List<RenameEntry> parseRenames(String content) {
        List<RenameEntry> out = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (int n = 0; n < lines.length; n++) {
            String trim = lines[n].trim();
            if (trim.isEmpty() || trim.startsWith("#")) continue;
            String[] parts = trim.split("\\s*->\\s*", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new ParseException("line " + (n + 1) + ": rename entry must be '<old> -> <new>'");
            }
            out.add(new RenameEntry(parts[0], parts[1]));
        }
        return out;
    }
}
