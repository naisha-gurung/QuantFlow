package com.quantflow.algorithm;

import com.quantflow.model.*;
import java.util.*;

/**
 * Statistical Mean Reversion Strategy using Z-score.
 *
 * Assumes prices revert to their rolling mean.
 * Z-score = (price - rolling_mean) / rolling_std
 *
 * If |Z| is large, price has deviated significantly from mean,
 * so we bet on reversion. This is the basis of statistical arbitrage.
 *
 * Entry: Z < -entryThreshold (price far below mean → BUY)
 * Exit:  Z > exitThreshold  (price far above mean → SELL)
 */
public class MeanReversionStrategy implements TradingStrategy {

    private final int lookback;
    private final double entryZ;  // Z-score threshold to enter (e.g. -2.0)
    private final double exitZ;   // Z-score threshold to exit  (e.g. +0.5)

    public MeanReversionStrategy(int lookback, double entryZ, double exitZ) {
        this.lookback = lookback;
        this.entryZ = entryZ;
        this.exitZ = exitZ;
    }

    public MeanReversionStrategy() {
        this(20, 2.0, 0.5);
    }

    @Override
    public String getName() {
        return String.format("Mean Reversion Z-Score (%d, entry=%.1f)", lookback, entryZ);
    }

    @Override
    public List<TradeSignal> generateSignals(List<MarketData> data) {
        List<TradeSignal> signals = new ArrayList<>();
        if (data.size() < lookback) return signals;

        double[] closes = data.stream().mapToDouble(MarketData::getClose).toArray();
        boolean inPosition = false;

        for (int i = lookback; i < closes.length; i++) {
            double mean = mean(closes, i);
            double std  = std(closes, i, mean);
            if (std < 1e-9) continue;

            double z = (closes[i] - mean) / std;

            if (!inPosition && z < -entryZ) {
                // Price significantly below mean → expect reversion upward
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(TradeSignal.Signal.BUY)
                    .price(closes[i])
                    .reason(String.format("Z-score=%.2f below -%.1f threshold (mean=%.2f, std=%.2f)",
                        z, entryZ, mean, std))
                    .build());
                inPosition = true;
            } else if (inPosition && z > exitZ) {
                // Price has reverted to/above mean → take profit
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(TradeSignal.Signal.SELL)
                    .price(closes[i])
                    .reason(String.format("Z-score=%.2f reverted above %.1f (mean reversion complete)",
                        z, exitZ))
                    .build());
                inPosition = false;
            }
        }
        return signals;
    }

    private double mean(double[] prices, int end) {
        double sum = 0;
        for (int i = end - lookback + 1; i <= end; i++) sum += prices[i];
        return sum / lookback;
    }

    private double std(double[] prices, int end, double mean) {
        double variance = 0;
        for (int i = end - lookback + 1; i <= end; i++) {
            variance += Math.pow(prices[i] - mean, 2);
        }
        return Math.sqrt(variance / lookback);
    }
}
