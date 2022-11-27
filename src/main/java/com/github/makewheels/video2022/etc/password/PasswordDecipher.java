package com.github.makewheels.video2022.etc.password;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * 密码解密器
 */
@Slf4j
public class PasswordDecipher implements BeanFactoryPostProcessor, Ordered {

    private final ConfigurableEnvironment environment;

    public PasswordDecipher(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) {
        MutablePropertySources propertySources = environment.getPropertySources();

        String springProfileActive = environment.getProperty("spring.profile.active");
        Map<String, String> plainTextMap = PasswordUtil.getPlainTextMap(springProfileActive);

        plainTextMap.forEach((key, value) -> {
            Map<String, Object> map = new HashMap<>();
            map.put(key, value);
            propertySources.addFirst(new MapPropertySource(key, map));
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
