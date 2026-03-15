package com.github.makewheels.video2022.user.bean;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Data
@Document("verification_codes")
public class VerificationCode {
    @Id
    private String id;
    @Indexed(unique = true)
    private String phone;
    private String code;
    @Indexed(expireAfterSeconds = 600)
    private Date createdAt;
}
