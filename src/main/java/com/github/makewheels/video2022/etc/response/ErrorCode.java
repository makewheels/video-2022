package com.github.makewheels.video2022.etc.response;

public enum ErrorCode {
    SUCCESS(0, "success"),
    FAIL(1, "fail"),

    PHONE_VERIFICATION_CODE_WRONG(1000, "验证码错误"),
    TOKEN_WRONG(1001, "登陆token校验未通过"),

    VIDEO_NOT_EXIST(2000, "视频不存在"),
    VIDEO_AND_UPLOADER_NOT_MATCH(2001, "视频与上传者不匹配，请检查权限"),

    FILE_NOT_EXIST(3000, "文件不存在"),
    FILE_NOT_READY(3001, "文件未就绪"),

    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
