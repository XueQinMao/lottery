package com.my.project.api.pojo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result
 *
 * @author 刘强
 * @version 2025/07/17 16:12
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    private int code;

    public String message;

    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message != null ? message : "系统内部错误", null);
    }
}
