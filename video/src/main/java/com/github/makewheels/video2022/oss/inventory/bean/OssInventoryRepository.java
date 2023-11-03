package com.github.makewheels.video2022.oss.inventory.bean;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;

@Repository
public class OssInventoryRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * inventoryGenerationDate是否存在
     */
    public boolean isInventoryGenerationDateExists(LocalDate inventoryGenerationDate) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("inventoryGenerationDate").is(inventoryGenerationDate)),
                OssInventory.class);
    }

    /**
     * 根据key和日期查item
     */
    public OssInventoryItem getItemByObjectNameAndDate(String objectName, Integer inventoryGenerationDate) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("objectName").is(objectName)
                        .and("inventoryGenerationDate").is(inventoryGenerationDate)),
                OssInventoryItem.class);
    }

}
