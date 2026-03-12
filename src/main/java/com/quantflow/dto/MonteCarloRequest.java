package com.quantflow.dto;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MonteCarloRequest {
    private List<Double> historicalReturns;
    private int horizonDays;
    private double initialValue;
    private int simulations;
    private String symbol; // optional, to fetch returns automatically
}
