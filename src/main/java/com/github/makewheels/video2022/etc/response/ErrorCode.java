package com.github.makewheels.video2022.etc.response;

public enum ErrorCode {
    SUCCESS(0, "success"),
    FAIL(1, "fail"),

    WRONG_PARAM(2, "传入参数错误"),
    NOT_SUPPORT(3, "不支持此操作"),

    //用户相关
    NEED_LOGIN(1000, "请先登录"),
    CHECK_TOKEN_ERROR(1001, "检查登录token错误"),
    REGISTER_LOGIN_NAME_ALREADY_EXISTS(1002, "登录名已存在"),
    LOGIN_LOGIN_NAME_PASSWORD_WRONG(1003, "用户名或密码错误"),
    SEARCH_USER_LOGIN_NAME_NOT_EXIST(1004, "搜索登录名不存在"),
    LOGIN_JPUSH_REGISTRATION_ID_IS_EMPTY(1005, "极光推送id为空，请稍后再试"),
    MODIFY_PASSWORD_OLD_PASSWORD_WRONG(1006, "老密码错误"),
    MODIFY_PHONE_PASSWORD_WRONG(1007, "密码错误"),
    MODIFY_PHONE_PHONE_SAME(1008, "新手机号与原手机号相同"),
    MODIFY_PHONE_VERIFICATION_CODE_EXPIRE(1010, "验证码过期"),
    MODIFY_PHONE_VERIFICATION_CODE_WRONG(1011, "验证码错误"),

    //会话相关
    CONVERSATION_CREATE_TARGET_USER_NOT_EXIST(2000, "创建会话错误：目标用户不存在"),
    CONVERSATION_CREATE_WITH_MY_SELF(2001, "和自己聊有意思么？"),
    CONVERSATION_CREATE_REPEAT(2002, "请不要重复创建会话"),

    //消息相关
    MESSAGE_TYPE_NOT_EXIST(3000, "消息类型不存在"),
    MESSAGE_CANT_FIND_CONVERSATION(3001, "会话不存在"),
    MESSAGE_PERSON_CONVERSATION_AMOUNT_NOT_EQUALS_TWO(3002, "给人发的消息，找到会话数量不等于2"),
    MESSAGE_NOT_EXIST(3003, "消息不存在"),
    MESSAGE_REPEAT_ARRIVE_REPEAT(3004, "重复上报已送达"),
    MESSAGE_UPLOAD_NOT_FINISH(3005, "文件上传未完成"),
    SEND_FILE_MESSAGE_WITHOUT_MD5(3006, "上传文件类型消息没有md5"),
    NOT_YOUR_MESSAGE(3007, "不属于你的消息"),

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
