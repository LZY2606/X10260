package com.timing.model;

/** 工具版本与报告坐标基准，随项目保留。 */
public record ToolInfo(String vendor, String tool, String toolVersion,
                       String device, String timeUnit, String reportDate, String rawSegment) {
}
