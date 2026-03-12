package com.quantflow.controller;

import com.quantflow.dto.BacktestRequest;
import com.quantflow.model.BacktestResult;
import com.quantflow.service.BacktestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/backtest")
@CrossOrigin(origins = "*")
@Tag(name = "Backtesting", description = "Algorithmic trading strategy backtesting engine")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    @PostMapping("/run")
    @Operation(summary = "Run a backtest",
               description = "Execute a trading strategy on historical data and return full performance analysis")
    public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        BacktestResult result = backtestService.run(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/strategies")
    @Operation(summary = "List available strategies")
    public ResponseEntity<List<Map<String, Object>>> listStrategies() {
        return ResponseEntity.ok(List.of(
            Map.of("id", "MA_CROSSOVER", "name", "Moving Average Crossover",
                   "description", "Golden/Death cross using fast and slow SMAs",
                   "params", Map.of("fastPeriod", 20, "slowPeriod", 50)),
            Map.of("id", "RSI", "name", "RSI Oscillator",
                   "description", "Buy oversold, sell overbought using Wilder RSI",
                   "params", Map.of("period", 14, "oversold", 30, "overbought", 70)),
            Map.of("id", "BOLLINGER_BANDS", "name", "Bollinger Bands",
                   "description", "Mean reversion at band extremes with %B confirmation",
                   "params", Map.of("period", 20, "multiplier", 2.0)),
            Map.of("id", "MEAN_REVERSION", "name", "Statistical Mean Reversion",
                   "description", "Z-score based statistical arbitrage entry/exit",
                   "params", Map.of("lookback", 20, "entryZ", 2.0, "exitZ", 0.5))
        ));
    }

    @GetMapping("/quick/{symbol}/{strategy}")
    @Operation(summary = "Quick backtest with defaults",
               description = "Run a backtest with default parameters on 2-year window")
    public ResponseEntity<BacktestResult> quickBacktest(
        @PathVariable String symbol,
        @PathVariable String strategy) {
        BacktestRequest request = BacktestRequest.builder()
            .symbol(symbol)
            .strategy(strategy)
            .startDate(LocalDate.now().minusYears(2))
            .endDate(LocalDate.now())
            .initialCapital(100_000)
            .strategyParams(Map.of())
            .build();
        return ResponseEntity.ok(backtestService.run(request));
    }
}
