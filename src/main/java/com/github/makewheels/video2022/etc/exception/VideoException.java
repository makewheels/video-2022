package com.github.makewheels.video2022.etc.exception;

import com.github.makewheels.video2022.etc.response.ErrorCode;

public class VideoException extends RuntimeException {
    private ErrorCode errorCode;

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public VideoException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

}
