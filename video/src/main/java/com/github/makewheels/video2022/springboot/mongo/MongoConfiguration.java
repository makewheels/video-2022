package com.github.makewheels.video2022.springboot.mongo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class MongoConfiguration {
    @Resource
    private MongoDatabaseFactory mongoDbFactory;
    @Resource
    private MongoMappingContext mongoMappingContext;

    /**
     * 转换类配置
     */
    @Bean
    public MappingMongoConverter mappingMongoConverter() {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDbFactory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mongoMappingContext);
        // 不保存 _class
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));

        // 设置自定义转换器
        List<Object> customConversions = new ArrayList<>();
        customConversions.add(new BigDecimalToDecimal128Converter());
        customConversions.add(new Decimal128ToBigDecimalConverter());
        converter.setCustomConversions(new MongoCustomConversions(customConversions));

        return converter;
    }
}
