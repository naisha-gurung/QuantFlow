package com.quantflow.controller;

import com.quantflow.model.MarketData;
import com.quantflow.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
@CrossOrigin(origins = "*")
@Tag(name = "Market Data", description = "Historical OHLCV market data endpoints")
public class MarketDataController {

    @Autowired
    private MarketDataService marketDataService;

    @GetMapping("/symbols")
    @Operation(summary = "List all available symbols")
    public ResponseEntity<List<String>> getSymbols() {
        return ResponseEntity.ok(marketDataService.getAvailableSymbols());
    }

    @GetMapping("/history/{symbol}")
    @Operation(summary = "Get historical OHLCV data for a symbol")
    public ResponseEntity<List<MarketData>> getHistory(
        @PathVariable String symbol,
        @RequestParam(defaultValue = "2023-01-01") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(marketDataService.getHistoricalData(symbol.toUpperCase(), from, toDate));
    }

    @GetMapping("/latest/{symbol}")
    @Operation(summary = "Get the latest price for a symbol")
    public ResponseEntity<Map<String, Object>> getLatest(@PathVariable String symbol) {
        return marketDataService.getLatest(symbol.toUpperCase())
            .map(md -> ResponseEntity.ok(Map.<String, Object>of(
                "symbol", md.getSymbol(),
                "date", md.getDate(),
                "close", md.getClose(),
                "open", md.getOpen(),
                "high", md.getHigh(),
                "low", md.getLow(),
                "volume", md.getVolume()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
}
