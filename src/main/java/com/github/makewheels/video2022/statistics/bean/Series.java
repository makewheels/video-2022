package com.github.makewheels.video2022.statistics.bean;

import lombok.Data;

import java.util.List;

@Data
public class Series {
    private String type;
    private List<Long> data;
}
