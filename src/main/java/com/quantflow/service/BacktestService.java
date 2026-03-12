package com.quantflow.service;

import com.quantflow.algorithm.*;
import com.quantflow.dto.BacktestRequest;
import com.quantflow.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Event-driven backtesting engine.
 *
 * Simulates executing a trading strategy on historical data with realistic
 * constraints: commission costs, position sizing, cash management.
 *
 * Design principles:
 * - No look-ahead bias: only data up to signal date is used
 * - Realistic fill prices: uses close price of signal day
 * - Commission model: flat fee + percentage of trade value
 * - Full equity curve and drawdown tracking
 */
@Service
public class BacktestService {

    private static final double COMMISSION_FLAT    = 1.0;    // $1 per trade
    private static final double COMMISSION_PERCENT = 0.001;  // 0.1% of trade value
    private static final double POSITION_SIZE      = 0.95;   // Use 95% of cash for buys

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private RiskAnalysisService riskService;

    public BacktestResult run(BacktestRequest request) {
        TradingStrategy strategy = resolveStrategy(request.getStrategy(), request.getStrategyParams());

        List<MarketData> data = marketDataService.getHistoricalData(
            request.getSymbol(), request.getStartDate(), request.getEndDate());

        if (data.isEmpty()) {
            throw new IllegalArgumentException("No market data found for " + request.getSymbol());
        }

        List<TradeSignal> rawSignals = strategy.generateSignals(data);

        // Simulate execution
        double cash = request.getInitialCapital();
        int shares = 0;
        List<TradeSignal> executedTrades = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>();
        List<Double> dailyReturns = new ArrayList<>();

        double peakEquity = cash;
        double lastEquity = cash;
        Map<String, Integer> signalIndex = new HashMap<>();
        for (TradeSignal sig : rawSignals) {
            signalIndex.put(sig.getDate().toString(), rawSignals.indexOf(sig));
        }

        for (int i = 0; i < data.size(); i++) {
            MarketData bar = data.get(i);
            String dateKey = bar.getDate().toString();

            // Check for signal on this day
            Optional<TradeSignal> signal = rawSignals.stream()
                .filter(s -> s.getDate().equals(bar.getDate()))
                .findFirst();

            if (signal.isPresent()) {
                TradeSignal sig = signal.get();
                if (sig.getSignal() == TradeSignal.Signal.BUY && cash > 100) {
                    double tradeValue = cash * POSITION_SIZE;
                    int qty = (int)(tradeValue / sig.getPrice());
                    double cost = qty * sig.getPrice() + commission(qty * sig.getPrice());
                    if (qty > 0 && cost <= cash) {
                        cash -= cost;
                        shares += qty;
                        sig.setQuantity(qty);
                        executedTrades.add(sig);
                    }
                } else if (sig.getSignal() == TradeSignal.Signal.SELL && shares > 0) {
                    double proceeds = shares * sig.getPrice() - commission(shares * sig.getPrice());
                    double pnl = proceeds - (executedTrades.stream()
                        .filter(t -> t.getSignal() == TradeSignal.Signal.BUY)
                        .mapToDouble(t -> t.getQuantity() * t.getPrice())
                        .sum());
                    cash += proceeds;
                    sig.setQuantity(shares);
                    sig.setPnl(pnl);
                    executedTrades.add(sig);
                    shares = 0;
                }
            }

            // Mark equity to market
            double equity = cash + shares * bar.getClose();
            peakEquity = Math.max(peakEquity, equity);
            double drawdown = (equity - peakEquity) / peakEquity * 100.0;

            // Daily return
            double dailyReturn = lastEquity != 0 ? (equity - lastEquity) / lastEquity : 0.0;
            dailyReturns.add(dailyReturn);
            lastEquity = equity;

            // Build benchmark: buy-and-hold from start
            double benchmarkValue = request.getInitialCapital() / data.get(0).getClose() * bar.getClose();

            equityCurve.add(EquityPoint.builder()
                .date(bar.getDate())
                .value(equity)
                .drawdown(drawdown)
                .benchmarkValue(benchmarkValue)
                .build());
        }

        // Mark final position to market
        double finalPrice = data.get(data.size() - 1).getClose();
        double finalCapital = cash + shares * finalPrice;

        // Compute benchmark returns for beta/alpha
        List<Double> benchmarkReturns = computeBenchmarkReturns(data);

        RiskMetrics metrics = riskService.compute(dailyReturns, benchmarkReturns);
        metrics.setTotalTrades(executedTrades.size());

        // Win/loss statistics
        List<TradeSignal> sells = executedTrades.stream()
            .filter(t -> t.getSignal() == TradeSignal.Signal.SELL)
            .collect(Collectors.toList());
        long wins = sells.stream().filter(t -> t.getPnl() > 0).count();
        metrics.setWinningTrades((int) wins);
        metrics.setLosingTrades(sells.size() - (int) wins);
        metrics.setWinRate(sells.isEmpty() ? 0 : (double) wins / sells.size() * 100.0);

        double grossProfit = sells.stream().filter(t -> t.getPnl() > 0).mapToDouble(TradeSignal::getPnl).sum();
        double grossLoss = Math.abs(sells.stream().filter(t -> t.getPnl() < 0).mapToDouble(TradeSignal::getPnl).sum());
        metrics.setProfitFactor(grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? Double.MAX_VALUE : 0);

        if (!sells.isEmpty()) {
            OptionalDouble avgWin = sells.stream().filter(t -> t.getPnl() > 0).mapToDouble(TradeSignal::getPnl).average();
            OptionalDouble avgLoss = sells.stream().filter(t -> t.getPnl() < 0).mapToDouble(TradeSignal::getPnl).average();
            metrics.setAvgWin(avgWin.orElse(0));
            metrics.setAvgLoss(avgLoss.orElse(0));
            metrics.setExpectancy((metrics.getWinRate()/100.0 * metrics.getAvgWin()) +
                                   ((1 - metrics.getWinRate()/100.0) * Math.abs(metrics.getAvgLoss())));
        }

        return BacktestResult.builder()
            .strategyName(strategy.getName())
            .symbol(request.getSymbol())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .initialCapital(request.getInitialCapital())
            .finalCapital(finalCapital)
            .trades(executedTrades)
            .equityCurve(equityCurve)
            .dailyReturns(dailyReturns)
            .riskMetrics(metrics)
            .build();
    }

