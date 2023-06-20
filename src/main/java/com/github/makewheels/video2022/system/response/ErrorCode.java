package com.github.makewheels.video2022.system.response;

public enum ErrorCode {
    SUCCESS(0, "成功"),
    FAIL(1, "未知错误"),

    USER_PHONE_VERIFICATION_CODE_WRONG(11, "验证码错误"),
    USER_PHONE_VERIFICATION_CODE_EXPIRED(12, "验证码已过期"),
    USER_TOKEN_WRONG(13, "登陆token校验未通过"),
    USER_NOT_EXIST(14, "用户不存在"),
    USER_NOT_LOGIN(15, "用户未登录"),

    VIDEO_NOT_EXIST(21, "视频不存在"),
    VIDEO_AND_UPLOADER_NOT_MATCH(22, "视频与上传者不匹配"),
    VIDEO_NOT_READY(23, "视频未就绪"),
    VIDEO_IS_READY(24, "视频已就绪"),

    FILE_NOT_EXIST(31, "文件不存在"),
    FILE_NOT_READY(32, "文件未就绪"),
    FILE_AND_USER_NOT_MATCH(33, "文件和用户不匹配"),
    FILE_GENERATE_UPLOAD_CREDENTIALS_FAIL(34, "生成对象存储上传凭证失败"),

    COVER_NOT_EXIST(41, "封面不存在"),

    PLAYLIST_NOT_EXIST(51, "播放列表不存在"),
    PLAYLIST_DELETED(52, "播放列表已删除"),
    PLAYLIST_AND_USER_NOT_MATCH(53, "播放列表和用户不匹配"),
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
