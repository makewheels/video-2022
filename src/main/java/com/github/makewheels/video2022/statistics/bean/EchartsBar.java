package com.github.makewheels.video2022.statistics.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EchartsBar {
    @JsonProperty("xAxis")
    private XAxis xAxis;
    @JsonProperty("yAxis")
    private YAxis yAxis;
    private List<Series> series;
}
