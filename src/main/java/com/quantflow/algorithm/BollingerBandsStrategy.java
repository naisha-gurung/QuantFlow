package com.quantflow.algorithm;

import com.quantflow.model.*;
import java.util.*;

/**
 * Bollinger Bands Strategy.
 *
 * Bollinger Bands = SMA ± (k * rolling standard deviation)
 * Default: 20-period SMA, k=2 standard deviations.
 *
 * Signals:
 *   - Price touches or crosses below lower band: BUY (mean reversion)
 *   - Price touches or crosses above upper band: SELL
 *   - %B indicator and bandwidth used for confirmation
 */
public class BollingerBandsStrategy implements TradingStrategy {

    private final int period;
    private final double multiplier;

    public BollingerBandsStrategy(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    public BollingerBandsStrategy() {
        this(20, 2.0);
    }

    @Override
    public String getName() {
        return String.format("Bollinger Bands (%d, %.1f)", period, multiplier);
    }

    @Override
    public List<TradeSignal> generateSignals(List<MarketData> data) {
        List<TradeSignal> signals = new ArrayList<>();
        if (data.size() < period) return signals;

        double[] closes = data.stream().mapToDouble(MarketData::getClose).toArray();
        boolean inPosition = false;

        for (int i = period; i < closes.length; i++) {
            double sma = sma(closes, i, period);
            double std = rollingStdDev(closes, i, period);
            double upper = sma + multiplier * std;
            double lower = sma - multiplier * std;
            double bandwidth = (upper - lower) / sma;  // Squeeze indicator
            double percentB = std > 0 ? (closes[i] - lower) / (upper - lower) : 0.5;

            if (!inPosition && closes[i] <= lower) {
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(TradeSignal.Signal.BUY)
                    .price(closes[i])
                    .reason(String.format("Price (%.2f) touched lower band (%.2f), %%B=%.2f, BW=%.3f",
                        closes[i], lower, percentB, bandwidth))
                    .build());
                inPosition = true;
            } else if (inPosition && closes[i] >= upper) {
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(TradeSignal.Signal.SELL)
                    .price(closes[i])
                    .reason(String.format("Price (%.2f) touched upper band (%.2f), %%B=%.2f",
                        closes[i], upper, percentB))
                    .build());
                inPosition = false;
            }
        }
        return signals;
    }

    private double sma(double[] prices, int end, int period) {
        double sum = 0;
        for (int i = end - period + 1; i <= end; i++) sum += prices[i];
        return sum / period;
    }

    private double rollingStdDev(double[] prices, int end, int period) {
        double mean = sma(prices, end, period);
        double variance = 0;
        for (int i = end - period + 1; i <= end; i++) {
            variance += Math.pow(prices[i] - mean, 2);
        }
        return Math.sqrt(variance / period);
    }
}
