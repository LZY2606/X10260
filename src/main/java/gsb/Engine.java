package gsb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static gsb.Util.*;

/**
 * Derivation engine. Every derived value carries: algorithm version, the input
 * sections it depends on, and its direct sources. Raw representations stay in
 * the input layer; only this derived layer normalizes units.
 */
public final class Engine {
    public static final String ALGO_VERSION = "sta-derive/1";

    public static final String[] SECTIONS = {
        "tool", "report_raw", "clocks", "generated_clocks", "objects", "paths", "constraints"
    };

    private Engine() {}

    /** Per-section fingerprints of an input set. */
    public static ObjectNode sectionFingerprints(ObjectNode input) {
        ObjectNode fps = obj();
        for (String s : SECTIONS) {
            JsonNode n = input.get(s);
            fps.put(s, sha256(canonical(n == null ? null : n)));
        }
        return fps;
    }

    /** Section-level diff between two input sets (machine readable). */
    public static ObjectNode diffSections(ObjectNode a, ObjectNode b) {
        ObjectNode fa = sectionFingerprints(a), fb = sectionFingerprints(b);
        ObjectNode out = obj();
        for (String s : SECTIONS) {
            ObjectNode d = obj();
            boolean same = fa.get(s).asText().equals(fb.get(s).asText());
            d.put("same", same);
            d.put("a_fingerprint", fa.get(s).asText());
            d.put("b_fingerprint", fb.get(s).asText());
            out.set(s, d);
        }
        return out;
    }

    /** Run all derivations for one input set. Returns {"derived": [...]}. */
    public static ObjectNode deriveAll(ObjectNode input) {
        ObjectNode out = obj();
        ArrayNode derived = arr();
        Map<String, ObjectNode> clocks = deriveClocks(input, derived);
        for (JsonNode p : input.withArray("paths")) {
            derived.add(derivePath((ObjectNode) p, input, clocks));
        }
        out.set("derived", derived);
        return out;
    }

    // ---------------------------------------------------------------- clocks

    /** Derive waveforms for base + generated clocks. Returns name -> waveform result. */
    static Map<String, ObjectNode> deriveClocks(ObjectNode input, ArrayNode sink) {
        Map<String, JsonNode> base = new LinkedHashMap<>();
        for (JsonNode c : input.withArray("clocks")) base.put(c.get("name").asText(), c);
        Map<String, JsonNode> gen = new LinkedHashMap<>();
        for (JsonNode c : input.withArray("generated_clocks")) gen.put(c.get("name").asText(), c);

        Map<String, ObjectNode> results = new HashMap<>();
        for (String name : base.keySet()) results.put(name, waveform(input, name, base, gen, results, sink, new ArrayList<>()));
        for (String name : gen.keySet()) results.computeIfAbsent(name,
                n -> waveform(input, n, base, gen, results, sink, new ArrayList<>()));
        return results;
    }

