package com.quantflow.service;

import com.quantflow.model.RiskMetrics;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core financial risk analytics engine.
 *
 * Implements industry-standard risk metrics used by quantitative analysts:
 * - Sharpe, Sortino, Calmar ratios
 * - Parametric and Historical Value at Risk (VaR)
 * - Conditional VaR (Expected Shortfall)
 * - Maximum Drawdown
 * - Beta, Alpha, R-squared vs benchmark
 */
@Service
public class RiskAnalysisService {

    private static final double RISK_FREE_RATE = 0.05;     // 5% annual (approx 10-yr Treasury)
    private static final double TRADING_DAYS   = 252.0;
    private static final double DAILY_RISK_FREE = RISK_FREE_RATE / TRADING_DAYS;

    /**
     * Compute full risk metrics from a list of daily portfolio returns.
     * @param dailyReturns list of daily returns as decimals (e.g. 0.012 = 1.2%)
     * @param benchmarkReturns daily returns of benchmark (e.g. S&P500), can be null
     */
    public RiskMetrics compute(List<Double> dailyReturns, List<Double> benchmarkReturns) {
        if (dailyReturns == null || dailyReturns.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 data points for risk analysis");
        }

        double mean    = mean(dailyReturns);
        double stdDev  = stdDev(dailyReturns);
        double annRet  = annualizedReturn(dailyReturns);
        double annVol  = stdDev * Math.sqrt(TRADING_DAYS);

        double sharpe  = sharpeRatio(mean, stdDev);
        double sortino = sortinoRatio(dailyReturns, mean);
        double[] drawdownStats = maxDrawdownStats(dailyReturns);
        double calmar  = drawdownStats[0] != 0 ? annRet / Math.abs(drawdownStats[0]) : 0.0;

        double var95   = parametricVaR(mean, stdDev, 0.95);
        double var99   = parametricVaR(mean, stdDev, 0.99);
        double cvar95  = historicalCVaR(dailyReturns, 0.95);
        double cvar99  = historicalCVaR(dailyReturns, 0.99);

        double totalRet = dailyReturns.stream()
            .reduce(1.0, (acc, r) -> acc * (1 + r));
        totalRet = (totalRet - 1.0) * 100.0;

        RiskMetrics.RiskMetricsBuilder builder = RiskMetrics.builder()
            .totalReturn(totalRet)
            .annualizedReturn(annRet * 100.0)
            .dailyMeanReturn(mean * 100.0)
            .dailyReturnStdDev(stdDev * 100.0)
            .annualizedVolatility(annVol * 100.0)
            .sharpeRatio(sharpe)
            .sortinoRatio(sortino)
            .calmarRatio(calmar)
            .maxDrawdown(drawdownStats[0] * 100.0)
            .maxDrawdownDuration(drawdownStats[1])
            .var95(var95 * 100.0)
            .var99(var99 * 100.0)
            .cvar95(cvar95 * 100.0)
            .cvar99(cvar99 * 100.0);

        // Optional benchmark-relative metrics
        if (benchmarkReturns != null && benchmarkReturns.size() >= dailyReturns.size()) {
            List<Double> bmk = benchmarkReturns.subList(0, dailyReturns.size());
            double beta  = beta(dailyReturns, bmk);
            double bmkAnnRet = annualizedReturn(bmk);
            double alpha = annRet - (RISK_FREE_RATE + beta * (bmkAnnRet - RISK_FREE_RATE));
            double r2    = rSquared(dailyReturns, bmk);
            double te    = trackingError(dailyReturns, bmk);
            double ir    = te != 0 ? (annRet - bmkAnnRet) / te : 0.0;

            builder.beta(beta).alpha(alpha * 100.0)
                   .rSquared(r2).trackingError(te * 100.0)
                   .informationRatio(ir);
        }

        return builder.build();
    }

    // ─── Core Statistics ──────────────────────────────────────────────────────