    private double commission(double tradeValue) {
        return COMMISSION_FLAT + tradeValue * COMMISSION_PERCENT;
    }

    private List<Double> computeBenchmarkReturns(List<MarketData> data) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            double r = (data.get(i).getClose() - data.get(i-1).getClose()) / data.get(i-1).getClose();
            returns.add(r);
        }
        if (!returns.isEmpty()) returns.add(0, 0.0); // pad first day
        return returns;
    }

    /**
     * Strategy factory — maps string name to algorithm instance.
     */
    public TradingStrategy resolveStrategy(String name, Map<String, Object> params) {
        return switch (name.toUpperCase().replace(" ", "_")) {
            case "MA_CROSSOVER", "MOVING_AVERAGE" -> {
                int fast = getParam(params, "fastPeriod", 20);
                int slow = getParam(params, "slowPeriod", 50);
                yield new MovingAverageCrossover(fast, slow);
            }
            case "RSI" -> {
                int period    = getParam(params, "period", 14);
                double os     = getDoubleParam(params, "oversold", 30.0);
                double ob     = getDoubleParam(params, "overbought", 70.0);
                yield new RSIStrategy(period, os, ob);
            }
            case "BOLLINGER", "BOLLINGER_BANDS" -> {
                int period    = getParam(params, "period", 20);
                double mult   = getDoubleParam(params, "multiplier", 2.0);
                yield new BollingerBandsStrategy(period, mult);
            }
            case "MEAN_REVERSION" -> {
                int lookback  = getParam(params, "lookback", 20);
                double entry  = getDoubleParam(params, "entryZ", 2.0);
                double exit   = getDoubleParam(params, "exitZ", 0.5);
                yield new MeanReversionStrategy(lookback, entry, exit);
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + name);
        };
    }

    private int getParam(Map<String, Object> p, String key, int def) {
        return p != null && p.containsKey(key) ? ((Number) p.get(key)).intValue() : def;
    }

    private double getDoubleParam(Map<String, Object> p, String key, double def) {
        return p != null && p.containsKey(key) ? ((Number) p.get(key)).doubleValue() : def;
    }
}