    private static ObjectNode waveform(ObjectNode input, String name,
                                       Map<String, JsonNode> base, Map<String, JsonNode> gen,
                                       Map<String, ObjectNode> memo, ArrayNode sink, List<String> stack) {
        if (memo.containsKey(name)) return memo.get(name);
        ObjectNode result = obj();
        ArrayNode diagnostics = arr();
        result.put("name", name);
        result.put("algo_version", ALGO_VERSION);
        ArrayNode sources = arr().add(name);

        if (stack.contains(name)) {
            diagnostics.add("generated_clock_cycle:" + String.join("->", stack) + "->" + name);
            finishClock(sink, name, "generated", result, diagnostics, sources, null, null, null);
            memo.put(name, result);
            return result;
        }

        if (base.containsKey(name)) {
            JsonNode c = base.get(name);
            Qty period = parseQty(c.get("period"), "ns");
            result.put("kind", "base");
            result.set("period", period.toJson());
            ArrayNode edges = arr();
            if (c.has("waveform") && c.get("waveform").isArray()) {
                for (JsonNode e : c.get("waveform")) {
                    Qty q = parseQty(e, "ns");
                    if (q.ok()) edges.add(q.value); else diagnostics.add(q.diagnostic + ":" + q.raw);
                }
            } else if (period.ok()) {
                edges.add(0.0).add(period.value / 2.0);
                diagnostics.add("waveform_defaulted_to_0_halfperiod");
            }
            finishClock(sink, name, "base", result, diagnostics, sources,
                    period.ok() ? period.value : null, edges, null);
        } else if (gen.containsKey(name)) {
            JsonNode g = gen.get(name);
            String src = g.get("source").asText();
            sources.add(src);
            result.put("kind", "generated");
            result.put("source", src);
            result.put("divide_by", g.path("divide_by").asInt(1));
            result.put("multiply_by", g.path("multiply_by").asInt(1));
            Qty phase = parseQty(g.get("phase"), "deg");
            result.set("phase", phase.toJson());
            List<String> next = new ArrayList<>(stack); next.add(name);
            ObjectNode srcW = waveform(input, src, base, gen, memo, sink, next);
            Double srcPeriod = srcW.has("period_ns") && !srcW.get("period_ns").isNull()
                    ? srcW.get("period_ns").asDouble() : null;
            int div = Math.max(1, g.path("divide_by").asInt(1));
            int mul = Math.max(1, g.path("multiply_by").asInt(1));
            Double period = srcPeriod == null ? null : srcPeriod * div / mul;
            if (period == null) diagnostics.add("source_period_unknown:" + src);
            if (phase.diagnostic != null) diagnostics.add(phase.diagnostic + ":" + phase.raw);
            double offset = (period != null && phase.ok()) ? period * phase.value / 360.0 : 0.0;
            ArrayNode edges = arr();
            if (period != null) edges.add(offset).add(offset + period / 2.0);
            result.put("phase_offset_ns", offset);
            finishClock(sink, name, "generated", result, diagnostics, sources, period, edges, src);
        } else {
            diagnostics.add("clock_not_defined:" + name);
            finishClock(sink, name, "unknown", result, diagnostics, sources, null, null, null);
        }
        memo.put(name, result);
        return result;
    }

    private static void finishClock(ArrayNode sink, String name, String kind, ObjectNode result,
                                    ArrayNode diagnostics, ArrayNode sources,
                                    Double periodNs, ArrayNode edgesNs, String source) {
        result.put("kind", kind);
        if (periodNs != null) result.put("period_ns", periodNs); else result.putNull("period_ns");
        result.set("edges_ns", edgesNs == null ? arr() : edgesNs);
        result.set("diagnostics", diagnostics);
        result.put("status", diagnostics.size() == 0 && periodNs != null ? "OK" : (periodNs != null ? "OK_WITH_DIAGNOSTICS" : "UNKNOWN"));
        result.set("sources", sources);

        ObjectNode row = obj();
        row.put("kind", "clock_waveform");
        row.put("key", name);
        row.put("algo_version", ALGO_VERSION);
        row.set("depends", toJsonArray(List.of("clocks", "generated_clocks")));
        row.set("source_ids", sources);
        row.set("result", result);
        sink.add(row);
    }

    // ----------------------------------------------------------------- paths

    /** True when both clocks resolve to the same root through generated-clock chains. */
    static String rootClock(ObjectNode input, String clock) {
        Map<String, String> genSrc = new HashMap<>();
        for (JsonNode g : input.withArray("generated_clocks"))
            genSrc.put(g.get("name").asText(), g.get("source").asText());
        String cur = clock;
        for (int i = 0; i < 32 && genSrc.containsKey(cur); i++) cur = genSrc.get(cur);
        return cur;
    }

