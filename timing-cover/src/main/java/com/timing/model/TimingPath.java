package com.timing.model;

import java.util.List;

/** 一条时序路径：端点、分段、报文原 slack 均保留。 */
public record TimingPath(String id, String name, String startpoint, String endpoint,
                         String launchClock, String captureClock,
                         TimeValue rawSlack, String slackKind,
                         List<PathSegment> segments,
                         int introducedVersion, int retiredVersion,
                         String rawSegment, String sourceVersionId) {
}
