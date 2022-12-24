package com.github.makewheels.video2022.statistics;

import com.alibaba.fastjson.JSONObject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

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
     *
     * db.fileAccessLog.aggregate(
     * [
     *     {
     *         $project:{
     *             hour:{$hour:"$createTime"},
     *             videoId:1,
     *             size:1
     *         }
     *     },
     *     {
     *         $match: {
     *             $and:[
     *                 {videoId: "63a69d6f1c50bd587d342ccc"},
     *                 {hour:{"$in":[0,1,2,3,4,5,6,7,8]}}
     *             ]
     *         }
     *     },
     *     {
     *         $group: { _id: null, sum: { $sum: "$size"}}
     *     }
     * ]);
     */
    public long getTrafficConsumeByTimeRange(String videoId, List<Integer> hours) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.project("hour", "videoId", "size"),
                Aggregation.match(
                        Criteria.where("videoId").is(videoId)
                                .and("hour").in(hours)
                ),
                Aggregation.group("id").sum("size").as("sum")
        );
        AggregationResults<JSONObject> result = mongoTemplate.aggregate(
                aggregation, "fileAccessLog", JSONObject.class);
        return result.getMappedResults().get(0).getLong("sum");
    }
}
