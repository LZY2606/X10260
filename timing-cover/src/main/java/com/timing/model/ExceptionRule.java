package com.timing.model;

import java.util.List;

/**
 * false path / multicycle 例外约束。按“具体对象集合”保存，
 * 命中与优先级在派生层计算（不按文本最后一条取胜）。
 * multicycle hold 与 setup 分别记录，派生成对状态。
 */
public record ExceptionRule(String id, String kind, String checkType, int multiplier,
                            List<String> objects, List<String> fromObjects,
                            List<String> throughObjects, List<String> toObjects,
                            String fromClock, String toClock,
                            int introducedVersion, int retiredVersion,
                            int orderIndex, String rawSegment, String sourceVersionId) {
    public boolean isFalsePath() {
        return "FALSE_PATH".equalsIgnoreCase(kind);
    }
    public boolean isMulticycle() {
        return "MULTICYCLE".equalsIgnoreCase(kind);
    }
}
