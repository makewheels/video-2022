package com.github.makewheels.video2022.statistics;

import com.alibaba.fastjson.JSONObject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;

@Repository
public class StatisticsRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * <a href="https://www.baeldung.com/spring-data-mongodb-projections-aggregations">
     * spring-data-mongodb-projections-aggregations
     * </a>
     * <p>
     * db.fileAccessLog.aggregate(
     * [
     * {
     * $match: {videoId: "6381ca21be2b3c61f70361d2" }
     * },
     * {
     * $group: { _id: null, sum: { $sum: "$size" }}
     * }
     * ]
     * );
     */
    public long getTrafficConsume(String videoId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("videoId").is(videoId)),
                Aggregation.group("id").sum("size").as("sum")
        );
        AggregationResults<JSONObject> result = mongoTemplate.aggregate(
                aggregation, "fileAccessLog", JSONObject.class);
        return result.getMappedResults().get(0).getLong("sum");
    }

    /**
     * 获取时间范围的流量消耗
     */
    public long getTrafficConsumeByTimeRange(String videoId, Date start, Date end) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                        Criteria.where("videoId").is(videoId)
                                .and("createTime").gte(start)
                                .and("createTime").lt(end)
                ),
                Aggregation.group("id").sum("size").as("sum")
        );
        AggregationResults<JSONObject> result = mongoTemplate.aggregate(
                aggregation, "fileAccessLog", JSONObject.class);
        return result.getMappedResults().get(0).getLong("sum");
    }
}
