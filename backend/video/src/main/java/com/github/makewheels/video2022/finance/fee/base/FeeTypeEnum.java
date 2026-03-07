package com.github.makewheels.video2022.finance.fee.base;

import lombok.Getter;

/**
 * 费用类型
 */
@Getter
public enum FeeTypeEnum {
    TRANSCODE("TRANSCODE", "视频转码"),
    OSS_ACCESS("OSS_ACCESS", "OSS访问文件"),
    OSS_STORAGE("OSS_STORAGE", "OSS存储空间");

    private final String code;
    private final String name;

    FeeTypeEnum(String value, String name) {
        this.code = value;
        this.name = name;
    }

    public static FeeTypeEnum codeToEnum(String code) {
        for (FeeTypeEnum feeType : FeeTypeEnum.values()) {
            if (feeType.getCode().equals(code)) {
                return feeType;
            }
        }
        return null;
    }

}
