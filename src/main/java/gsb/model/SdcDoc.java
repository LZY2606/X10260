package gsb.model;

import java.util.List;

/** Parsed result of a clocks or constraints document; unparsed lines become diagnostics. */
public record SdcDoc(
        List<ClockDef> clocks,
        List<ClockGroup> groups,
        List<ExceptionDef> exceptions,
        List<String> diagnostics) {

    public static SdcDoc empty() {
        return new SdcDoc(List.of(), List.of(), List.of(), List.of());
    }
}
