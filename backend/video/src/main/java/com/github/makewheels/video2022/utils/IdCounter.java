package com.github.makewheels.video2022.utils;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("id_counters")
public class IdCounter {
    @Id
    private String key;
    private long counter;
}
