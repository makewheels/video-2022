package com.github.makewheels.video2022.system.response;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {
        this.code = ErrorCode.SUCCESS.getCode();
        this.message = ErrorCode.SUCCESS.getMessage();
    }

    public Result(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public Result(ErrorCode errorCode, T data) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.data = data;
    }

    public Result(ErrorCode code, String message) {
        this.code = code.getCode();
        this.message = message;
    }

    public Result(ErrorCode code, String message, T data) {
        this.code = code.getCode();
        this.message = message;
        this.data = data;
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Result<Void> ok() {
        return new Result<>(ErrorCode.SUCCESS);
    }

    public static Result<Void> ok(String message) {
        return new Result<>(ErrorCode.SUCCESS, message);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS, data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ErrorCode.SUCCESS, message, data);
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(ErrorCode.SUCCESS, message, data);
    }

    public static <T> Result<T> error(ErrorCode code) {
        return new Result<T>(code);
    }

    public static <T> Result<T> error(ErrorCode code, T data) {
        return new Result<T>(code, data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<T>(ErrorCode.FAIL, message);
    }

}
