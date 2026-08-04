package com.harucut.common.response;

public record FieldErrorResponse(
        String field,
        String message
) {
}
