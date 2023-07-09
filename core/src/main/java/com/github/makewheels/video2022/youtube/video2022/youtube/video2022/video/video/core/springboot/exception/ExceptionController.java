package com.github.makewheels.video2022.youtube.video2022.youtube.video2022.video.video.core.springboot.exception;

import com.github.makewheels.video2022.system.response.Result;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("exceptionLog")
public class ExceptionController {
    @Resource
    private MongoTemplate mongoTemplate;

    @GetMapping
    @RequestMapping("getById")
    public Result<ExceptionLog> getById(@RequestParam String exceptionLogId) {
        ExceptionLog exceptionLog = mongoTemplate.findById(exceptionLogId, ExceptionLog.class);
        return Result.ok(exceptionLog);
    }
}
