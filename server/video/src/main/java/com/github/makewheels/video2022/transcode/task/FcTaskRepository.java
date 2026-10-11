package com.github.makewheels.video2022.transcode.task;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

@Service
public class FcTaskRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public FcTask getById(String id) {
        return mongoTemplate.findById(id, FcTask.class);
    }

    public void save(FcTask task) {
        mongoTemplate.save(task);
    }

    /**
     * 原子认领：只有当任务仍处于 expectStatus 且 attemptId 匹配时才认领成功。
     * 用于超时恢复与回调的互斥，晚到的旧 attempt 拿不到登记权。
     */
    public boolean claim(String taskId, String expectStatus, String expectAttemptId, ClaimUpdate update) {
        Query query = Query.query(Criteria.where("id").is(taskId)
                .and("status").is(expectStatus)
                .and("attemptId").is(expectAttemptId));
        Update dbUpdate = new Update()
                .set("attemptId", update.newAttemptId)
                .set("deadline", update.newDeadline)
                .inc("attemptCount", 1);
        if (update.newStatus != null) dbUpdate.set("status", update.newStatus);
        if (update.errorMessage != null) dbUpdate.set("errorMessage", update.errorMessage);
        return mongoTemplate.updateFirst(query, dbUpdate, FcTask.class).getModifiedCount() == 1;
    }

    /**
     * 认领时的字段更新
     */
    public static class ClaimUpdate {
        public final String newAttemptId;
        public final String newStatus;
        public final Date newDeadline;
        public final String errorMessage;

        public ClaimUpdate(String newAttemptId, String newStatus, Date newDeadline, String errorMessage) {
            this.newAttemptId = newAttemptId;
            this.newStatus = newStatus;
            this.newDeadline = newDeadline;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 原子置终态：attemptId 匹配且任务未处于终态时才生效，保证晚到回调不覆盖。
     */
    public boolean finish(String taskId, String attemptId, String status, Date finishTime, String errorMessage) {
        Query query = Query.query(Criteria.where("id").is(taskId)
                .and("attemptId").is(attemptId)
                .and("status").nin(FcTaskStatus.SUCCEEDED, FcTaskStatus.FAILED));
        Update update = new Update().set("status", status).set("finishTime", finishTime);
        if (errorMessage != null) update.set("errorMessage", errorMessage);
        return mongoTemplate.updateFirst(query, update, FcTask.class).getModifiedCount() == 1;
    }

    /**
     * 查询超时未完成的任务
     */
    public java.util.List<FcTask> findTimeoutUnfinished(Date now) {
        Query query = Query.query(Criteria.where("deadline").lt(now)
                .and("status").in(FcTaskStatus.SUBMITTED));
        return mongoTemplate.find(query, FcTask.class);
    }
}
