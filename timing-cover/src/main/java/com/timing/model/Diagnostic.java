package com.timing.model;

/** 导入期诊断：无法转换/格式问题等，定位到行与源版本。 */
public record Diagnostic(String id, String level, String code, String message,
                         Integer line, String sourceVersionId, String context) {
}
