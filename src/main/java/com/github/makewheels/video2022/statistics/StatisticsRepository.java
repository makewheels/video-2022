package com.github.makewheels.video2022.statistics;

import com.alibaba.fastjson.JSONObject;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;
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
     * <p>
     * db.fileAccessLog.aggregate(
     * [
     * {
     * $project:{
     * hour:{$hour:"$createTime"},
     * videoId:1,
     * size:1
     * }
     * },
     * {
     * $match: {
     * $and:[
     * {videoId: "63a69d6f1c50bd587d342ccc"},
     * {hour:{"$in":[0,1,2,3,4,5,6,7,8]}}
     * ]
     * }
     * },
     * {
     * $group: { _id: null, sum: { $sum: "$size"}}
     * }
     * ]);
     */
    public long getTrafficConsumeByTimeRange(String videoId, List<Integer> hours) {
//        Aggregation aggregation = Aggregation.newAggregation(
//                Aggregation.project("hour", "videoId", "size"),
//                Aggregation.match(
//                        Criteria.where("videoId").is(videoId)
//                                .and("hour").in(hours)
//                ),
//                Aggregation.group("id").sum("size").as("sum")
//        );
//        AggregationResults<JSONObject> result = mongoTemplate.aggregate(
//                aggregation, "fileAccessLog", JSONObject.class);
//        return result.getMappedResults().get(0).getLong("sum");

//        Aggregation aggregation = Aggregation.newAggregation(
//                Aggregation.match(Criteria.where("videoId").is(videoId)),
//                Aggregation.match(Criteria.where("videoId").is(videoId)),
//
//                Aggregation.project().andExpression("$hour(createTime)").as("hour"),
//
//                Aggregation.project("size"),
//                Aggregation.group().sum("size").as("totalSize")
//        );
//        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "fileAccessLog", Document.class);
//        Document document = results.getUniqueMappedResult();
//        if (document == null) {
//            return 0L;
//        }
//        return document.getLong("totalSize");


        // 2023年2月11日14:53:51 ChatGPT从js转为的java代码：
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.project("videoId", "size")
                        .andExpression("hour(createTime)").as("hour"),
                Aggregation.match(Criteria.where("videoId").is(videoId)
                        .andOperator(Criteria.where("hour").in(hours))),
                Aggregation.group().sum("size").as("sum")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "fileAccessLog", Document.class);
        Document document = results.getUniqueMappedResult();
        if (document == null) {
            return 0L;
        }
        return document.getLong("totalSize");
    }

    /**
     * GPT生成的代码：
     * 作用：统计每天流量消耗，用于echarts的柱状图显示
     * <p>
     * var startDate = new Date("2022-01-01T00:00:00.000Z");
     * var endDate = new Date("2023-05-07T23:59:59.999Z");
     * <p>
     * var pipeline = [
     * {
     * $match: {
     * createTime: {
     * $gte: startDate,
     * $lte: endDate
     * }
     * }
     * },
     * {
     * $group: {
     * _id: {
     * $dateToString: { format: "%Y-%m-%d", date: "$createTime" }
     * },
     * totalSize: { $sum: "$size" }
     * }
     * },
     * {
     * $sort: { "_id": 1 }
     * }
     * ];
     * <p>
     * db.fileAccessLog.aggregate(pipeline).forEach(function(doc) {
     * print(doc._id + ": " + doc.totalSize + " bytes");
     * });
     */
    public List<Document> aggregateTrafficData(Date startDate, Date endDate) {
        Criteria criteria = Criteria.where("createTime").gte(startDate).lte(endDate);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project()
                        .and(DateOperators.DateToString.dateOf("createTime")
                                .toString("%Y-%m-%d")).as("date")
                        .and("size").as("size"),
                Aggregation.group("date").sum("size").as("totalSize"),
                Aggregation.sort(Sort.Direction.ASC, "date")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "fileAccessLog", Document.class);
        return results.getMappedResults();
    }
}
