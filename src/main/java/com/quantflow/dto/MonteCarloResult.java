package com.quantflow.dto;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MonteCarloResult {
    private int simulations;
    private int horizonDays;
    private double initialValue;
    private double mu;
    private double sigma;
    private double meanTerminalValue;
    private double medianTerminalValue;
    private double p5;
    private double p25;
    private double p75;
    private double p95;
    private double worstCase;
    private double bestCase;
    private double probabilityOfProfit;
    private double expectedReturn;
    private List<double[]> samplePaths;
    private double[][] percentileBands;
}
