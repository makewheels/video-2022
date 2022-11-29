package com.github.makewheels.video2022.etc.response;

public enum ErrorCode {
    SUCCESS(0, "success"),
    FAIL(1, "fail"),

    PHONE_VERIFICATION_CODE_WRONG(1000, "验证码错误"),

    RUBBISH(1415926535, "我是垃圾，请忽略我");

    private final int code;
    private final String value;

    ErrorCode(int code, String value) {
        this.code = code;
        this.value = value;
    }

    public int getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

}
