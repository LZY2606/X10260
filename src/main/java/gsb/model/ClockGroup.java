package gsb.model;

import java.util.List;

/** set_clock_groups -asynchronous membership; clocks not listed form the implicit "others" set. */
public record ClockGroup(List<String> clocks, String sourceId, int lineNo) {}
