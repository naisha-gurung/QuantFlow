package com.quantflow.controller;

import com.quantflow.dto.MonteCarloRequest;
import com.quantflow.dto.MonteCarloResult;
import com.quantflow.model.MarketData;
import com.quantflow.model.RiskMetrics;
import com.quantflow.service.MarketDataService;
import com.quantflow.service.MonteCarloService;
import com.quantflow.service.RiskAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/risk")
@CrossOrigin(origins = "*")
@Tag(name = "Risk Analytics", description = "Portfolio risk metrics and Monte Carlo simulation")
public class RiskController {

    @Autowired private RiskAnalysisService riskService;
    @Autowired private MonteCarloService monteCarloService;
    @Autowired private MarketDataService marketDataService;

    @GetMapping("/metrics/{symbol}")
    @Operation(summary = "Compute risk metrics for a symbol")
    public ResponseEntity<RiskMetrics> getRiskMetrics(
        @PathVariable String symbol,
        @RequestParam(defaultValue = "2022-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().toString()}") String to) {

        LocalDate toDate = to.equals("#{T(java.time.LocalDate).now().toString()}")
            ? LocalDate.now() : LocalDate.parse(to);

        List<MarketData> data = marketDataService.getHistoricalData(symbol, from, toDate);
        List<Double> returns = toReturns(data);

        // S&P500 (SPY) as benchmark
        List<MarketData> spyData = marketDataService.getHistoricalData("SPY", from, toDate);
        List<Double> benchmarkReturns = toReturns(spyData);

        return ResponseEntity.ok(riskService.compute(returns, benchmarkReturns));
    }

    @PostMapping("/montecarlo")
    @Operation(summary = "Run Monte Carlo simulation",
               description = "10,000 path GBM simulation with percentile bands and fan chart data")
    public ResponseEntity<MonteCarloResult> monteCarlo(@RequestBody MonteCarloRequest request) {
        // If symbol provided, auto-fetch historical returns
        if (request.getSymbol() != null && !request.getSymbol().isBlank()
            && (request.getHistoricalReturns() == null || request.getHistoricalReturns().isEmpty())) {
            List<MarketData> data = marketDataService.getHistoricalData(
                request.getSymbol(),
                LocalDate.now().minusYears(3),
                LocalDate.now());
            request.setHistoricalReturns(toReturns(data));
            if (request.getInitialValue() == 0) request.setInitialValue(100_000);
        }
        return ResponseEntity.ok(monteCarloService.simulate(request));
    }

    private List<Double> toReturns(List<MarketData> data) {
        List<Double> returns = new java.util.ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            returns.add((data.get(i).getClose() - data.get(i-1).getClose()) / data.get(i-1).getClose());
        }
        if (!returns.isEmpty()) returns.add(0, 0.0);
        return returns;
    }
}
