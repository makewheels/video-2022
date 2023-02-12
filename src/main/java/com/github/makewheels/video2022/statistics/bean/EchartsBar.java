package com.github.makewheels.video2022.statistics.bean;

import lombok.Data;

import java.util.List;

@Data
public class EchartsBar {
    private XAxis xAxis;
    private YAxis yAxis;
    private List<Series> series;
}
