package com.timing.model;

/**
 * 跨时钟域关系定义。NONE=无关系（跨域路径标 unconstrained，不假定同步）。
 */
public record ClockRelation(String id, String clockA, String clockB, String relation,
                            int introducedVersion, int retiredVersion,
                            String rawSegment, String sourceVersionId) {
    public boolean async() {
        return "ASYNC".equalsIgnoreCase(relation) || "ASYNC_GROUPS".equalsIgnoreCase(relation);
    }
}
