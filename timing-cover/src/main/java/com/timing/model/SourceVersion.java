package com.timing.model;

import java.util.List;

/** 一次不可变的源提交；后续修订以新版本+父版本关系表达。 */
public record SourceVersion(String id, int seq, String kind, String parentVersionId,
                            String content, String commitVirtualTime, List<Diagnostic> diagnostics,
                            String changeSummary) {
}
