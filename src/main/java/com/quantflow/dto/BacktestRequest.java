package com.quantflow.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BacktestRequest {
    private String symbol;
    private String strategy;
    private LocalDate startDate;
    private LocalDate endDate;
    private double initialCapital;
    private Map<String, Object> strategyParams;
}
