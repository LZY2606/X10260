package com.fpgasta.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.db.Repository;
import com.fpgasta.model.Ids;
import com.fpgasta.model.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain derivation.
 *
 * Produces first-class candidates, rejection reasons and unknown states.
 * Exception precedence is decided from concrete object-set coverage; a later
 * text line never wins just because it appears last. Cross-domain paths with no
 * clock relation are reported as unconstrained rather than assumed synchronous.
 */
final class Derivation {

    private final Repository repository;
    private final Projector.Snapshot snapshot;
    private final long headSeq;

    Derivation(Repository repository, Projector.Snapshot snapshot, long headSeq) {
        this.repository = repository;
        this.snapshot = snapshot;
        this.headSeq = headSeq;
    }

    void run(List<ObjectNode> producedEvents) {
        deriveClocks();
        deriveRelations();
        derivePaths();
        deriveHoldPairing();
    }

    private Map<String, ObjectNode> clockByName = new LinkedHashMap<>();
    private Map<String, ObjectNode> derivedClockNodes = new LinkedHashMap<>();

    private void deriveClocks() {
        clockByName.clear();
        derivedClockNodes.clear();
        for (JsonNode clock : snapshot.clocks) {
            String name = clock.path("record").path("name").asText();
            clockByName.put(name, (ObjectNode) clock);
        }
        for (JsonNode clock : snapshot.clocks) {
            ObjectNode derived = Json.object();
            ObjectNode record = clock.with("record");
            String entityId = clock.path("entityId").asText();
            String name = record.path("name").asText();
            derived.put("name", name);
            derived.put("kind", record.path("kind").asText());
            derived.put("entityId", entityId);
            derived.set("baseWaveform", record.path("waveform").deepCopy());
            derived.set("objects", record.path("objects").deepCopy());
            if ("generated".equals(record.path("kind").asText())) {
                deriveGenerated(derived, record);
            } else {
                derived.put("sourceClock", name);
                derived.put("phaseNs", 0.0d);
            }
            String fingerprint = fingerprintClock(derived);
            derived.put("parameterFingerprint", fingerprint);
            derived.put("id", "dv.clock." + name + "#" + fingerprint);
            derivedClockNodes.put(name, derived);
            save("clock_waveform", entityId, fingerprint,
                    List.of(revisionSource(entityId)), "ok", derived);
        }
    }

    private void deriveGenerated(ObjectNode derived, ObjectNode record) {
        JsonNode generated = record.path("generatedSource");
        String master = generated.path("masterClock").asText();
        derived.put("sourceClock", master);
        ObjectNode masterNode = clockByName.get(master);
        double phaseNs = 0.0d;
        ArrayNode phaseEdges = Json.NODE_FACTORY.arrayNode();
        if (generated.has("edgeShift")) {
            for (JsonNode shift : generated.withArray("edgeShift")) {
                if (shift.path("convertible").asBoolean(false)) {
                    phaseNs = shift.path("ns").asDouble();
                    ObjectNode edge = Json.object();
                    edge.put("source", "edge_shift");
                    edge.put("raw", shift.path("raw").asText());
                    edge.put("ns", phaseNs);
                    phaseEdges.add(edge);
                } else {
                    ObjectNode edge = Json.object();
                    edge.put("source", "edge_shift");
                    edge.put("raw", shift.path("raw").asText());
                    edge.putNull("ns");
                    edge.put("unknown", true);
                    edge.put("reason", shift.path("note").asText());
                    phaseEdges.add(edge);
                }
            }
        }
        double divide = generated.path("divideBy").asDouble(Double.NaN);
        double multiply = generated.path("multiplyBy").asDouble(Double.NaN);
        if (!Double.isNaN(divide) && divide > 0) {
            derived.put("divideBy", divide);
        }
        if (!Double.isNaN(multiply) && multiply > 0) {
            derived.put("multiplyBy", multiply);
        }
        derived.put("phaseNs", phaseNs);
        derived.set("phaseCandidates", phaseEdges);
        if (masterNode == null) {
            derived.put("status", "unknown");
            derived.put("reason", "master clock '" + master + "' is not defined; generated phase cannot be resolved");
            ArrayNode wave = Json.NODE_FACTORY.arrayNode();
            derived.set("derivedWaveform", wave);
            return;
        }
        double masterPeriod = masterNode.path("record").path("period").path("ns").asDouble(Double.NaN);
        ArrayNode wave = Json.NODE_FACTORY.arrayNode();
        if (Double.isNaN(masterPeriod) || !record.path("period").path("convertible").asBoolean(false)) {
            derived.put("status", "unknown");
            derived.put("reason", "master or generated period is not numeric");
            derived.set("derivedWaveform", wave);
            return;
        }
        double genPeriod = record.path("period").path("ns").asDouble();
        for (JsonNode edge : record.withArray("waveform")) {
            ObjectNode edgeNode = wave.addObject();
            edgeNode.put("raw", edge.path("raw").asText());
            edgeNode.put("unit", edge.path("unit").asText("ns"));
            if (edge.path("convertible").asBoolean(false)) {
                edgeNode.put("ns", edge.path("ns").asDouble() + phaseNs);
                edgeNode.put("phaseNs", phaseNs);
                edgeNode.put("coordinateBasis", master + " launch edge + generated phase");
            } else {
                edgeNode.putNull("ns");
                edgeNode.put("unknown", true);
                edgeNode.put("reason", edge.path("note").asText());
            }
        }
        derived.put("periodNs", genPeriod);
        derived.set("derivedWaveform", wave);
        derived.put("status", "ok");
    }