    public double mean(List<Double> returns) {
        return returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public double stdDev(List<Double> returns) {
        double mean = mean(returns);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average().orElse(0.0);
        return Math.sqrt(variance);
    }

    public double annualizedReturn(List<Double> returns) {
        double compounded = returns.stream()
            .reduce(1.0, (acc, r) -> acc * (1.0 + r));
        double years = returns.size() / TRADING_DAYS;
        return Math.pow(compounded, 1.0 / years) - 1.0;
    }

    // ─── Risk-Adjusted Metrics ─────────────────────────────────────────────────

    public double sharpeRatio(double dailyMean, double dailyStdDev) {
        if (dailyStdDev == 0) return 0.0;
        double excessReturn = (dailyMean - DAILY_RISK_FREE) * TRADING_DAYS;
        double annVol = dailyStdDev * Math.sqrt(TRADING_DAYS);
        return excessReturn / annVol;
    }

    public double sortinoRatio(List<Double> returns, double mean) {
        // Downside deviation: only penalizes negative returns
        double downsideVar = returns.stream()
            .filter(r -> r < DAILY_RISK_FREE)
            .mapToDouble(r -> Math.pow(r - DAILY_RISK_FREE, 2))
            .average().orElse(0.0);
        double downsideDev = Math.sqrt(downsideVar) * Math.sqrt(TRADING_DAYS);
        if (downsideDev == 0) return 0.0;
        double excessReturn = (mean - DAILY_RISK_FREE) * TRADING_DAYS;
        return excessReturn / downsideDev;
    }

    // ─── Drawdown ──────────────────────────────────────────────────────────────

    /**
     * @return [maxDrawdown (negative decimal), maxDrawdownDurationDays]
     */
    public double[] maxDrawdownStats(List<Double> returns) {
        double peak = 1.0, equity = 1.0;
        double maxDD = 0.0;
        int ddStart = 0, maxDDDuration = 0, currentDuration = 0;

        for (int i = 0; i < returns.size(); i++) {
            equity *= (1.0 + returns.get(i));
            if (equity > peak) {
                peak = equity;
                currentDuration = 0;
                ddStart = i;
            } else {
                currentDuration++;
                double drawdown = (equity - peak) / peak;
                if (drawdown < maxDD) {
                    maxDD = drawdown;
                    maxDDDuration = currentDuration;
                }
            }
        }
        return new double[]{maxDD, maxDDDuration};
    }

    // ─── Value at Risk ─────────────────────────────────────────────────────────

    /**
     * Parametric (normal distribution) VaR.
     * VaR = -(mean - z * stdDev)
     */
    public double parametricVaR(double mean, double stdDev, double confidence) {
        double z = confidence == 0.99 ? 2.326 : 1.645;
        return -(mean - z * stdDev);  // positive number = potential loss
    }

    /**
     * Historical VaR via direct percentile.
     */
    public double historicalVaR(List<Double> returns, double confidence) {
        List<Double> sorted = new ArrayList<>(returns);
        Collections.sort(sorted);
        int index = (int) Math.floor((1.0 - confidence) * sorted.size());
        return -sorted.get(index);
    }

    /**
     * Conditional VaR (Expected Shortfall) = average of losses beyond VaR threshold.
     */
    public double historicalCVaR(List<Double> returns, double confidence) {
        List<Double> sorted = new ArrayList<>(returns);
        Collections.sort(sorted);
        int cutoff = (int) Math.floor((1.0 - confidence) * sorted.size());
        return -sorted.subList(0, Math.max(1, cutoff))
                      .stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // ─── Benchmark Metrics ─────────────────────────────────────────────────────

    public double beta(List<Double> portfolioReturns, List<Double> benchmarkReturns) {
        double portMean = mean(portfolioReturns);
        double bmkMean  = mean(benchmarkReturns);
        double cov = 0.0, bmkVar = 0.0;
        int n = Math.min(portfolioReturns.size(), benchmarkReturns.size());
        for (int i = 0; i < n; i++) {
            double pd = portfolioReturns.get(i) - portMean;
            double bd = benchmarkReturns.get(i) - bmkMean;
            cov    += pd * bd;
            bmkVar += bd * bd;
        }
        return bmkVar != 0 ? cov / bmkVar : 1.0;
    }

    public double rSquared(List<Double> portfolioReturns, List<Double> benchmarkReturns) {
        double portMean = mean(portfolioReturns);
        double bmkMean  = mean(benchmarkReturns);
        double cov = 0.0, portVar = 0.0, bmkVar = 0.0;
        int n = Math.min(portfolioReturns.size(), benchmarkReturns.size());
        for (int i = 0; i < n; i++) {
            double pd = portfolioReturns.get(i) - portMean;
            double bd = benchmarkReturns.get(i) - bmkMean;
            cov    += pd * bd;
            portVar += pd * pd;
            bmkVar  += bd * bd;
        }
        double denom = Math.sqrt(portVar * bmkVar);
        double corr = denom != 0 ? cov / denom : 0.0;
        return corr * corr;
    }

    public double trackingError(List<Double> portfolioReturns, List<Double> benchmarkReturns) {
        int n = Math.min(portfolioReturns.size(), benchmarkReturns.size());
        List<Double> excessReturns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            excessReturns.add(portfolioReturns.get(i) - benchmarkReturns.get(i));
        }
        return stdDev(excessReturns) * Math.sqrt(TRADING_DAYS);
    }
}
