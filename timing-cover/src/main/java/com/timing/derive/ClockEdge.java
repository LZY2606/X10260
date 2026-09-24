package com.timing.derive;

/** 时钟边沿（画图与相位计算的坐标基准）。 */
public record ClockEdge(String clockId, String role, int edgeIndex, double timeNs,
                        String sourceRule, Double phaseDeg) {
}
