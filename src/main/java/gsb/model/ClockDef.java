package gsb.model;

import java.util.List;

/**
 * Clock definition. Raw tokens are kept; derived waveforms are computed by the
 * engine so that generated-clock provenance (source clock, transform) survives.
 */
public record ClockDef(
        String name,
        boolean generated,
        Quantity period,
        List<String> waveformRaw,
        String sourceClock,
        Integer divideBy,
        List<Integer> edges,
        List<String> edgeShiftsRaw,
        String targetObject,
        String sourceId,
        int lineNo) {

    public static ClockDef primary(String name, Quantity period, List<String> waveformRaw,
                                   String targetObject, String sourceId, int lineNo) {
        return new ClockDef(name, false, period, waveformRaw, null, null, null, null,
                targetObject, sourceId, lineNo);
    }

    public static ClockDef generated(String name, String sourceClock, Integer divideBy,
                                     List<Integer> edges, List<String> edgeShiftsRaw,
                                     String targetObject, String sourceId, int lineNo) {
        return new ClockDef(name, true, null, null, sourceClock, divideBy, edges, edgeShiftsRaw,
                targetObject, sourceId, lineNo);
    }
}
