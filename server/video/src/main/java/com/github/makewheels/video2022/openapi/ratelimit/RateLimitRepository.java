package com.github.makewheels.video2022.openapi.ratelimit;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * MongoDB Repository：限流状态持久化。
 */
@Repository
public interface RateLimitRepository extends MongoRepository<RateLimitRecord, String> {

    RateLimitRecord findByAppId(String appId);

    void deleteByAppId(String appId);
}
