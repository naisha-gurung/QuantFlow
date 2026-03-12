package com.quantflow.algorithm;

import com.quantflow.model.*;
import java.util.*;

/**
 * Relative Strength Index (RSI) Strategy.
 *
 * RSI measures the velocity and magnitude of price movements.
 * RSI = 100 - (100 / (1 + RS))  where RS = Avg Gain / Avg Loss
 *
 * Signals:
 *   - RSI < oversoldThreshold (default 30): BUY (oversold, potential reversal up)
 *   - RSI > overboughtThreshold (default 70): SELL (overbought, potential reversal down)
 */
public class RSIStrategy implements TradingStrategy {

    private final int period;
    private final double oversold;
    private final double overbought;

    public RSIStrategy(int period, double oversold, double overbought) {
        this.period = period;
        this.oversold = oversold;
        this.overbought = overbought;
    }

    public RSIStrategy() {
        this(14, 30.0, 70.0);
    }

    @Override
    public String getName() {
        return String.format("RSI(%d) [%s/%s]", period, (int)oversold, (int)overbought);
    }

    @Override
    public List<TradeSignal> generateSignals(List<MarketData> data) {
        List<TradeSignal> signals = new ArrayList<>();
        if (data.size() < period + 1) return signals;

        double[] closes = data.stream().mapToDouble(MarketData::getClose).toArray();
        double[] rsi = computeRSI(closes);

        boolean inPosition = false;
        for (int i = period + 1; i < rsi.length; i++) {
            if (rsi[i] == Double.NaN) continue;
            double prevRsi = rsi[i - 1];
            double currRsi = rsi[i];

            if (!inPosition && prevRsi >= oversold && currRsi < oversold) {
                // Entered oversold zone
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(TradeSignal.Signal.BUY)
                    .price(closes[i])
                    .reason(String.format("RSI oversold entry: RSI=%.1f crossed below %.0f", currRsi, oversold))
                    .build());
                inPosition = true;
            } else if (inPosition && prevRsi <= overbought && currRsi > overbought) {
                // Entered overbought zone
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(TradeSignal.Signal.SELL)
                    .price(closes[i])
                    .reason(String.format("RSI overbought exit: RSI=%.1f crossed above %.0f", currRsi, overbought))
                    .build());
                inPosition = false;
            }
        }
        return signals;
    }

    /**
     * Wilder's Smoothed RSI (the standard definition).
     * Uses exponential smoothing: alpha = 1 / period
     */
    private double[] computeRSI(double[] prices) {
        double[] rsi = new double[prices.length];
        Arrays.fill(rsi, Double.NaN);

        // Seed with simple averages for first period
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = prices[i] - prices[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        rsi[period] = 100.0 - (100.0 / (1.0 + (avgLoss == 0 ? Double.MAX_VALUE : avgGain / avgLoss)));

        // Wilder's smoothing for the rest
        for (int i = period + 1; i < prices.length; i++) {
            double change = prices[i] - prices[i - 1];
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            rsi[i] = avgLoss == 0 ? 100.0 : 100.0 - (100.0 / (1.0 + avgGain / avgLoss));
        }
        return rsi;
    }
}
