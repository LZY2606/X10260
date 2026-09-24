package com.timing.model;

import java.util.List;

/**
 * 网表对象。改名保持同一稳定身份（id 不变，oldNames 累积）。
 */
public record NetObject(String id, String currentName, String kind, String clockName,
                        List<String> oldNames, int introducedVersion, int retiredVersion,
                        String rawSegment, String sourceVersionId) {
}
