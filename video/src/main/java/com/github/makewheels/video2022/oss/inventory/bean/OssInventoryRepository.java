package com.github.makewheels.video2022.oss.inventory.bean;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class OssInventoryRepository {
    @Resource
    private MongoTemplate mongoTemplate;

    public boolean isInventoryGenerationDate(Integer inventoryGenerationDate) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("inventoryGenerationDate").is(inventoryGenerationDate)),
                OssInventory.class);
    }

    public OssInventory getInventoryByInventoryGenerationDate(Integer inventoryGenerationDate) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("inventoryGenerationDate").is(inventoryGenerationDate)),
                OssInventory.class);
    }

    public OssInventoryItem getItemByObjectNameAndDate(String objectName, Integer inventoryGenerationDate) {
        return mongoTemplate.findOne(
                Query.query(Criteria.where("objectName").is(objectName)
                        .and("inventoryGenerationDate").is(inventoryGenerationDate)),
                OssInventoryItem.class);
    }

}
