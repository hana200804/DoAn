package com.signa.common.error;

import java.util.List;

/** Thân response lỗi chung cho mọi service. */
public record ApiError(String code, String message, List<FieldIssue> details, String requestId) {}
