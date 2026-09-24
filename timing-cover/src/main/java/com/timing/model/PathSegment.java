package com.timing.model;

/** path 分段（arrival 明细），保留原始行。 */
public record PathSegment(String id, String pathId, int orderIndex, String kind,
                          String objectName, TimeValue incremental, TimeValue cumulative,
                          String rawLine, String sourceVersionId) {
}
