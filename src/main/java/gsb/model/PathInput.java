package gsb.model;

import java.util.List;
import java.util.Map;

/** One path segment parsed from a post-synthesis timing report. */
public record PathInput(
        String key,
        String fromObj,
        String toObj,
        String launchClock,
        String captureClock,
        Quantity arrival,
        List<String> segments,
        Map<String, String> extra,
        List<String> diagnostics) {}
