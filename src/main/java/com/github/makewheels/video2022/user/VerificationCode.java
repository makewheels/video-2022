package com.github.makewheels.video2022.user;

import lombok.Data;

@Data
public class VerificationCode {
    private String phone;
    private String code;
}
