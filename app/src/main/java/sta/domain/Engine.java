package sta.domain;

import sta.domain.Model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Derived-layer computation. Algorithm is versioned; every result carries
 *  algoVersion, a parameter fingerprint and its direct sources. Candidates,
 *  rejection reasons and unknown states are first-class data. */
public final class Engine {

    public static final String ALGO_VERSION = "sta-engine/1.3.0";

    // ---------- derived result kinds ----------
    public enum PathStatus {
        CONSTRAINED,        // clocks related, slack computed
        UNCONSTRAINED,      // no clock relationship defined; NOT assumed synchronous
        FALSE_PATH,         // covered by a winning false_path exception
        UNDECIDED           // conflicting exceptions with equal specificity; all legal candidates kept
    }

    public static final class ExceptionHit {
        public String constraintId;
        public ConstraintKind kind;
        public int specificity;          // number of exact (non-glob) object names matched in from+to sets
        public List<String> matchedFrom = new ArrayList<>();
        public List<String> matchedTo = new ArrayList<>();
        public String outcome;           // applied | rejected:<reason> | candidate
        public Integer setupMult;
        public Integer holdMult;         // effective hold multiplier after pairing
        public String holdPairing;       // "explicit" | "paired:setup-1" | null
    }

    public static final class PathAnalysis {
        public String pathId;
        public PathStatus status;
        public String launchClock;
        public String captureClock;
        public String clockRelation;     // "same" | "generated-chain" | "none"
        public Double launchEdgePs;      // normalized, derived layer
        public Double captureEdgePs;
        public Double requiredPs;        // computed required (derived)
        public Double arrivalPs;         // normalized from raw
        public Double slackPs;           // null when unknown
        public List<ExceptionHit> hits = new ArrayList<>();
        public List<String> candidates = new ArrayList<>();   // legal outcomes when undecided
        public List<String> diagnostics = new ArrayList<>();  // unknown / unconvertible values
        public String effectiveExceptionId; // winning exception, if any
    }

    public static final class ClockInfo {
        public String name;
        public String rootMaster;        // root master clock name for relation checks
        public List<Double> edgesPs = new ArrayList<>(); // one period of edges, normalized
        public double periodPs;
        public String via;               // "base" | description of generated derivation incl. phase
        public String diagnostic;
    }

    public static final class AnalysisResult {
        public Map<String, ClockInfo> clocks = new LinkedHashMap<>();
        public List<PathAnalysis> paths = new ArrayList<>();
        public List<String> diagnostics = new ArrayList<>();
        public String fingerprint;
    }

    // ---------- clock graph ----------

    private static ClockInfo deriveClock(ClockDef c, List<String> diags) {
        ClockInfo ci = new ClockInfo();
        ci.name = c.name;
        ci.rootMaster = c.name;
        ci.via = "base";
        Double per = c.period.toPs();
        if (per == null) { ci.diagnostic = "period not convertible: " + c.period.display(); ci.periodPs = Double.NaN; diags.add(ci.diagnostic); return ci; }
        ci.periodPs = per;
        if (c.waveform.isEmpty()) { ci.edgesPs.add(0.0); ci.edgesPs.add(per / 2); }
        else for (Quantity q : c.waveform) {
            Double ps = q.toPs();
            if (ps == null) { ci.diagnostic = "waveform edge not convertible: " + q.display(); diags.add(ci.diagnostic); }
            else ci.edgesPs.add(ps);
        }
        return ci;
    }

    private static ClockInfo deriveGenClock(GenClock g, Map<String, ClockInfo> resolved, List<String> diags) {
        ClockInfo ci = new ClockInfo();
        ci.name = g.name;
        ClockInfo master = resolved.get(g.sourceClock);
        if (master == null) {
            ci.diagnostic = "generated clock source '" + g.sourceClock + "' unresolved; left unconstrained";
            ci.rootMaster = g.name; // self-rooted: no relation to anything
            ci.periodPs = Double.NaN;
            diags.add(ci.diagnostic);
            return ci;
        }
        ci.rootMaster = master.rootMaster;
        ci.periodPs = master.periodPs * g.divideBy / g.multiplyBy;
        double phasePs = 0.0;
        if (g.phase != null && g.phase.value != null) {
            if (g.phase.unit == null || g.phase.unit.equals("deg")) phasePs = g.phase.value / 360.0 * ci.periodPs;
            else { Double ps = g.phase.toPs(); if (ps != null) phasePs = ps; else { ci.diagnostic = g.phase.diagnostic; diags.add(ci.diagnostic); } }
        }
        for (Double e : master.edgesPs) ci.edgesPs.add(e * g.divideBy / g.multiplyBy + phasePs);
        ci.via = "generated from " + g.sourceClock + " x" + g.multiplyBy + "/" + g.divideBy
                + (phasePs != 0.0 ? " phase+" + fmt(phasePs) + "ps(" + g.phase.raw + ")" : "");
        return ci;
    }

    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.3f", v); }

    // ---------- exception matching ----------

