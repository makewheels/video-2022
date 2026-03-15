package com.github.makewheels.video2022.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
@Slf4j
public class IdService {
    @Resource
    private MongoTemplate mongoTemplate;

    private long nextCounter(String counterKey) {
        Query query = new Query(Criteria.where("_id").is(counterKey));
        Update update = new Update().inc("counter", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        IdCounter counter = mongoTemplate.findAndModify(query, update, options, IdCounter.class);
        return counter != null ? counter.getCounter() : 1;
    }

    private synchronized String nextId(
            Duration duration, int serialNumberLength, int randomLength) {
        long currentTime = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        long startTime = LocalDateTime.of(2023, 1, 1, 0, 0, 0)
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        long timeUnit = (currentTime - startTime) / duration.toMillis();

        long increaseId = nextCounter("longId:" + timeUnit);
        String serialNumber = new DecimalFormat(StringUtils.repeat("0", serialNumberLength))
                .format(increaseId);

        String random = RandomUtil.randomNumbers(randomLength);
        log.debug("生成id: " + timeUnit + "-" + serialNumber + "-" + random);

        return timeUnit + serialNumber + random;
    }

    public synchronized String nextLongId() {
        return nextId(Duration.ofSeconds(4), 5, 3);
    }

    public synchronized String nextShortId() {
        long currentSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentSeconds - 1640995200L;
        timestamp = timestamp / 60 / 60 / 24;

        long increaseId = nextCounter("shortId:" + DateUtil.formatDate(new Date()));
        String increaseIdStr = new DecimalFormat("00").format(increaseId);

        String random = RandomUtil.randomNumbers(3);

        long decimal = Long.parseLong(timestamp + increaseIdStr + random);
        String result = Long.toString(decimal, Character.MAX_RADIX).toUpperCase();

        String format = timestamp + "-" + increaseIdStr + "-" + random;
        log.debug("生成id：format = {}, result = {}", format, result);

        return result;
    }

    private synchronized String getCommonId() {
        return nextLongId();
    }

    public synchronized String nextLongId(String prefix) {
        return prefix + getCommonId();
    }

    public synchronized String getUserId() {
        return "u_" + getCommonId();
    }

    public synchronized String getVideoId() {
        return "v_" + getCommonId();
    }

    public synchronized String getTranscodeId() {
        return "tr_" + getCommonId();
    }

    public synchronized String getCoverId() {
        return "c_" + getCommonId();
    }

    public synchronized String getFileId() {
        return "f_" + getCommonId();
    }

    public synchronized String getTsFileId() {
        return "f_ts_" + getCommonId();
    }
}