    private static ObjectNode derivePath(ObjectNode path, ObjectNode input, Map<String, ObjectNode> clocks) {
        String pid = path.get("id").asText();
        String from = path.get("from").asText();
        String to = path.get("to").asText();
        String launch = path.get("launch_clock").asText();
        String capture = path.get("capture_clock").asText();

        ObjectNode r = obj();
        r.put("path_id", pid);
        r.put("from", from);
        r.put("to", to);
        r.put("launch_clock", launch);
        r.put("capture_clock", capture);
        r.put("algo_version", ALGO_VERSION);
        ArrayNode diagnostics = arr();
        ArrayNode sources = arr().add(pid).add(launch).add(capture);

        // endpoints must exist in the netlist object inventory
        List<String> objects = toStringList(input.get("objects"));
        if (!objects.contains(from)) diagnostics.add("endpoint_not_in_netlist:" + from);
        if (!objects.contains(to)) diagnostics.add("endpoint_not_in_netlist:" + to);

        Qty arrival = parseQty(path.get("arrival"), "ns");
        Qty required = parseQty(path.get("required"), "ns");
        r.set("arrival", arrival.toJson());
        r.set("required", required.toJson());
        if (arrival.diagnostic != null) diagnostics.add("arrival_" + arrival.diagnostic);
        if (required.diagnostic != null) diagnostics.add("required_" + required.diagnostic);

        // clock domain relationship: never assume synchronous across domains
        String domainStatus;
        if (launch.equals(capture)) domainStatus = "INTRA";
        else if (rootClock(input, launch).equals(rootClock(input, capture))
                && clocks.containsKey(launch) && clocks.containsKey(capture)) domainStatus = "CROSS_RELATED";
        else domainStatus = "UNCONSTRAINED";
        r.put("domain_status", domainStatus);
        if (domainStatus.equals("UNCONSTRAINED"))
            diagnostics.add("cross_domain_no_relationship_defined;marked_unconstrained;not_assumed_synchronous");

        // exception resolution: object-set specificity, never last-text-wins
        ObjectNode exc = resolveExceptions(path, input);
        r.set("exceptions", exc);
        String applied = exc.has("applied") && !exc.get("applied").isNull()
                ? exc.get("applied").get("constraint_id").asText() : null;
        if (applied != null) sources.add(applied);
        for (JsonNode c : exc.withArray("candidates")) sources.add(c.get("constraint_id").asText());

        // multicycle hold pairing
        ObjectNode mc = obj();
        Integer setupN = null;
        if (applied != null) {
            JsonNode c = findConstraint(input, applied);
            if (c != null && c.path("type").asText().equals("multicycle")) {
                setupN = c.path("setup").isInt() ? c.get("setup").asInt() : null;
                mc.put("pairing", "MISSING");
            }
        }
        if (setupN != null) {
            int expectedHold = setupN - 1;
            Integer foundHold = null;
            String holdCid = null;
            for (JsonNode c : input.withArray("constraints")) {
                if (!c.path("type").asText().equals("multicycle") || !c.has("hold")) continue;
                if (covers(c, from, to)) { foundHold = c.get("hold").asInt(); holdCid = c.get("id").asText(); }
            }
            mc.put("setup", setupN);
            mc.put("hold_expected", expectedHold);
            if (foundHold == null) {
                mc.put("pairing", "MISSING");
                mc.put("derived_candidate_hold", expectedHold);
                diagnostics.add("multicycle_hold_missing;derived_candidate_hold=" + expectedHold);
            } else {
                mc.put("hold_found", foundHold);
                mc.put("hold_constraint", holdCid);
                sources.add(holdCid);
                mc.put("pairing", foundHold == expectedHold ? "OK" : "MISMATCH");
                if (foundHold != expectedHold)
                    diagnostics.add("multicycle_hold_pair_mismatch:expected=" + expectedHold + ",found=" + foundHold);
            }
        } else {
            mc.put("pairing", "NOT_APPLICABLE");
        }
        r.set("multicycle", mc);

        // slack
        ObjectNode clkCap = clocks.get(capture);
        Double capPeriod = clkCap != null && clkCap.has("period_ns") && !clkCap.get("period_ns").isNull()
                ? clkCap.get("period_ns").asDouble() : null;
        String effect = applied != null ? findConstraint(input, applied).path("type").asText() : "none";
        r.put("applied_effect", effect);
        String slackStatus;
        Double slack = null;
        if (domainStatus.equals("UNCONSTRAINED")) {
            slackStatus = "UNCONSTRAINED";
        } else if (effect.equals("false_path")) {
            slackStatus = "EXCLUDED";
        } else if (arrival.ok() && required.ok() && capPeriod != null) {
            double reqAdj = required.value + (setupN != null ? (setupN - 1) * capPeriod : 0.0);
            r.put("required_adjusted_ns", reqAdj);
            slack = reqAdj - arrival.value;
            slackStatus = "OK";
        } else {
            slackStatus = "UNKNOWN";
            diagnostics.add("slack_inputs_incomplete");
        }
        r.put("slack_status", slackStatus);
        if (slack != null) r.put("slack_ns", slack); else r.putNull("slack_ns");
        r.set("diagnostics", diagnostics);
        r.set("sources", sources);

        ObjectNode row = obj();
        row.put("kind", "path_analysis");
        row.put("key", pid);
        row.put("algo_version", ALGO_VERSION);
        row.set("depends", toJsonArray(List.of("clocks", "generated_clocks", "objects", "paths", "constraints", "tool")));
        row.set("source_ids", sources);
        row.set("result", r);
        return row;
    }

