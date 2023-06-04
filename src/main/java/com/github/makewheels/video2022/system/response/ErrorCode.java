package com.github.makewheels.video2022.system.response;

public enum ErrorCode {
    SUCCESS(0, "成功"),
    FAIL(1, "未知错误"),

    USER_PHONE_VERIFICATION_CODE_WRONG(1001, "验证码错误"),
    USER_PHONE_VERIFICATION_CODE_EXPIRED(1002, "验证码已过期"),
    USER_TOKEN_WRONG(1003, "登陆token校验未通过"),
    USER_NOT_EXIST(1004, "用户不存在"),

    VIDEO_NOT_EXIST(2001, "视频不存在"),
    VIDEO_AND_UPLOADER_NOT_MATCH(2002, "视频与上传者不匹配"),

    FILE_NOT_EXIST(3001, "文件不存在"),
    FILE_NOT_READY(3002, "文件未就绪"),
    FILE_AND_USER_NOT_MATCH(3003, "文件和用户不匹配"),
    FILE_GENERATE_UPLOAD_CREDENTIALS_FAIL(3004, "生成对象存储上传凭证失败"),

    COVER_NOT_EXIST(4001, "封面不存在"),

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
