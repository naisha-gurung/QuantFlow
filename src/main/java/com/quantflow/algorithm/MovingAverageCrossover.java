package com.quantflow.algorithm;

import com.quantflow.model.*;
import java.util.*;

/**
 * Moving Average Crossover Strategy.
 *
 * Logic: Buy when fast SMA crosses above slow SMA (golden cross).
 *        Sell when fast SMA crosses below slow SMA (death cross).
 *
 * This is one of the most widely used systematic trading strategies,
 * forming the basis of many trend-following hedge fund algorithms.
 */
public class MovingAverageCrossover implements TradingStrategy {

    private final int fastPeriod;
    private final int slowPeriod;

    public MovingAverageCrossover(int fastPeriod, int slowPeriod) {
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public String getName() {
        return String.format("MA Crossover (%d/%d)", fastPeriod, slowPeriod);
    }

    @Override
    public List<TradeSignal> generateSignals(List<MarketData> data) {
        List<TradeSignal> signals = new ArrayList<>();
        if (data.size() < slowPeriod + 1) return signals;

        double[] closes = data.stream().mapToDouble(MarketData::getClose).toArray();

        for (int i = slowPeriod; i < closes.length; i++) {
            double fastSMA = sma(closes, i, fastPeriod);
            double slowSMA = sma(closes, i, slowPeriod);
            double prevFast = sma(closes, i - 1, fastPeriod);
            double prevSlow = sma(closes, i - 1, slowPeriod);

            TradeSignal.Signal sig = TradeSignal.Signal.HOLD;
            String reason = null;

            if (prevFast <= prevSlow && fastSMA > slowSMA) {
                // Golden cross
                sig = TradeSignal.Signal.BUY;
                reason = String.format("Golden Cross: %d-SMA (%.2f) crossed above %d-SMA (%.2f)",
                    fastPeriod, fastSMA, slowPeriod, slowSMA);
            } else if (prevFast >= prevSlow && fastSMA < slowSMA) {
                // Death cross
                sig = TradeSignal.Signal.SELL;
                reason = String.format("Death Cross: %d-SMA (%.2f) crossed below %d-SMA (%.2f)",
                    fastPeriod, fastSMA, slowPeriod, slowSMA);
            }

            if (sig != TradeSignal.Signal.HOLD) {
                signals.add(TradeSignal.builder()
                    .date(data.get(i).getDate())
                    .signal(sig)
                    .price(closes[i])
                    .reason(reason)
                    .build());
            }
        }
        return signals;
    }

    private double sma(double[] prices, int endIndex, int period) {
        double sum = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum += prices[i];
        }
        return sum / period;
    }
}
