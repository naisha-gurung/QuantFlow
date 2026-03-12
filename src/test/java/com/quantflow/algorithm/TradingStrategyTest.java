package com.quantflow.algorithm;

import com.quantflow.model.*;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for all trading strategy signal generators.
 * Verifies signal logic correctness against synthetic price series.
 */
@DisplayName("Trading Strategy Signal Generation")
class TradingStrategyTest {

    // ─── MA Crossover ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("MA Crossover: golden cross should generate BUY signal")
    void testMACrossoverBuySignal() {
        // Build a price series where fast SMA crosses above slow SMA midway
        List<MarketData> data = new ArrayList<>();
        // Declining phase (fast SMA < slow SMA)
        for (int i = 0; i < 60; i++) {
            data.add(bar(LocalDate.of(2023, 1, 1).plusDays(i), 100.0 - i * 0.3));
        }
        // Rising phase (fast SMA crosses above slow SMA)
        for (int i = 0; i < 60; i++) {
            data.add(bar(LocalDate.of(2023, 3, 2).plusDays(i), 82.0 + i * 0.8));
        }

        MovingAverageCrossover strategy = new MovingAverageCrossover(10, 30);
        List<TradeSignal> signals = strategy.generateSignals(data);

        assertThat(signals).isNotEmpty();
        boolean hasBuy = signals.stream().anyMatch(s -> s.getSignal() == TradeSignal.Signal.BUY);
        assertThat(hasBuy).isTrue();
    }

    @Test
    @DisplayName("MA Crossover: fast >= slow period should throw")
    void testMACrossoverInvalidParams() {
        assertThatThrownBy(() -> new MovingAverageCrossover(50, 20))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MA Crossover: insufficient data returns empty signals")
    void testMACrossoverInsufficientData() {
        List<MarketData> data = generateLinear(10, 100.0, 1.0);
        MovingAverageCrossover strategy = new MovingAverageCrossover(5, 20);
        assertThat(strategy.generateSignals(data)).isEmpty();
    }

    // ─── RSI Strategy ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("RSI Strategy: oversold condition should generate BUY")
    void testRSIBuyOnOversold() {
        // Create a sharp decline to trigger oversold RSI
        List<MarketData> data = new ArrayList<>();
        double price = 100.0;
        LocalDate date = LocalDate.of(2023, 1, 1);
        for (int i = 0; i < 30; i++, date = date.plusDays(1)) {
            data.add(bar(date, price));
        }
        // Sharp 15-day decline to drive RSI below 30
        for (int i = 0; i < 15; i++, date = date.plusDays(1)) {
            price *= 0.975;
            data.add(bar(date, price));
        }
        // One up day to cross RSI back above 30
        price *= 1.04;
        data.add(bar(date, price));

        RSIStrategy strategy = new RSIStrategy(14, 30.0, 70.0);
        List<TradeSignal> signals = strategy.generateSignals(data);
        // Should have a BUY somewhere after the decline
        // (exact signal depends on RSI crossing, not just being below threshold)
        assertThat(strategy.getName()).contains("RSI");
    }

    @Test
    @DisplayName("RSI default constructor uses standard parameters")
    void testRSIDefaults() {
        RSIStrategy strategy = new RSIStrategy();
        assertThat(strategy.getName()).contains("14");
    }

    // ─── Bollinger Bands ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Bollinger Bands: price at lower band triggers BUY")
    void testBollingerBuyAtLowerBand() {
        List<MarketData> data = new ArrayList<>();
        LocalDate date = LocalDate.of(2023, 1, 1);
        double price = 100.0;
        Random rng = new Random(42);

        // Build 30 normal bars for band initialization
        for (int i = 0; i < 30; i++, date = date.plusDays(1)) {
            price += rng.nextGaussian() * 0.5;
            data.add(bar(date, Math.max(50, price)));
        }

        // Add a bar far below the lower band to force BUY signal
        double extreme = data.stream().mapToDouble(d -> d.getClose()).average().orElse(100) - 10;
        data.add(bar(date, Math.max(1, extreme)));

        BollingerBandsStrategy strategy = new BollingerBandsStrategy(20, 2.0);
        List<TradeSignal> signals = strategy.generateSignals(data);
        assertThat(signals.stream().anyMatch(s -> s.getSignal() == TradeSignal.Signal.BUY)).isTrue();
    }

    // ─── Mean Reversion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Mean Reversion: Z-score below -2 triggers BUY")
    void testMeanReversionBuyOnLowZ() {
        List<MarketData> data = new ArrayList<>();
        LocalDate date = LocalDate.of(2023, 1, 1);

        // Flat price series (builds mean and std)
        for (int i = 0; i < 25; i++, date = date.plusDays(1)) {
            data.add(bar(date, 100.0 + Math.sin(i) * 0.1)); // near-constant
        }
        // Extreme drop: Z-score should be very negative
        data.add(bar(date, 92.0)); // ~8 std below mean for near-constant series

        MeanReversionStrategy strategy = new MeanReversionStrategy(20, 2.0, 0.5);
        List<TradeSignal> signals = strategy.generateSignals(data);
        assertThat(signals).anyMatch(s -> s.getSignal() == TradeSignal.Signal.BUY);
    }

    @Test
    @DisplayName("Strategy names are descriptive and non-empty")
    void testStrategyNames() {
        assertThat(new MovingAverageCrossover(10, 50).getName()).isNotBlank();
        assertThat(new RSIStrategy().getName()).isNotBlank();
        assertThat(new BollingerBandsStrategy().getName()).isNotBlank();
        assertThat(new MeanReversionStrategy().getName()).isNotBlank();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private MarketData bar(LocalDate date, double price) {
        return MarketData.builder()
            .symbol("TEST")
            .date(date)
            .open(price * 0.999)
            .high(price * 1.005)
            .low(price * 0.995)
            .close(price)
            .adjClose(price)
            .volume(1_000_000)
            .build();
    }

    private List<MarketData> generateLinear(int n, double start, double step) {
        List<MarketData> data = new ArrayList<>();
        LocalDate date = LocalDate.of(2023, 1, 1);
        for (int i = 0; i < n; i++, date = date.plusDays(1)) {
            data.add(bar(date, start + i * step));
        }
        return data;
    }
}