    private String fingerprintClock(ObjectNode derived) {
        return Ids.sha8(Json.canonical(derived.deepCopy().remove(List.of("id"))));
    }
}

    private void deriveRelations() {
        for (JsonNode relation : snapshot.relations) {
            ObjectNode record = relation.with("record");
            String entityId = relation.path("entityId").asText();
            ObjectNode derived = Json.object();
            derived.put("entityId", entityId);
            derived.put("relation", record.path("relation").asText());
            ArrayNode pairs = Json.NODE_FACTORY.arrayNode();
            List<List<String>> groups = new ArrayList<>();
            record.withArray("groups").forEach(group -> {
                List<String> clocks = new ArrayList<>();
                group.forEach(c -> clocks.add(c.asText()));
                groups.add(clocks);
            });
            for (int i = 0; i < groups.size(); i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    for (String a : groups.get(i)) {
                        for (String b : groups.get(j)) {
                            ObjectNode pair = pairs.addObject();
                            pair.put("fromClock", a);
                            pair.put("toClock", b);
                            pair.put("timed", false);
                            pair.put("relation", record.path("relation").asText());
                            pair.put("basis", "set_clock_groups " + record.path("relation").asText());
                        }
                    }
                }
            }
            derived.set("pairs", pairs);
            String fingerprint = Ids.sha8(Json.canonical(pairs));
            derived.put("id", "dv.relation." + entityId + "#" + fingerprint);
            save("clock_relation", entityId, fingerprint,
                    List.of(revisionSource(entityId)), "ok", derived);
        }
    }

    private void derivePaths() {
        for (JsonNode path : snapshot.paths) {
            deriveOnePath((ObjectNode) path);
        }
    }

    private void deriveOnePath(ObjectNode path) {
        ObjectNode record = path.with("record");
        String entityId = path.path("entityId").asText();
        ObjectNode summary = Json.object();
        summary.put("pathId", record.path("pathId").asText());
        summary.put("entityId", entityId);
        ObjectNode start = findSegment(record, "start");
        ObjectNode end = findSegment(record, "end");
        ObjectNode clocksSegment = findSegment(record, "clocks");
        ObjectNode arrivalSegment = findSegment(record, "arrival");
        ObjectNode requiredSegment = findSegment(record, "required");
        ObjectNode slackSegment = findSegment(record, "slack");
        summary.put("startObject", start == null ? "" : start.path("object").asText());
        summary.put("endObject", end == null ? "" : end.path("object").asText());
        summary.put("launchClock", clocksSegment == null ? "" : clocksSegment.path("launch").asText());
        summary.put("captureClock", clocksSegment == null ? "" : clocksSegment.path("capture").asText());
        summary.set("arrivalReport", reportedValue(arrivalSegment));
        summary.set("requiredReport", reportedValue(requiredSegment));
        summary.set("slackReport", reportedValue(slackSegment));

        ObjectNode launch = derivedClockNodes.get(summary.path("launchClock").asText());
        ObjectNode capture = derivedClockNodes.get(summary.path("captureClock").asText());
        summary.set("clockSources", clockSources(launch, capture));

        ArrayNode exceptionCandidates = matchExceptions(path, start, end);
        summary.set("exceptionCandidates", exceptionCandidates);
        ObjectNode effective = chooseEffectiveException(exceptionCandidates);
        summary.set("effectiveException", effective);

        ObjectNode timing = Json.object();
        boolean sameClock = summary.path("launchClock").asText().equals(summary.path("captureClock").asText());
        boolean related = sameClock || hasRelation(
                summary.path("launchClock").asText(), summary.path("captureClock").asText());
        if (!sameClock && !related) {
            timing.put("status", "unconstrained");
            timing.put("reason", "no clock relationship defines '" + summary.path("launchClock").asText()
                    + "' and '" + summary.path("captureClock").asText() + "'; synchrony is not assumed");
            timing.putNull("slackNs");
            ObjectNode candidate = timing.putObject("rejectedSynchronousCandidate");
            candidate.put("status", "rejected");
            candidate.put("reason", "would assume same clock period without a relation or identical clock");
        } else if (effective != null && "false".equals(effective.path("exceptionKind").asText())) {
            timing.put("status", "false_path");
            timing.put("reason", "false path exception covers this exact object set");
            timing.putNull("slackNs");
        } else {
            computeSlack(timing, launch, capture, effective, arrivalSegment, requiredSegment);
        }
        summary.set("timing", timing);

        List<String> sources = new ArrayList<>();
        sources.add(revisionSource(entityId));
        addClockSources(sources, launch, capture);
        for (JsonNode candidate : exceptionCandidates) {
            sources.add(candidate.path("revisionId").asText());
        }
        String fingerprint = Ids.sha8(Json.canonical(summary.deepCopy().remove(List.of("id"))));
        summary.put("id", "dv.path." + entityId + "#" + fingerprint);
        summary.put("parameterFingerprint", fingerprint);
        String status = timing.path("status").asText();
        save("path_slack", entityId, fingerprint, sources,
                status.equals("unconstrained") ? "unknown" : "ok", summary);
    }

    private ObjectNode findSegment(ObjectNode record, String kind) {
        for (JsonNode segment : record.withArray("segments")) {
            if (kind.equals(segment.path("kind").asText())) {
                return (ObjectNode) segment;
            }
        }
        return null;
    }

    private ObjectNode reportedValue(ObjectNode segment) {
        if (segment == null) {
            ObjectNode missing = Json.object();
            missing.put("raw", "");
            missing.putNull("ns");
            missing.put("present", false);
            return missing;
        }
        ObjectNode view = Json.object();
        view.put("raw", segment.path("time").path("raw").asText());
        view.put("present", true);
        if (segment.path("time").path("convertible").asBoolean(false)) {
            view.put("ns", segment.path("time").path("ns").asDouble());
            view.put("unit", segment.path("time").path("unit").asText("ns"));
        } else {
            view.putNull("ns");
            view.put("unknown", true);
            view.put("reason", segment.path("time").path("note").asText("unconvertible report literal"));
        }
        return view;
    }

    private ArrayNode clockSources(ObjectNode launch, ObjectNode capture) {
        ArrayNode sources = Json.NODE_FACTORY.arrayNode();
        if (launch != null) {
            sources.add(launch.path("entityId").asText());
        }
        if (capture != null) {
            sources.add(capture.path("entityId").asText());
        }
        return sources;
    }

    private void addClockSources(List<String> sources, ObjectNode launch, ObjectNode capture) {
        if (launch != null) {
            sources.add("rev:" + launch.path("entityId").asText());
        }
        if (capture != null) {
            sources.add("rev:" + capture.path("entityId").asText());
        }
    }

    private String revisionSource(String entityId) {
        return "rev:" + entityId;
    }

    private ArrayNode matchExceptions(ObjectNode path, ObjectNode start, ObjectNode end) {
        ArrayNode candidates = Json.NODE_FACTORY.arrayNode();
        String startName = start == null ? "" : start.path("object").asText();
        String endName = end == null ? "" : end.path("object").asText();
        List<String> throughNames = new ArrayList<>();
        for (JsonNode segment : path.path("record").path("segments")) {
            if ("delta".equals(segment.path("kind").asText())) {
                throughNames.add(segment.path("fromObject").asText());
                throughNames.add(segment.path("toObject").asText());
            }
        }
        for (JsonNode exception : snapshot.exceptions) {
            ObjectNode record = exception.with("record");
            ObjectNode coverage = Json.object();
            ArrayNode matchedFrom = Json.NODE_FACTORY.arrayNode();
            ArrayNode matchedTo = Json.NODE_FACTORY.arrayNode();
            ArrayNode matchedThrough = Json.NODE_FACTORY.arrayNode();
            boolean fromOk = covers(record.withArray("from"), startName, matchedFrom);
            boolean toOk = covers(record.withArray("to"), endName, matchedTo);
            boolean throughOk = true;
            if (record.withArray("through").size() > 0) {
                throughOk = false;
                for (String throughName : throughNames) {
                    ArrayNode one = Json.NODE_FACTORY.arrayNode();
                    if (covers(record.withArray("through"), throughName, one)) {
                        throughOk = true;
                        one.forEach(matchedThrough::add);
                    }
                }
            }
            boolean covers = fromOk && toOk && throughOk;
            coverage.set("matchedFrom", matchedFrom);
            coverage.set("matchedTo", matchedTo);
            coverage.set("matchedThrough", matchedThrough);
            int specificity = matchedFrom.size() + matchedTo.size() + matchedThrough.size();
            if (covers) {
                ObjectNode candidate = candidates.addObject();
                candidate.put("candidateId", Ids.candidateId(
                        path.path("entityId").asText(),
                        exception.path("revisionId").asText(), "coverage"));
                candidate.put("entityId", exception.path("entityId").asText());
                candidate.put("revisionId", exception.path("revisionId").asText());
                candidate.put("version", exception.path("version").asInt());
                candidate.put("revisionSeq", exception.path("revisionEventSeq").asLong());
                candidate.put("exceptionKind",
                        record.path("command").asText().equals("set_false_path") ? "false" : "multicycle");
                candidate.put("checkType", record.path("checkType").asText("setup"));
                candidate.put("cycles", record.path("cycles").asInt(1));
                candidate.put("specificity", specificity);
                candidate.put("rawLine", record.path("rawLine").asText());
                candidate.put("status", "candidate");
                candidate.set("coverage", coverage);
            }
        }
        return candidates;
    }

    private boolean covers(ArrayNode patterns, String actual, ArrayNode matched) {
        if (patterns.size() == 0) {
            return true;
        }
        boolean result = false;
        for (JsonNode patternNode : patterns) {
            String pattern = patternNode.asText();
            if (globMatch(pattern, actual)) {
                matched.add(pattern);
                result = true;
            }
        }
        return result;
    }

    static boolean globMatch(String pattern, String value) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else if ("\\.[](){}+^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }

    private ObjectNode chooseEffectiveException(ArrayNode candidates) {
        if (candidates.size() == 0) {
            return null;
        }
        ObjectNode decided = humanDecisionWinner(candidates);
        if (decided != null) {
            return mark(decided, "accepted_by_decision",
                    "human adjudication selected this concrete object set");
        }
        List<ObjectNode> falseCandidates = new ArrayList<>();
        List<ObjectNode> multiCandidates = new ArrayList<>();
        for (JsonNode candidate : candidates) {
            if ("false".equals(candidate.path("exceptionKind").asText())) {
                falseCandidates.add((ObjectNode) candidate);
            } else {
                multiCandidates.add((ObjectNode) candidate);
            }
        }
        ObjectNode winner = mostSpecific(falseCandidates);
        String reason = "most specific false-path object set covers this path";
        if (winner == null) {
            winner = mostSpecific(multiCandidates);
            reason = "most specific multicycle object set covers this path";
        }
        if (winner == null) {
            return null;
        }
        ObjectNode effective = mark(winner, "object_set_precedence", reason);
        ArrayNode rejected = Json.NODE_FACTORY.arrayNode();
        for (JsonNode candidate : candidates) {
            ObjectNode node = (ObjectNode) candidate;
            if (node.path("revisionId").asText().equals(winner.path("revisionId").asText())) {
                continue;
            }
            ObjectNode reject = rejected.addObject();
            reject.put("revisionId", node.path("revisionId").asText());
            reject.put("rawLine", node.path("rawLine").asText());
            if (node.path("specificity").asInt() < winner.path("specificity").asInt()) {
                reject.put("reason", "object set is less specific; textual order does not decide precedence");
            } else {
                reject.put("reason", "same specificity, but winner is an earlier/identical concrete set");
            }
        }
        effective.set("rejectedCandidates", rejected);
        if (hasSpecificityTie(candidates, winner)) {
            effective.put("status", "conflict");
            effective.put("indeterminate", true);
            effective.put("reason",
                    "equal-specificity overlapping exceptions; all legal candidates are retained for decision");
        }
        return effective;
    }

    private boolean hasSpecificityTie(ArrayNode candidates, ObjectNode winner) {
        int winnerSpec = winner.path("specificity").asInt();
        String winnerSet = coverageKey(winner);
        for (JsonNode candidate : candidates) {
            if (candidate.path("revisionId").asText().equals(winner.path("revisionId").asText())) {
                continue;
            }
            if (candidate.path("specificity").asInt() == winnerSpec
                    && !coverageKey(candidate).equals(winnerSet)) {
                return true;
            }
        }
        return false;
    }

    private String coverageKey(JsonNode candidate) {
        return candidate.path("coverage").toString();
    }

    private ObjectNode mostSpecific(List<ObjectNode> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        ObjectNode best = nodes.get(0);
        for (ObjectNode node : nodes) {
            int spec = node.path("specificity").asInt();
            int bestSpec = best.path("specificity").asInt();
            if (spec > bestSpec
                    || (spec == bestSpec && node.path("revisionSeq").asLong() < best.path("revisionSeq").asLong())) {
                best = node;
            }
        }
        return best;
    }

    private ObjectNode humanDecisionWinner(ArrayNode candidates) {
        Map<String, ObjectNode> byRevision = new LinkedHashMap<>();
        for (JsonNode candidate : candidates) {
            byRevision.put(candidate.path("revisionId").asText(), (ObjectNode) candidate);
        }
        ObjectNode accepted = null;
        for (JsonNode decision : snapshot.decisions) {
            if (decision.path("revoked").asBoolean(false)) {
                continue;
            }
            if (!"exception_choice".equals(decision.path("kind").asText())) {
                continue;
            }
            String revisionId = decision.path("detail").path("revisionId").asText();
            if ("accept".equals(decision.path("verdict").asText()) && byRevision.containsKey(revisionId)) {
                accepted = byRevision.get(revisionId);
                accepted.put("decisionId", decision.path("decisionId").asText());
            }
        }
        return accepted;
    }

    private ObjectNode mark(ObjectNode source, String basis, String reason) {
        ObjectNode effective = Json.object();
        effective.put("revisionId", source.path("revisionId").asText());
        effective.put("entityId", source.path("entityId").asText());
        effective.put("exceptionKind", source.path("exceptionKind").asText());
        effective.put("checkType", source.path("checkType").asText());
        effective.put("cycles", source.path("cycles").asInt(1));
        effective.put("specificity", source.path("specificity").asInt());
        effective.put("rawLine", source.path("rawLine").asText());
        effective.put("basis", basis);
        effective.put("reason", reason);
        if (source.has("decisionId")) {
            effective.put("decisionId", source.path("decisionId").asText());
        }
        return effective;
    }

    private boolean hasRelation(String launch, String capture) {
        for (JsonNode relation : snapshot.relations) {
            for (JsonNode group : relation.path("record").path("groups")) {
                for (JsonNode clock : group) {
                    if (clock.asText().equals(launch) || clock.asText().equals(capture)) {
                        // same group is timed; different groups of an async declaration are not.
                    }
                }
            }
            JsonNode groups = relation.path("record").path("groups");
            int launchGroup = -1;
            int captureGroup = -1;
            for (int i = 0; i < groups.size(); i++) {
                for (JsonNode clock : groups.get(i)) {
                    if (clock.asText().equals(launch)) {
                        launchGroup = i;
                    }
                    if (clock.asText().equals(capture)) {
                        captureGroup = i;
                    }
                }
            }
            if (launchGroup >= 0 && captureGroup >= 0 && launchGroup == captureGroup) {
                return true;
            }
        }
        return false;
    }

    private void computeSlack(ObjectNode timing, ObjectNode launch, ObjectNode capture,
                              ObjectNode effective, ObjectNode arrivalSegment,
                              ObjectNode requiredSegment) {
        JsonNode arrivalTime = arrivalSegment == null ? null : arrivalSegment.path("time");
        JsonNode requiredTime = requiredSegment == null ? null : requiredSegment.path("time");
        boolean arrivalOk = arrivalTime != null && arrivalTime.path("convertible").asBoolean(false);
        boolean requiredOk = requiredTime != null && requiredTime.path("convertible").asBoolean(false);
        if (!arrivalOk || !requiredOk) {
            timing.put("status", "unknown");
            timing.put("reason", "report arrival/required literal is not convertible");
            timing.putNull("slackNs");
            return;
        }
        double launchPhase = launch == null ? 0.0d : launch.path("phaseNs").asDouble(0.0d);
        double capturePhase = capture == null ? 0.0d : capture.path("phaseNs").asDouble(0.0d);
        double setupAdjust = 0.0d;
        if (effective != null && "multicycle".equals(effective.path("exceptionKind").asText())
                && "setup".equals(effective.path("checkType").asText())) {
            setupAdjust = (effective.path("cycles").asInt(1) - 1) * periodNs(capture);
        }
        double arrivalNs = arrivalTime.path("ns").asDouble() + launchPhase;
        double requiredNs = requiredTime.path("ns").asDouble() + capturePhase + setupAdjust;
        double slack = requiredNs - arrivalNs;
        timing.put("status", "timed");
        timing.put("arrivalNs", arrivalNs);
        timing.put("requiredNs", requiredNs);
        timing.put("launchPhaseNs", launchPhase);
        timing.put("capturePhaseNs", capturePhase);
        timing.put("setupAdjustNs", setupAdjust);
        timing.put("slackNs", slack);
        timing.put("unit", "ns");
        timing.put("met", slack >= 0.0d);
        timing.put("formula", "slack = required + capturePhase + setupAdjust - arrival - launchPhase");
    }

    private double periodNs(ObjectNode clock) {
        if (clock == null) {
            return 0.0d;
        }
        if (clock.has("periodNs")) {
            return clock.path("periodNs").asDouble();
        }
        String name = clock.path("name").asText();
        ObjectNode stored = clockByName.get(name);
        if (stored == null) {
            return 0.0d;
        }
        return stored.path("record").path("period").path("ns").asDouble(0.0d);
    }

    private void deriveHoldPairing() {
        Map<String, ObjectNode> setupBySubject = new LinkedHashMap<>();
        for (JsonNode derived : repository.allRows("derived_values")) {
            // current effective multicycle setup paths are inspected via snapshot below
        }
        for (JsonNode path : snapshot.paths) {
            String entityId = path.path("entityId").asText();
            JsonNode current = repository.currentDerived(entityId);
            if (current == null) {
                continue;
            }
            JsonNode effective = current.path("effectiveException");
            if (effective.isMissingNode() || effective.isNull()) {
                continue;
            }
            if (!"multicycle".equals(effective.path("exceptionKind").asText())
                    || !"setup".equals(effective.path("checkType").asText())) {
                continue;
            }
            int setupCycles = effective.path("cycles").asInt(1);
            ObjectNode pairing = Json.object();
            pairing.put("pathEntityId", entityId);
            pairing.put("setupRevisionId", effective.path("revisionId").asText());
            pairing.put("setupCycles", setupCycles);
            boolean hasHold = hasHoldCompanion(effective.path("entityId").asText(), entityId);
            pairing.put("holdCompanionPresent", hasHold);
            ArrayNode legal = Json.NODE_FACTORY.arrayNode();
            ObjectNode defaultHold = legal.addObject();
            defaultHold.put("checkType", "hold");
            defaultHold.put("cycles", 0);
            defaultHold.put("valid", setupCycles == 1);
            defaultHold.put("reason", setupCycles == 1
                    ? "single-cycle setup needs no hold companion"
                    : "setup multicycle of N leaves hold over-constrained unless hold = N-1");
            ObjectNode pairedHold = legal.addObject();
            pairedHold.put("checkType", "hold");
            pairedHold.put("cycles", Math.max(0, setupCycles - 1));
            pairedHold.put("valid", true);
            pairedHold.put("reason", "paired hold multicycle keeps hold check on the launch edge");
            pairing.set("legalCandidates", legal);
            pairing.put("status", hasHold || setupCycles == 1 ? "paired" : "incomplete_pairing");
            String fingerprint = Ids.sha8(Json.canonical(pairing));
            pairing.put("id", "dv.holdpair." + entityId + "#" + fingerprint);
            save("hold_pairing", entityId, fingerprint,
                    List.of("rev:" + entityId, effective.path("revisionId").asText()),
                    hasHold || setupCycles == 1 ? "ok" : "warning", pairing);
        }
    }

    private boolean hasHoldCompanion(String exceptionEntityId, String pathEntityId) {
        for (JsonNode exception : snapshot.exceptions) {
            if (!exception.path("entityId").asText().equals(exceptionEntityId)) {
                continue;
            }
            ObjectNode record = exception.with("record");
            if (record.path("command").asText().equals("set_multicycle_path")
                    && "hold".equals(record.path("checkType").asText())) {
                return true;
            }
        }
        return false;
    }

    private void save(String kind, String subjectId, String fingerprint,
                      List<String> sources, String status, ObjectNode payload) {
        String id = payload.path("id").asText(
                "dv." + kind + "." + subjectId + "#" + fingerprint);
        ArrayNode sourceArray = Json.NODE_FACTORY.arrayNode();
        sources.forEach(sourceArray::add);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("kind", kind);
        row.put("subject_id", subjectId);
        row.put("algorithm_version", Algorithm.VERSION);
        row.put("param_fingerprint", fingerprint);
        row.put("sources", Json.canonical(sourceArray));
        row.put("status", status);
        row.put("produced_seq", headSeq);
        row.put("payload", Json.canonical(payload));
        repository.upsert("derived_values", row);
    }

    /**
     * Counterexample evaluation. The resulting event is machine readable and
     * records both accepted and rejected outcomes, so the review UI can read the
     * exact judgment basis instead of a single boolean.
     */
    ObjectNode evaluateCounterexample(String scenario, ObjectNode input) {
        ObjectNode outcome = Json.object();
        outcome.put("scenario", scenario);
        outcome.put("input", input);
        outcome.put("algorithmVersion", Algorithm.VERSION);
        ArrayNode judgments = Json.NODE_FACTORY.arrayNode();
        switch (scenario) {
            case "derived_clock_phase" -> judgeDerivedPhase(judgments, input);
            case "exception_overlap" -> judgeExceptionOverlap(judgments, input);
            case "object_rename" -> judgeObjectRename(judgments, input);
            case "crossdomain_unconstrained" -> judgeCrossDomain(judgments, input);
            case "multicycle_hold_pairing" -> judgeHoldPairing(judgments, input);
            case "path_endpoint" -> judgePathEndpoint(judgments, input);
            case "constraint_version" -> judgeConstraintVersion(judgments, input);
            default -> throw new IllegalArgumentException("Unknown counterexample scenario " + scenario);
        }
        outcome.set("judgments", judgments);
        outcome.put("accepted", anyAccepted(judgments));
        outcome.put("principle", principleText(scenario));
        return outcome;
    }

    private boolean anyAccepted(ArrayNode judgments) {
        for (JsonNode judgment : judgments) {
            if ("accepted".equals(judgment.path("result").asText())) {
                return true;
            }
        }
        return false;
    }

    static String principleText(String scenario) {
        return switch (scenario) {
            case "exception_overlap" ->
                    "exceptions_cover_concrete_object_sets:not_last_text_wins";
            case "crossdomain_unconstrained" ->
                    "cross_domain_without_relation_is_unconstrained:no_assumed_synchrony";
            case "multicycle_hold_pairing" ->
                    "setup_multicycle_requires_hold_companion_N_minus_1";
            case "derived_clock_phase" ->
                    "generated_phase_comes_from_named_edge_shift_and_master_clock";
            case "object_rename" ->
                    "entity_identity_is_stable_across_renames_aliases_tracked";
            case "path_endpoint" ->
                    "endpoint_change_only_invalidates_dependent_paths";
            case "constraint_version" ->
                    "revision_parent_chain_keeps_historical_effective_set";
            default -> "unknown";
        };
    }
}
