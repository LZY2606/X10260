package com.timing.derive;

import java.util.List;

/**
 * 一条 path 在某算法版本/参数指纹/源版本/决定集下的派生结果。
 * 每个显示值都带稳定身份（resultId）。候选、拒绝原因、未知状态都是一等数据。
 */
public record PathResult(
        String resultId,
        String pathId,
        String check,              // SETUP / HOLD
        String status,            // CONSTRAINED / FALSE / UNCONSTRAINED / UNKNOWN
        String launchClockId,
        String captureClockId,
        Double requirementNs,     // 可能为 null（unconstrained/unknown）
        Double arrivalNs,
        Double slackNs,
        Double rawReportedSlackNs,
        Double slackDeltaNs,      // 派生 slack 与报文原 slack 之差（诊断口径差异）
        List<Double> slackCandidatesNs, // 无法唯一决定时的全部合法候选（跨域相位）
        List<RuleHit> hitChain,
        List<String> rejectReasons,
        String multicyclePairState, // PAIRED / SETUP_ONLY_DERIVED_HOLD / NONE
        String rationale,
        String algorithmVersion,
        String paramFingerprint,
        String directSources,     // 直接来源 id 列表（逗号分隔）
        int basedOnSourceSeq,
        int basedOnDecisionSeq,
        String computedAtVirtualTime) {
}
