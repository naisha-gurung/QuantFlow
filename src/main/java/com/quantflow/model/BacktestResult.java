package com.quantflow.model;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {
    private String strategyName;
    private String symbol;
    private LocalDate startDate;
    private LocalDate endDate;
    private double initialCapital;
    private double finalCapital;

    private List<TradeSignal> trades;
    private List<EquityPoint> equityCurve;
    private List<Double> dailyReturns;

    private RiskMetrics riskMetrics;

    public double getTotalReturnPercent() {
        return ((finalCapital - initialCapital) / initialCapital) * 100.0;
    }
}
