package com.fpgasta.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.model.Ids;
import com.fpgasta.model.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the small, copy-pastable STA report DSL.
 *
 * Sections:
 *   @@TOOL key=value ...
 *   @@CLOCKS <constraint lines>
 *   @@CONSTRAINTS <constraint lines>
 *   @@REPORT <path blocks starting with PATH ...>
 *
 * Every parsed record keeps its original text and a 1-based line number, so the
 * review UI can reconstruct the exact report segment. Structural format errors
 * produce diagnostics with line numbers instead of a vague failure.
 */
public final class StaDslParser {

    private final List<String> lines;
    private final ParseResult result = new ParseResult();
    private final String sourceRef;

    public StaDslParser(String text, String sourceRef) {
        this.lines = splitLines(text);
        this.sourceRef = sourceRef;
    }

    public ParseResult parse() {
        String section = "";
        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String rawLine = lines.get(i);
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("@@")) {
                section = line.substring(2).split("\\s+", 2)[0].toUpperCase();
                String remainder = line.length() > section.length() + 2
                        ? line.substring(section.length() + 2).trim() : "";
                if (section.equals("TOOL") && !remainder.isEmpty()) {
                    parseToolLine(remainder, lineNo, rawLine);
                } else if (!isKnownSection(section)) {
                    error(lineNo, rawLine, "unknown section '@@" + section + "'");
                }
                continue;
            }
            switch (section) {
                case "TOOL" -> parseToolLine(line, lineNo, rawLine);
                case "CLOCKS" -> parseClockLine(line, lineNo, rawLine);
                case "CONSTRAINTS" -> parseConstraintLine(line, lineNo, rawLine);
                case "REPORT" -> parseReportLine(line, lineNo, rawLine);
                default -> error(lineNo, rawLine,
                        "content outside of any @@TOOL/@@CLOCKS/@@CONSTRAINTS/@@REPORT section");
            }
        }
        if (pendingPath != null) {
            error(pendingPathLine, pendingPath.path("rawLine").asText(),
                    "PATH " + pendingPath.path("pathId").asText() + " not closed with ENDPATH");
            finishPendingPath();
        }
        return result;
    }

    private boolean isKnownSection(String section) {
        return section.equals("TOOL") || section.equals("CLOCKS")
                || section.equals("CONSTRAINTS") || section.equals("REPORT");
    }

    private void parseToolLine(String line, int lineNo, String rawLine) {
        ObjectNode node = Json.object();
        node.put("id", Ids.of("tool", sourceRef + ":" + lineNo + ":" + line));
        node.put("lineNo", lineNo);
        node.put("rawLine", rawLine);
        ObjectNode fields = Json.object();
        for (Map.Entry<String, String> entry : keyValues(line).entrySet()) {
            fields.put(entry.getKey(), entry.getValue());
        }
        node.set("fields", fields);
        result.tools().add(node);
    }

    private void parseClockLine(String line, int lineNo, String rawLine) {
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            error(lineNo, rawLine, "empty clock command");
            return;
        }
        String command = tokens.get(0);
        ObjectNode node = baseNode(lineNo, rawLine, command);
        if (command.equals("create_clock")) {
            requireClockArguments(tokens, node, rawLine, lineNo, false);
        } else if (command.equals("create_generated_clock")) {
            requireClockArguments(tokens, node, rawLine, lineNo, true);
        } else if (command.equals("set_clock_groups")) {
            node.set("kind", Json.NODE_FACTORY.textNode("clock_groups"));
            node.set("groups", collectGroups(tokens, node, rawLine, lineNo));
            result.clocks().add(node);
        } else {
            error(lineNo, rawLine, "unknown clock command '" + command
                    + "' (expected create_clock/create_generated_clock/set_clock_groups)");
        }
    }

    private void requireClockArguments(List<String> tokens, ObjectNode node,
                                       String rawLine, int lineNo, boolean generated) {
        Map<String, String> opts = namedOptions(tokens, 1);
        String name = opts.get("-name");
        if (name == null || name.isBlank()) {
            error(lineNo, rawLine, "missing -name for " + (generated ? "generated" : "base") + " clock");
            return;
        }
        node.put("name", name);
        node.put("kind", generated ? "generated" : "base");
        String period = opts.get("-period");
        if (period == null) {
            error(lineNo, rawLine, "missing -period for clock '" + name + "'");
            return;
        }
        ObjectNode periodValue = TimeValue.parse(period);
        node.set("period", periodValue);
        if (!periodValue.path("convertible").asBoolean(false)) {
            diagnostic("warning", lineNo, rawLine,
                    "clock '" + name + "' period not convertible: " + periodValue.path("note").asText());
        }
        ArrayNode wave = Json.NODE_FACTORY.arrayNode();
        String waveform = opts.get("-waveform");
        if (waveform != null) {
            for (String edge : splitList(waveform)) {
                wave.add(TimeValue.parse(edge));
            }
        } else {
            ObjectNode rise = TimeValue.parse("0ns");
            ObjectNode fall = TimeValue.parse(formatHalfPeriod(periodValue));
            wave.add(rise).add(fall);
            if (!periodValue.path("convertible").asBoolean(false)) {
                ((ObjectNode) fall).putNull("ns");
                ((ObjectNode) fall).put("convertible", false);
                ((ObjectNode) fall).put("note", "derived from non-convertible period");
            }
        }
        node.set("waveform", wave);
        for (JsonNode edge : wave) {
            if (!edge.path("convertible").asBoolean(false)) {
                diagnostic("warning", lineNo, rawLine,
                        "clock '" + name + "' keeps an unconvertible waveform edge in place");
                break;
            }
        }
        ArrayNode pins = Json.NODE_FACTORY.arrayNode();
        for (String source : positionalArguments(tokens, 1, "-name", "-period", "-waveform",
                "-source", "-edges", "-edge_shift", "-divide_by", "-multiply_by",
                "-invert", "-combinational")) {
            pins.add(source);
        }
        node.set("objects", pins);
        if (generated) {
            ObjectNode source = Json.object();
            String sourcePin = opts.get("-source");
            source.put("sourceObject", sourcePin == null ? "" : sourcePin);
            source.put("masterClock", opts.getOrDefault("-master_clock", ""));
            if (opts.containsKey("-divide_by")) {
                source.put("divideBy", parseDoubleOrKeep(opts.get("-divide_by")));
            }
            if (opts.containsKey("-multiply_by")) {
                source.put("multiplyBy", parseDoubleOrKeep(opts.get("-multiply_by")));
            }
            if (opts.containsKey("-edge_shift")) {
                ArrayNode shifts = Json.NODE_FACTORY.arrayNode();
                for (String edgeShift : splitList(opts.get("-edge_shift"))) {
                    shifts.add(TimeValue.parse(edgeShift));
                }
                source.set("edgeShift", shifts);
            }
            if (opts.containsKey("-edges")) {
                ArrayNode edges = Json.NODE_FACTORY.arrayNode();
                for (String edge : splitList(opts.get("-edges"))) {
                    edges.add(parseDoubleOrKeep(edge));
                }
                source.set("edges", edges);
            }
            source.put("invert", tokens.contains("-invert"));
            node.set("generatedSource", source);
        }
        result.clocks().add(node);
    }

    private com.fasterxml.jackson.databind.JsonNode parseDoubleOrKeep(String raw) {
        try {
            return Json.NODE_FACTORY.numberNode(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            return Json.NODE_FACTORY.textNode(raw);
        }
    }

    private String formatHalfPeriod(ObjectNode period) {
        if (!period.path("convertible").asBoolean(false)) {
            return "";
        }
        double halfNs = period.path("ns").asDouble() / 2.0d;
        return trimDouble(halfNs) + "ns";
    }

    static String trimDouble(double value) {
        if (value == Math.floor(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }


    private ArrayNode collectGroups(List<String> tokens, ObjectNode node, String rawLine, int lineNo) {
        ArrayNode groups = Json.NODE_FACTORY.arrayNode();
        boolean physicallyExclusive = tokens.contains("-physically_exclusive");
        boolean asynchronous = tokens.contains("-asynchronous");
        node.put("relation", physicallyExclusive ? "physically_exclusive"
                : asynchronous ? "asynchronous" : "logically_exclusive");
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i).equals("-group")) {
                ArrayNode group = Json.NODE_FACTORY.arrayNode();
                int j = i + 1;
                while (j < tokens.size() && !tokens.get(j).startsWith("-")) {
                    group.add(tokens.get(j));
                    j++;
                }
                if (group.isEmpty()) {
                    error(lineNo, rawLine, "set_clock_groups -group requires at least one clock");
                }
                groups.add(group);
                i = j - 1;
            }
        }
        if (groups.size() < 2) {
            error(lineNo, rawLine, "set_clock_groups needs at least two groups to define a clock relation");
        }
        if (!physicallyExclusive && !asynchronous) {
            diagnostic("warning", lineNo, rawLine,
                    "set_clock_groups without -asynchronous/-physically_exclusive is treated as logically exclusive");
        }
        return groups;
    }

    private void parseConstraintLine(String line, int lineNo, String rawLine) {
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            error(lineNo, rawLine, "empty constraint command");
            return;
        }
        String command = tokens.get(0);
        if (command.equals("set_false_path")) {
            ObjectNode node = baseNode(lineNo, rawLine, command);
            attachPathSpec(tokens, node, lineNo, rawLine);
            result.exceptions().add(node);
        } else if (command.equals("set_multicycle_path")) {
            if (tokens.size() < 2) {
                error(lineNo, rawLine, "set_multicycle_path requires a cycle count");
                return;
            }
            int cycles;
            try {
                cycles = Integer.parseInt(tokens.get(1));
            } catch (NumberFormatException e) {
                error(lineNo, rawLine, "multicycle count must be an integer, found '" + tokens.get(1) + "'");
                return;
            }
            ObjectNode node = baseNode(lineNo, rawLine, command);
            node.put("cycles", cycles);
            Map<String, String> opts = namedOptions(tokens, 2);
            String setupHold = opts.getOrDefault("-setup", null) != null ? "setup" : null;
            if (opts.containsKey("-hold")) {
                setupHold = "hold";
            }
            node.put("checkType", setupHold == null ? "setup" : setupHold);
            node.put("checkTypeExplicit", setupHold != null);
            attachPathSpec(tokens, node, lineNo, rawLine);
            result.exceptions().add(node);
        } else if (command.equals("set_clock_groups")) {
            parseClockLine(line, lineNo, rawLine);
        } else {
            error(lineNo, rawLine, "unsupported constraint '" + command
                    + "' (accepted: set_false_path, set_multicycle_path, set_clock_groups)");
        }
    }

    private void attachPathSpec(List<String> tokens, ObjectNode node, int lineNo, String rawLine) {
        ArrayNode from = Json.NODE_FACTORY.arrayNode();
        ArrayNode through = Json.NODE_FACTORY.arrayNode();
        ArrayNode to = Json.NODE_FACTORY.arrayNode();
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            ArrayNode target = switch (token) {
                case "-from", "-start" -> from;
                case "-through" -> through;
                case "-to", "-end" -> to;
                default -> null;
            };
            if (target != null) {
                int j = i + 1;
                while (j < tokens.size() && !tokens.get(j).startsWith("-")) {
                    target.add(tokens.get(j));
                    j++;
                }
                if (j == i + 1) {
                    error(lineNo, rawLine, token + " requires at least one object");
                }
                i = j - 1;
            } else if (token.startsWith("-") && !token.equals("-setup") && !token.equals("-hold")) {
                diagnostic("warning", lineNo, rawLine, "ignoring unsupported path-specifier " + token);
            }
        }
        node.set("from", from);
        node.set("through", through);
        node.set("to", to);
        if (from.isEmpty() && through.isEmpty() && to.isEmpty()) {
            error(lineNo, rawLine, "exception constraint has no -from/-through/-to object set");
        }
    }


    private ObjectNode pendingPath;
    private int pendingPathLine;

    private void parseReportLine(String line, int lineNo, String rawLine) {
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            return;
        }
        if (tokens.get(0).equals("PATH")) {
            if (pendingPath != null) {
                finishPendingPath();
            }
            pendingPath = baseNode(lineNo, rawLine, "PATH");
            pendingPathLine = lineNo;
            pendingPath.put("pathId", tokens.size() > 1 ? tokens.get(1) : "");
            pendingPath.set("segments", Json.NODE_FACTORY.arrayNode());
            if (tokens.size() < 2) {
                error(lineNo, rawLine, "PATH record requires an id, e.g. PATH p1");
            }
            return;
        }
        if (pendingPath == null) {
            error(lineNo, rawLine, "REPORT detail line outside of a PATH block");
            return;
        }
        String keyword = tokens.get(0);
        switch (keyword) {
            case "START", "END", "CLOCKS", "ARRIVAL", "REQUIRED", "SLACK" -> {
                Map<String, String> opts = namedOptions(tokens, 1);
                ObjectNode detail = Json.object();
                detail.put("lineNo", lineNo);
                detail.put("rawLine", rawLine);
                detail.put("kind", keyword.toLowerCase());
                if (keyword.equals("CLOCKS")) {
                    detail.put("launch", opts.getOrDefault("launch", ""));
                    detail.put("capture", opts.getOrDefault("capture", ""));
                    detail.put("relation", opts.getOrDefault("relation", "undefined"));
                } else if (keyword.equals("SLACK")) {
                    detail.put("rawValue", opts.getOrDefault("value", ""));
                    detail.set("time", TimeValue.parse(opts.getOrDefault("value", "")));
                    detail.put("met", opts.get("met") != null && Boolean.parseBoolean(opts.get("met")));
                } else if (keyword.equals("ARRIVAL") || keyword.equals("REQUIRED")) {
                    detail.put("rawValue", opts.getOrDefault("value", ""));
                    detail.set("time", TimeValue.parse(opts.getOrDefault("value", "")));
                } else {
                    detail.put("object", opts.getOrDefault("object", ""));
                    detail.put("clock", opts.getOrDefault("clock", ""));
                    detail.put("edge", opts.getOrDefault("edge", ""));
                }
                ((ArrayNode) pendingPath.get("segments")).add(detail);
            }
            case "DELTA" -> {
                if (tokens.size() < 4) {
                    error(lineNo, rawLine, "DELTA expects: DELTA <fromObject> <toObject> <delay> [cell=...]");
                    return;
                }
                ObjectNode segment = Json.object();
                segment.put("lineNo", lineNo);
                segment.put("rawLine", rawLine);
                segment.put("kind", "delta");
                segment.put("fromObject", tokens.get(1));
                segment.put("toObject", tokens.get(2));
                segment.put("rawDelay", tokens.get(3));
                segment.set("delay", TimeValue.parse(tokens.get(3)));
                Map<String, String> opts = namedOptions(tokens, 4);
                segment.put("cell", opts.getOrDefault("cell", ""));
                ((ArrayNode) pendingPath.get("segments")).add(segment);
            }
            case "ENDPATH" -> {
                finishPendingPath();
                pendingPath = null;
            }
            default -> error(lineNo, rawLine, "unknown REPORT keyword '" + keyword
                    + "' (expected START/DELTA/END/CLOCKS/ARRIVAL/REQUIRED/SLACK/ENDPATH)");
        }
    }

    private void finishPendingPath() {
        Map<String, String> seen = new LinkedHashMap<>();
        for (JsonNode segmentNode : pendingPath.get("segments")) {
            seen.put(segmentNode.path("kind").asText(), segmentNode.path("rawLine").asText());
        }
        for (String required : List.of("start", "end", "clocks", "arrival", "required", "slack")) {
            if (!seen.containsKey(required)) {
                error(pendingPathLine, pendingPath.path("rawLine").asText(),
                        "PATH " + pendingPath.path("pathId").asText() + " is missing " + required.toUpperCase()
                                + " segment");
            }
        }
        result.paths().add(pendingPath);
    }


    private ObjectNode baseNode(int lineNo, String rawLine, String command) {
        ObjectNode node = Json.object();
        node.put("id", Ids.of("rec", sourceRef + ":" + lineNo + ":" + rawLine));
        node.put("lineNo", lineNo);
        node.put("rawLine", rawLine);
        node.put("command", command);
        node.put("sourceRef", sourceRef);
        return node;
    }

    private void error(int lineNo, String rawLine, String message) {
        diagnostic("error", lineNo, rawLine, message);
    }

    private void diagnostic(String severity, int lineNo, String rawLine, String message) {
        ObjectNode node = Json.object();
        node.put("id", Ids.of("diag", sourceRef + ":" + lineNo + ":" + severity + ":" + message));
        node.put("severity", severity);
        node.put("lineNo", lineNo);
        node.put("rawLine", rawLine);
        node.put("message", message);
        result.diagnostics().add(node);
    }

    static List<String> splitLines(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                result.add(stripTrailingCarriageReturn(text.substring(start, i)));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            result.add(stripTrailingCarriageReturn(text.substring(start)));
        }
        return result;
    }

    private static String stripTrailingCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }

    private static String stripComment(String line) {
        boolean inBrace = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                inBrace = true;
            } else if (c == '}') {
                inBrace = false;
            } else if (c == '#' && !inBrace) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '{' || c == '[') {
                char close = c == '{' ? '}' : ']';
                int end = line.indexOf(close, i + 1);
                if (end < 0) {
                    tokens.add(line.substring(i));
                    i = line.length();
                } else {
                    tokens.add(line.substring(i + 1, end));
                    i = end + 1;
                }
            } else {
                int start = i;
                while (i < line.length() && !Character.isWhitespace(line.charAt(i))) {
                    i++;
                }
                tokens.add(line.substring(start, i));
            }
        }
        return tokens;
    }

    private static List<String> splitList(String raw) {
        List<String> result = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private static Map<String, String> keyValues(String line) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String token : line.split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals > 0) {
                map.put(token.substring(0, equals), token.substring(equals + 1));
            }
        }
        return map;
    }

    private static Map<String, String> namedOptions(List<String> tokens, int fromIndex) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = fromIndex; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("-") && !token.equals("-invert") && !token.equals("-combinational")) {
                String key = token;
                if (i + 1 < tokens.size()) {
                    String next = tokens.get(i + 1);
                    if (!next.startsWith("-")) {
                        options.put(key, next);
                        i++;
                    } else {
                        options.put(key, "");
                    }
                } else {
                    options.put(key, "");
                }
            }
        }
        return options;
    }

    private static boolean looksLikeFlag(String token) {
        return token.equals("-setup") || token.equals("-hold") || token.equals("-invert")
                || token.equals("-asynchronous") || token.equals("-physically_exclusive")
                || token.equals("-combinational");
    }

    private static List<String> positionalArguments(List<String> tokens, int fromIndex, String... optionKeys) {
        List<String> positionals = new ArrayList<>();
        java.util.Set<String> skip = new java.util.HashSet<>(java.util.Arrays.asList(optionKeys));
        for (int i = fromIndex; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (skip.contains(token)) {
                i++;
                continue;
            }
            if (token.startsWith("-")) {
                continue;
            }
            positionals.add(token);
        }
        return positionals;
    }

}
