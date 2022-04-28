package com.github.makewheels.video2022.cover;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class CoverCallbackService {
    @Resource
    private MongoTemplate mongoTemplate;
}
