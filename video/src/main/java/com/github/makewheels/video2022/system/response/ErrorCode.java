package com.github.makewheels.video2022.system.response;

public enum ErrorCode {
    SUCCESS(0, "success"),
    FAIL(1, "unknown error"),

    USER_PHONE_VERIFICATION_CODE_WRONG(11, "verification code incorrect"),
    USER_PHONE_VERIFICATION_CODE_EXPIRED(12, "verification code expired"),
    USER_TOKEN_WRONG(13, "token validation failed"),
    USER_NOT_EXIST(14, "user not found"),
    USER_NOT_LOGIN(15, "user not logged in"),

    VIDEO_CREATE_ARG_ILLEGAL(21, "invalid video creation parameters"),
    VIDEO_NOT_EXIST(22, "video not found"),
    VIDEO_AND_UPLOADER_NOT_MATCH(23, "video and uploader do not match"),
    VIDEO_NOT_READY(24, "video not ready"),
    VIDEO_IS_READY(25, "video is already ready"),

    FILE_NOT_EXIST(31, "file not found"),
    FILE_NOT_READY(32, "file not ready"),
    FILE_AND_USER_NOT_MATCH(33, "file and user do not match"),
    FILE_GENERATE_UPLOAD_CREDENTIALS_FAIL(34, "failed to generate upload credentials"),

    COVER_NOT_EXIST(41, "cover not found"),

    PLAYLIST_NOT_EXIST(51, "playlist not found"),
    PLAYLIST_DELETED(52, "playlist has been deleted"),
    PLAYLIST_AND_USER_NOT_MATCH(53, "playlist and user do not match"),
    ;

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
