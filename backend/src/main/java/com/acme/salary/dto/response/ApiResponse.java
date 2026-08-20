package com.acme.salary.dto.response;

/**
 * Uniform envelope for every /api response. Controllers return plain DTOs;
 * ApiResponseWrapper wraps them, and GlobalExceptionHandler emits the same
 * shape for errors. HTTP status codes remain authoritative — the envelope
 * exists so the frontend handles one shape everywhere.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
