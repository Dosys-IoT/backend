package com.dosys.platform.shared.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        String code,
        String message,
        int status,
        OffsetDateTime timestamp,
        List<FieldErrorDetail> fieldErrors
) {
    public record FieldErrorDetail(String field, String message) {
    }
}
