package com.github.makewheels.video2022.openapi.oauth.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.jwt.JWT;
import com.github.makewheels.video2022.openapi.oauth.entity.Developer;
import com.github.makewheels.video2022.openapi.oauth.repository.DeveloperRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class DeveloperService {
    @Resource
    private DeveloperRepository developerRepository;

    @Value("${developer.jwt-secret}")
    private String jwtSecret;

    /**
     * 注册开发者
     */
    public Developer register(String email, String password, String name, String company) {
        Developer existing = developerRepository.findByEmail(email);
        if (existing != null) {
            throw new RuntimeException("Email already registered");
        }

        Developer developer = new Developer();
        developer.setEmail(email);
        developer.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        developer.setName(name);
        developer.setCompany(company);
        return developerRepository.save(developer);
    }

    /**
     * 开发者登录，返回JWT
     */
    public String login(String email, String password) {
        Developer developer = developerRepository.findByEmail(email);
        if (developer == null) {
            throw new RuntimeException("Invalid email or password");
        }
        if (!"active".equals(developer.getStatus())) {
            throw new RuntimeException("Developer account is suspended");
        }
        if (!BCrypt.checkpw(password, developer.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        return JWT.create()
                .setPayload("developerId", developer.getId())
                .setPayload("email", developer.getEmail())
                .setIssuedAt(new Date())
                .setExpiresAt(new Date(System.currentTimeMillis() + 24 * 3600 * 1000))
                .setKey(jwtSecret.getBytes())
                .sign();
    }

    public Developer getById(String id) {
        return developerRepository.getById(id);
    }

    public Developer findByEmail(String email) {
        return developerRepository.findByEmail(email);
    }

    /**
     * 校验JWT，返回developerId
     */
    public String validateJwt(String token) {
        try {
            JWT jwt = JWT.of(token);
            if (!jwt.setKey(jwtSecret.getBytes()).verify()) {
                return null;
            }
            if (!jwt.validate(0)) {
                return null;
            }
            return (String) jwt.getPayload("developerId");
        } catch (Exception e) {
            return null;
        }
    }
}
