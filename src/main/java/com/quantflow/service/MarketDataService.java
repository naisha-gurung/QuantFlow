package com.quantflow.service;

import com.quantflow.model.MarketData;
import com.quantflow.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Market data service with realistic GBM-based price simulation.
 *
 * In production this would integrate with data vendors (Bloomberg, Refinitiv,
 * Polygon.io, Alpha Vantage). For the demo, we generate statistically realistic
 * OHLCV data calibrated to real-world equity parameters.
 */
@Service
public class MarketDataService {

    @Autowired
    private MarketDataRepository repository;

    // Symbol configs: [annualDrift, annualVol, startPrice]
    private static final Map<String, double[]> SYMBOL_CONFIG = Map.of(
        "AAPL",  new double[]{0.22,  0.28, 150.0},
        "MSFT",  new double[]{0.20,  0.25, 300.0},
        "GOOGL", new double[]{0.18,  0.30, 2800.0},
        "AMZN",  new double[]{0.19,  0.32, 3300.0},
        "TSLA",  new double[]{0.25,  0.65, 220.0},
        "JPM",   new double[]{0.15,  0.22, 140.0},
        "GS",    new double[]{0.14,  0.24, 350.0},
        "SPY",   new double[]{0.12,  0.16, 400.0}
    );

    @PostConstruct
    public void seedData() {
        if (repository.count() == 0) {
            LocalDate start = LocalDate.of(2020, 1, 2);
            LocalDate end   = LocalDate.of(2024, 12, 31);
            Random rng = new Random(42L); // fixed seed for reproducibility

            for (Map.Entry<String, double[]> entry : SYMBOL_CONFIG.entrySet()) {
                List<MarketData> bars = generateOHLCV(entry.getKey(), entry.getValue(), start, end, rng);
                repository.saveAll(bars);
            }
        }
    }

    public List<MarketData> getHistoricalData(String symbol, LocalDate start, LocalDate end) {
        return repository.findBySymbolAndDateBetweenOrderByDateAsc(
            symbol.toUpperCase(), start, end);
    }

    public Optional<MarketData> getLatest(String symbol) {
        return repository.findTopBySymbolOrderByDateDesc(symbol.toUpperCase());
    }

    public List<String> getAvailableSymbols() {
        return repository.findDistinctSymbols();
    }

    /**
     * Generate realistic OHLCV bars using GBM for close prices,
     * with intraday OHLC derived from log-normal intraday range.
     */
    private List<MarketData> generateOHLCV(
        String symbol, double[] config, LocalDate start, LocalDate end, Random rng) {

        double mu    = config[0];
        double sigma = config[1];
        double price = config[2];
        double dt    = 1.0 / 252.0;
        double drift = (mu - 0.5 * sigma * sigma) * dt;
        double diff  = sigma * Math.sqrt(dt);

        List<MarketData> bars = new ArrayList<>();
        LocalDate date = start;

        while (!date.isAfter(end)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY &&
                date.getDayOfWeek() != DayOfWeek.SUNDAY) {

                double prevClose = price;

                // GBM close price
                price *= Math.exp(drift + diff * rng.nextGaussian());

                // Intraday range: high/low derived from daily volatility
                double dailyRange = price * sigma * Math.sqrt(dt) * (1.5 + rng.nextDouble());
                double high = price + dailyRange * (0.3 + 0.4 * rng.nextDouble());
                double low  = price - dailyRange * (0.3 + 0.4 * rng.nextDouble());
                low  = Math.max(low, price * 0.85); // floor
                high = Math.max(high, price);
                low  = Math.min(low, price);

                // Open: gap from previous close (small daily gap)
                double open = prevClose * Math.exp(0.002 * rng.nextGaussian());
                open = Math.max(low, Math.min(high, open));

                // Volume: log-normal centered around avg daily volume
                long baseVolume = (long)(1_000_000 / price);
                long volume = (long)(baseVolume * Math.exp(0.5 * rng.nextGaussian()) * price / 10.0);
                volume = Math.max(100_000, volume);

                bars.add(MarketData.builder()
                    .symbol(symbol)
                    .date(date)
                    .open(round(open))
                    .high(round(high))
                    .low(round(low))
                    .close(round(price))
                    .adjClose(round(price))
                    .volume(volume)
                    .build());
            }
            date = date.plusDays(1);
        }
        return bars;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