    private static JsonNode findConstraint(ObjectNode input, String id) {
        for (JsonNode c : input.withArray("constraints"))
            if (c.path("id").asText().equals(id)) return c;
        return null;
    }

    private static boolean memberMatch(JsonNode set, String endpoint) {
        if (set == null || !set.isArray() || set.size() == 0) return true; // unconstrained side
        for (JsonNode e : set) if (e.asText().equals("*") || e.asText().equals(endpoint)) return true;
        return false;
    }

    private static int specificity(JsonNode set) {
        if (set == null || !set.isArray()) return 0;
        int n = 0;
        for (JsonNode e : set) if (!e.asText().equals("*")) n++;
        return n;
    }

    static boolean covers(JsonNode constraint, String from, String to) {
        return memberMatch(constraint.get("from"), from) && memberMatch(constraint.get("to"), to);
    }

    /**
     * Exceptions apply by concrete object-set coverage. Text order is irrelevant:
     * the most specific covering constraint wins; ties keep every legal candidate.
     */
    static ObjectNode resolveExceptions(ObjectNode path, ObjectNode input) {
        String from = path.get("from").asText();
        String to = path.get("to").asText();
        ObjectNode out = obj();
        out.put("basis", "exception_resolved_by_object_set_specificity_not_text_order");
        ArrayNode candidates = arr();
        ArrayNode rejected = arr();
        List<ObjectNode> covering = new ArrayList<>();
        for (JsonNode c : input.withArray("constraints")) {
            String cid = c.get("id").asText();
            if (!covers(c, from, to)) {
                rejected.add(obj().put("constraint_id", cid).put("reason", "not_covering_endpoints"));
                continue;
            }
            int spec = specificity(c.get("from")) + specificity(c.get("to"));
            ObjectNode cand = obj()
                    .put("constraint_id", cid)
                    .put("type", c.path("type").asText())
                    .put("specificity", spec);
            cand.set("text", c.get("text"));
            covering.add(cand);
        }
        covering.sort(Comparator.<ObjectNode>comparingInt(c -> -c.get("specificity").asInt())
                .thenComparing(c -> c.get("constraint_id").asText()));
        if (covering.isEmpty()) {
            out.put("status", "NONE");
            out.putNull("applied");
        } else {
            int top = covering.get(0).get("specificity").asInt();
            List<ObjectNode> best = new ArrayList<>();
            for (ObjectNode c : covering) {
                if (c.get("specificity").asInt() == top) best.add(c);
                else rejected.add(obj().put("constraint_id", c.get("constraint_id").asText())
                        .put("reason", "less_specific_object_set"));
            }
            if (best.size() == 1) {
                out.put("status", "RESOLVED");
                out.set("applied", best.get(0));
                candidates.add(best.get(0));
            } else {
                out.put("status", "AMBIGUOUS");
                out.putNull("applied");
                best.forEach(candidates::add);
                out.put("unresolved_reason", "equal_specificity_all_legal_candidates_kept");
            }
        }
        out.set("candidates", candidates);
        out.set("rejected", rejected);
        return out;
    }
}
