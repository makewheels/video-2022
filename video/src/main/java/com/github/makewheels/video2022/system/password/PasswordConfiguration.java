package com.github.makewheels.video2022.system.password;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration
public class PasswordConfiguration {
    @Bean
    public static PasswordDecipher enableEncryptionData(ConfigurableEnvironment environment) {
        return new PasswordDecipher(environment);
    }
}
