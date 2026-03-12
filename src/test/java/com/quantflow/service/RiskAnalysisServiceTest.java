package com.quantflow.service;

import com.quantflow.model.RiskMetrics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for the risk analytics engine.
 * Tests are calibrated against known analytical results.
 */
@DisplayName("RiskAnalysisService")
class RiskAnalysisServiceTest {

    private RiskAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RiskAnalysisService();
    }

    // ─── Statistical Primitives ───────────────────────────────────────────────

    @Test
    @DisplayName("Mean of uniform returns should equal expected value")
    void testMean() {
        List<Double> returns = List.of(0.01, 0.02, 0.03, 0.04, 0.05);
        assertThat(service.mean(returns)).isCloseTo(0.03, within(1e-10));
    }

    @Test
    @DisplayName("StdDev of constant series should be zero")
    void testStdDevConstant() {
        List<Double> returns = Collections.nCopies(100, 0.01);
        assertThat(service.stdDev(returns)).isCloseTo(0.0, within(1e-10));
    }

    @Test
    @DisplayName("StdDev should match manual calculation")
    void testStdDevManual() {
        List<Double> returns = List.of(-0.02, 0.01, 0.03, -0.01, 0.02);
        double mean = service.mean(returns);
        double expected = Math.sqrt(returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average().orElse(0));
        assertThat(service.stdDev(returns)).isCloseTo(expected, within(1e-10));
    }

    // ─── Sharpe Ratio ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sharpe ratio of zero-volatility series is undefined (0)")
    void testSharpeZeroVol() {
        assertThat(service.sharpeRatio(0.001, 0.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Sharpe ratio should be positive for returns exceeding risk-free")
    void testSharpePositive() {
        // Daily mean >> risk-free, low vol → should give positive Sharpe
        double sharpe = service.sharpeRatio(0.001, 0.008);
        assertThat(sharpe).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Higher volatility reduces Sharpe for same return")
    void testSharpeVsVolatility() {
        double sharpe1 = service.sharpeRatio(0.001, 0.008);
        double sharpe2 = service.sharpeRatio(0.001, 0.016);
        assertThat(sharpe1).isGreaterThan(sharpe2);
    }

    // ─── VaR ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VaR99 should be greater than VaR95 (higher loss at higher confidence)")
    void testVarOrdering() {
        double var95 = service.parametricVaR(0.001, 0.015, 0.95);
        double var99 = service.parametricVaR(0.001, 0.015, 0.99);
        assertThat(var99).isGreaterThan(var95);
    }

    @Test
    @DisplayName("CVaR should be worse than VaR (expected shortfall is more extreme)")
    void testCVarWorseThanVaR() {
        List<Double> returns = generateNormalReturns(1000, 0.001, 0.015, 42L);
        double var95  = service.historicalVaR(returns, 0.95);
        double cvar95 = service.historicalCVaR(returns, 0.95);
        assertThat(cvar95).isGreaterThan(var95);
    }

    // ─── Drawdown ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Monotonically increasing returns should have zero drawdown")
    void testMaxDrawdownZero() {
        List<Double> returns = Collections.nCopies(100, 0.005); // always up
        double[] stats = service.maxDrawdownStats(returns);
        assertThat(stats[0]).isCloseTo(0.0, within(1e-10));
    }

    @Test
    @DisplayName("Single large loss should be detected as max drawdown")
    void testMaxDrawdownSingleCrash() {
        List<Double> returns = new ArrayList<>(Collections.nCopies(50, 0.001));
        returns.add(-0.20); // 20% crash
        returns.addAll(Collections.nCopies(50, 0.001));
        double[] stats = service.maxDrawdownStats(returns);
        assertThat(stats[0]).isLessThan(-0.15); // at least 15% drawdown
    }

    // ─── Beta / Alpha ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Portfolio identical to benchmark should have beta of 1.0")
    void testBetaIdentical() {
        List<Double> returns = generateNormalReturns(252, 0.0005, 0.01, 1L);
        assertThat(service.beta(returns, returns)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("Beta of leveraged 2x portfolio should be approximately 2.0")
    void testBeta2x() {
        List<Double> bmk = generateNormalReturns(252, 0.0005, 0.01, 42L);
        List<Double> leveraged = bmk.stream().map(r -> r * 2.0).collect(Collectors.toList());
        assertThat(service.beta(leveraged, bmk)).isCloseTo(2.0, within(0.01));
    }

    // ─── Full Metrics Integration ─────────────────────────────────────────────

    @Test
    @DisplayName("Full metrics computation should produce valid results")
    void testFullMetrics() {
        List<Double> portfolio  = generateNormalReturns(500, 0.0006, 0.012, 10L);
        List<Double> benchmark  = generateNormalReturns(500, 0.0004, 0.010, 99L);

        RiskMetrics metrics = service.compute(portfolio, benchmark);

        assertThat(metrics.getSharpeRatio()).isNotNaN();
        assertThat(metrics.getSortinoRatio()).isNotNaN();
        assertThat(metrics.getMaxDrawdown()).isLessThanOrEqualTo(0.0);
        assertThat(metrics.getVar95()).isGreaterThan(0.0);
        assertThat(metrics.getCvar95()).isGreaterThanOrEqualTo(metrics.getVar95());
        assertThat(metrics.getBeta()).isGreaterThan(0.0);
        assertThat(metrics.getRSquared()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Insufficient data should throw IllegalArgumentException")
    void testInsufficientData() {
        assertThatThrownBy(() -> service.compute(List.of(0.01), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private List<Double> generateNormalReturns(int n, double mean, double std, long seed) {
        Random rng = new Random(seed);
        List<Double> returns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            returns.add(mean + std * rng.nextGaussian());
        }
        return returns;
    }
}
