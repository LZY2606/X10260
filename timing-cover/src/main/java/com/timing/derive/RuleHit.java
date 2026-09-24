package com.timing.derive;

/** 约束命中链中的一环：规则、命中方式、对象集合匹配细节、是否生效、为何被覆盖。 */
public record RuleHit(String ruleId, String kind, String check,
                      String matchScope, int specificity,
                      boolean effective, String reason,
                      List<String> matchedObjects) {
}
