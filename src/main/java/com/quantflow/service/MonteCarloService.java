package com.quantflow.service;

import com.quantflow.dto.MonteCarloRequest;
import com.quantflow.dto.MonteCarloResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Monte Carlo simulation engine using Geometric Brownian Motion (GBM).
 *
 * GBM models a stock price S as:
 *   dS = μ*S*dt + σ*S*dW
 *
 * Discrete form:
 *   S(t+1) = S(t) * exp((μ - σ²/2)*dt + σ*sqrt(dt)*Z)
 *
 * where Z ~ N(0,1) is a standard normal random variable.
 *
 * This is the same model underlying the Black-Scholes options pricing framework,
 * used extensively by quantitative analysts at investment banks.
 */
@Service
public class MonteCarloService {

    private static final int NUM_SIMULATIONS = 10_000;
    private static final double TRADING_DAYS = 252.0;

    @Autowired
    private RiskAnalysisService riskService;

    /**
     * Run Monte Carlo portfolio forecast.
     * @param request contains historical returns, forecast horizon, initial value
     */
    public MonteCarloResult simulate(MonteCarloRequest request) {
        List<Double> historicalReturns = request.getHistoricalReturns();
        double mu    = riskService.mean(historicalReturns);
        double sigma = riskService.stdDev(historicalReturns);
        int horizon  = request.getHorizonDays();
        double S0    = request.getInitialValue();
        int simCount = request.getSimulations() > 0 ? request.getSimulations() : NUM_SIMULATIONS;

        // Run simulations in parallel for performance
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        List<double[]> paths;
        try {
            paths = pool.submit(() ->
                IntStream.range(0, simCount)
                    .parallel()
                    .mapToObj(i -> simulatePath(S0, mu, sigma, horizon))
                    .collect(Collectors.toList())
            ).get();
        } catch (Exception e) {
            paths = IntStream.range(0, simCount)
                .mapToObj(i -> simulatePath(S0, mu, sigma, horizon))
                .collect(Collectors.toList());
        } finally {
            pool.shutdown();
        }

        // Extract terminal values for distribution analysis
        double[] terminalValues = paths.stream()
            .mapToDouble(p -> p[p.length - 1])
            .sorted()
            .toArray();

        double mean   = Arrays.stream(terminalValues).average().orElse(S0);
        double median = terminalValues[terminalValues.length / 2];
        double p5     = terminalValues[(int)(0.05 * terminalValues.length)];
        double p25    = terminalValues[(int)(0.25 * terminalValues.length)];
        double p75    = terminalValues[(int)(0.75 * terminalValues.length)];
        double p95    = terminalValues[(int)(0.95 * terminalValues.length)];
        double worst  = terminalValues[0];
        double best   = terminalValues[terminalValues.length - 1];

        long profitableSims = Arrays.stream(terminalValues).filter(v -> v > S0).count();
        double probProfit   = (double) profitableSims / simCount * 100.0;

        // Sample paths for visualization (25 representative paths)
        List<double[]> samplePaths = selectRepresentativePaths(paths, terminalValues);

        // Build percentile bands for fan chart
        double[][] percentileBands = buildPercentileBands(paths, horizon);

        return MonteCarloResult.builder()
            .simulations(simCount)
            .horizonDays(horizon)
            .initialValue(S0)
            .mu(mu)
            .sigma(sigma)
            .meanTerminalValue(mean)
            .medianTerminalValue(median)
            .p5(p5).p25(p25).p75(p75).p95(p95)
            .worstCase(worst)
            .bestCase(best)
            .probabilityOfProfit(probProfit)
            .expectedReturn((mean - S0) / S0 * 100.0)
            .samplePaths(samplePaths)
            .percentileBands(percentileBands)
            .build();
    }

    /**
     * Simulate a single GBM price path.
     */
    private double[] simulatePath(double S0, double mu, double sigma, int horizon) {
        Random rng = ThreadLocalRandom.current();
        double[] path = new double[horizon + 1];
        path[0] = S0;
        double dt = 1.0 / TRADING_DAYS;
        double drift  = (mu - 0.5 * sigma * sigma) * dt;
        double diffusion = sigma * Math.sqrt(dt);

        for (int t = 1; t <= horizon; t++) {
            double Z = rng.nextGaussian();
            path[t] = path[t-1] * Math.exp(drift + diffusion * Z);
        }
        return path;
    }

    /**
     * Build daily percentile bands (5th, 25th, 50th, 75th, 95th) across all simulations.
     * Result: [day][percentile_index]
     */
    private double[][] buildPercentileBands(List<double[]> paths, int horizon) {
        double[][] bands = new double[horizon + 1][5];
        int n = paths.size();
        int[] pctIndices = {
            (int)(0.05 * n), (int)(0.25 * n), (int)(0.50 * n),
            (int)(0.75 * n), (int)(0.95 * n)
        };

        for (int day = 0; day <= horizon; day++) {
            final int d = day;
            double[] dayValues = paths.stream()
                .mapToDouble(p -> p[d])
                .sorted()
                .toArray();

            for (int pi = 0; pi < 5; pi++) {
                bands[day][pi] = dayValues[Math.min(pctIndices[pi], n - 1)];
            }
        }
        return bands;
    }

    /**
     * Select 25 representative sample paths (worst, best, near each percentile).
     */
    private List<double[]> selectRepresentativePaths(List<double[]> paths, double[] sortedTerminals) {
        Set<Integer> selectedIndices = new HashSet<>();
        // Take 25 evenly-spaced terminal values for a representative fan
        int step = Math.max(1, sortedTerminals.length / 25);
        for (int i = 0; i < 25 && i * step < sortedTerminals.length; i++) {
            final double target = sortedTerminals[i * step];
            paths.stream()
                .filter(p -> Math.abs(p[p.length - 1] - target) < 1e-6)
                .findFirst()
                .map(paths::indexOf)
                .ifPresent(selectedIndices::add);
        }
        return selectedIndices.stream()
            .map(paths::get)
            .collect(Collectors.toList());
    }

    private java.util.stream.IntStream IntStream(int start, int end) {
        return java.util.stream.IntStream.range(start, end);
    }

    private static class IntStream {
        static java.util.stream.IntStream range(int start, int end) {
            return java.util.stream.IntStream.range(start, end);
        }
    }
}
