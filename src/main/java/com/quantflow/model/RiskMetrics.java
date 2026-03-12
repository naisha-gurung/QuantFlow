package com.quantflow.model;

import lombok.*;

/**
 * Comprehensive risk metrics computed for a portfolio or backtest result.
 * Covers return-based, risk-adjusted, and drawdown analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskMetrics {

    // --- Return Metrics ---
    private double totalReturn;           // % total return over period
    private double annualizedReturn;      // Annualized % return (CAGR)
    private double dailyMeanReturn;       // Average daily return
    private double dailyReturnStdDev;     // Daily return standard deviation (volatility)
    private double annualizedVolatility;  // Ann. volatility = stdDev * sqrt(252)

    // --- Risk-Adjusted Metrics ---
    private double sharpeRatio;           // (Return - RiskFree) / Volatility
    private double sortinoRatio;          // (Return - RiskFree) / DownsideDeviation
    private double calmarRatio;           // AnnualizedReturn / MaxDrawdown
    private double informationRatio;      // Active return / Tracking error

    // --- Drawdown Metrics ---
    private double maxDrawdown;           // Maximum peak-to-trough decline %
    private double maxDrawdownDuration;   // Days in worst drawdown

    // --- Value at Risk ---
    private double var95;                 // Value at Risk at 95% confidence (parametric)
    private double var99;                 // Value at Risk at 99% confidence
    private double cvar95;                // Conditional VaR (Expected Shortfall) at 95%
    private double cvar99;                // Conditional VaR at 99%

    // --- Market Exposure ---
    private double beta;                  // Portfolio beta vs benchmark
    private double alpha;                 // Jensen's alpha (annualized)
    private double rSquared;              // R-squared vs benchmark
    private double trackingError;         // Std dev of excess returns

    // --- Trade Metrics (for backtests) ---
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;               // % of profitable trades
    private double profitFactor;          // Gross profit / Gross loss
    private double avgWin;
    private double avgLoss;
    private double expectancy;            // Expected return per trade
    private double maxConsecutiveLosses;

    public double getLossRate() {
        return 100.0 - winRate;
    }
}
