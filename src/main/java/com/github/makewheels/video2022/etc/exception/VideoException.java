package com.github.makewheels.video2022.etc.exception;

import com.github.makewheels.video2022.etc.response.ErrorCode;

public class VideoException extends RuntimeException {
    private final ErrorCode errorCode;
    private String message;

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public VideoException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public VideoException(String message) {
        super(message);
        this.message = message;
        this.errorCode = ErrorCode.FAIL;
    }
}
