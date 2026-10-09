package com.my.project.api.response;

import java.util.function.Supplier;

/**
 * ApiResponse
 *
 * @author 刘强
 * @version 2025/07/17 16:12
 **/
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message != null ? message : "系统内部错误", null);
    }

    /** Preserve the existing business-code/HTTP-status contract for guarded endpoints. */
    public static <T> ApiResponse<T> capture(Supplier<T> action) {
        return capture(action, "");
    }

    public static <T> ApiResponse<T> capture(Supplier<T> action, String errorPrefix) {
        try {
            return success(action.get());
        } catch (Exception failure) {
            return error(errorPrefix.isEmpty() ? failure.getMessage() : errorPrefix + failure.getMessage());
        }
    }
}