    private static boolean globMatch(String pattern, String name) {
        if (pattern.equals(name)) return true;
        if (!pattern.contains("*")) return false;
        StringBuilder re = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') re.append(".*");
            else if ("\\.[]{}()+-^$|?".indexOf(ch) >= 0) re.append('\\').append(ch);
            else re.append(ch);
        }
        return name.matches(re.toString());
    }

    /** Specificity = count of exact (non-glob) object names in the matched sets.
     *  Textual order is deliberately NOT used: exceptions are scoped by object
     *  sets, and the most specific object set wins. */
    private static ExceptionHit match(Constraint c, TimingPath p) {
        List<String> mf = new ArrayList<>(), mt = new ArrayList<>();
        boolean fromOk = c.fromSet.isEmpty();
        for (String pat : c.fromSet) if (globMatch(pat, p.startpoint)) { mf.add(pat); fromOk = true; }
        boolean toOk = c.toSet.isEmpty();
        for (String pat : c.toSet) if (globMatch(pat, p.endpoint)) { mt.add(pat); toOk = true; }
        if (!fromOk || !toOk) return null;
        ExceptionHit h = new ExceptionHit();
        h.constraintId = c.id;
        h.kind = c.kind;
        h.matchedFrom = mf;
        h.matchedTo = mt;
        int spec = 0;
        for (String s : mf) if (!s.contains("*")) spec++;
        for (String s : mt) if (!s.contains("*")) spec++;
        h.specificity = spec;
        if (c.kind == ConstraintKind.MULTICYCLE) {
            h.setupMult = c.setupMult;
            if (c.holdMult != null) { h.holdMult = c.holdMult; h.holdPairing = "explicit"; }
            else if (c.setupMult != null) { h.holdMult = c.setupMult - 1; h.holdPairing = "paired:setup-1"; }
        }
        return h;
    }

    // ---------- main entry ----------

    public static AnalysisResult analyze(List<ParsedBundle> inputs) {
        AnalysisResult r = new AnalysisResult();
        List<String> diags = r.diagnostics;

        // merge inputs in version order; later versions of the same object id replace earlier ones
        Map<String, ClockDef> clocks = new LinkedHashMap<>();
        Map<String, GenClock> gens = new LinkedHashMap<>();
        Map<String, Constraint> cons = new LinkedHashMap<>();
        Map<String, TimingPath> paths = new LinkedHashMap<>();
        for (ParsedBundle b : inputs) {
            for (ClockDef c : b.clocks) clocks.put(c.name, c);
            for (GenClock g : b.genClocks) gens.put(g.name, g);
            for (Constraint c : b.constraints) cons.put(c.id + "@" + c.sourceInputId, c);
            for (TimingPath p : b.paths) paths.put(p.id, p);
            diags.addAll(b.diagnostics);
        }

        // resolve clock graph: base clocks first, then generated in dependency order
        Map<String, ClockInfo> resolved = new LinkedHashMap<>();
        for (ClockDef c : clocks.values()) resolved.put(c.name, deriveClock(c, diags));
        List<GenClock> pending = new ArrayList<>(gens.values());
        for (int guard = 0; guard < 10 && !pending.isEmpty(); guard++) {
            List<GenClock> next = new ArrayList<>();
            for (GenClock g : pending) {
                if (resolved.containsKey(g.sourceClock)) resolved.put(g.name, deriveGenClock(g, resolved, diags));
                else next.add(g);
            }
            if (next.size() == pending.size()) { // no progress: unresolved sources
                for (GenClock g : next) resolved.put(g.name, deriveGenClock(g, resolved, diags));
                break;
            }
            pending = next;
        }
        r.clocks.putAll(resolved);

        // clock-group declarations (asynchronous => explicitly unrelated)
        List<String[]> asyncPairs = new ArrayList<>();
        for (Constraint c : cons.values()) if (c.kind == ConstraintKind.CLOCK_GROUPS) {
            for (int i = 0; i < c.groups.size(); i++)
                for (int j = i + 1; j < c.groups.size(); j++)
                    for (String a : c.groups.get(i)) for (String b : c.groups.get(j))
                        asyncPairs.add(new String[]{a, b});
        }

        for (TimingPath p : paths.values()) {
            PathAnalysis pa = new PathAnalysis();
            pa.pathId = p.id;
            pa.launchClock = p.launchClock;
            pa.captureClock = p.captureClock;
            ClockInfo lc = resolved.get(p.launchClock);
            ClockInfo cc = resolved.get(p.captureClock);

            // Rule: cross-domain paths without a defined relationship are
            // UNCONSTRAINED; synchrony is never assumed.
            boolean declaredAsync = false;
            if (lc != null && cc != null)
                for (String[] pr : asyncPairs)
                    if ((pr[0].equals(p.launchClock) && pr[1].equals(p.captureClock)) ||
                        (pr[1].equals(p.launchClock) && pr[0].equals(p.captureClock))) declaredAsync = true;
            boolean related = lc != null && cc != null && !declaredAsync
                    && lc.rootMaster != null && lc.rootMaster.equals(cc.rootMaster)
                    && !Double.isNaN(lc.periodPs) && !Double.isNaN(cc.periodPs);
            if (lc == null || cc == null) {
                pa.clockRelation = "none";
                pa.diagnostics.add("clock not found: " + (lc == null ? p.launchClock : p.captureClock));
            } else if (declaredAsync) pa.clockRelation = "none";
            else if (p.launchClock.equals(p.captureClock)) pa.clockRelation = "same";
            else if (related) pa.clockRelation = "generated-chain";
            else pa.clockRelation = "none";

            // exception resolution by object-set specificity
            List<ExceptionHit> hits = new ArrayList<>();
            for (Constraint c : cons.values()) {
                if (c.kind == ConstraintKind.CLOCK_GROUPS) continue;
                ExceptionHit h = match(c, p);
                if (h != null) hits.add(h);
            }
            int best = hits.stream().mapToInt(h -> h.specificity).max().orElse(-1);
            List<ExceptionHit> winners = new ArrayList<>();
            for (ExceptionHit h : hits) if (h.specificity == best) winners.add(h);
            boolean conflict = winners.size() > 1
                    && winners.stream().map(w -> w.kind).distinct().count() > 1
                    || winners.stream().filter(w -> w.kind == ConstraintKind.MULTICYCLE)
                        .map(w -> String.valueOf(w.setupMult) + "/" + String.valueOf(w.holdMult)).distinct().count() > 1;
            for (ExceptionHit h : hits) {
                if (h.specificity < best) h.outcome = "rejected:less-specific-object-set(spec=" + h.specificity + " < " + best + ")";
                else if (conflict) h.outcome = "candidate";
                else h.outcome = "applied";
            }
            pa.hits.addAll(hits);

            if (conflict) {
                pa.status = PathStatus.UNDECIDED;
                for (ExceptionHit w : winners) pa.candidates.add(candidateLabel(w));
                pa.diagnostics.add("conflicting exceptions with equal object-set specificity; all legal candidates kept");
                r.paths.add(pa);
                continue;
            }
            ExceptionHit eff = winners.isEmpty() ? null : winners.get(0);
            if (eff != null) pa.effectiveExceptionId = eff.constraintId;

            if (eff != null && eff.kind == ConstraintKind.FALSE_PATH) {
                pa.status = PathStatus.FALSE_PATH;
                r.paths.add(pa);
                continue;
            }
            if (!related) {
                pa.status = PathStatus.UNCONSTRAINED;
                pa.diagnostics.add("no clock relationship defined between " + p.launchClock + " and "
                        + p.captureClock + "; not assumed synchronous");
                r.paths.add(pa);
                continue;
            }

            // constrained: compute edges and slack
            pa.status = PathStatus.CONSTRAINED;
            pa.launchEdgePs = lc.edgesPs.isEmpty() ? 0.0 : lc.edgesPs.get(0);
            double cap = Double.POSITIVE_INFINITY;
            for (Double e : cc.edgesPs) if (e > pa.launchEdgePs) cap = Math.min(cap, e);
            if (Double.isInfinite(cap)) cap = cc.periodPs; // fallback: next period edge
            pa.captureEdgePs = cap;
            int setupMult = (eff != null && eff.setupMult != null) ? eff.setupMult : 1;
            pa.requiredPs = pa.captureEdgePs + (setupMult - 1) * cc.periodPs;
            Double arr = p.arrival != null ? p.arrival.toPs() : null;
            if (arr == null) {
                pa.arrivalPs = null;
                pa.slackPs = null;
                pa.diagnostics.add("arrival not convertible: " + (p.arrival == null ? "missing" : p.arrival.display())
                        + "; slack unknown, value left in place");
            } else {
                pa.arrivalPs = arr;
                pa.slackPs = pa.requiredPs - arr;
            }
            r.paths.add(pa);
        }
        r.fingerprint = fingerprint(inputs);
        return r;
    }

    private static String candidateLabel(ExceptionHit w) {
        if (w.kind == ConstraintKind.FALSE_PATH) return "FALSE_PATH via " + w.constraintId;
        return "MULTICYCLE setup=" + w.setupMult + " hold=" + w.holdMult + "(" + w.holdPairing + ") via " + w.constraintId;
    }

    /** Parameter fingerprint: algo version + sorted direct source ids + canonical params. */
    public static String fingerprint(List<ParsedBundle> inputs) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(ALGO_VERSION.getBytes(StandardCharsets.UTF_8));
            TreeSet<String> srcs = new TreeSet<>();
            for (ParsedBundle b : inputs) {
                for (ClockDef c : b.clocks) srcs.add(c.sourceInputId + ":" + c.id);
                for (GenClock g : b.genClocks) srcs.add(g.sourceInputId + ":" + g.id);
                for (Constraint c : b.constraints) srcs.add(c.sourceInputId + ":" + c.id);
                for (TimingPath p : b.paths) srcs.add(p.sourceInputId + ":" + p.id);
            }
            for (String s : srcs) md.update(s.getBytes(StandardCharsets.UTF_8));
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Engine() {}
}
