package com.github.makewheels.video2022.watch.progress;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class ProgressService {
    @Resource
    private MongoTemplate mongoTemplate;
    @Resource
    private ProgressRepository progressRepository;



}
