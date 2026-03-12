package com.quantflow.websocket;

import com.quantflow.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Simulates a live market data feed by broadcasting price ticks
 * over WebSocket to connected clients.
 *
 * In production: would connect to FIX protocol feed, Bloomberg B-PIPE,
 * or a market data vendor's streaming API.
 */
@Service
public class LiveMarketBroadcaster {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MarketDataService marketDataService;

    private final Map<String, Double> lastPrices = new HashMap<>();
    private final Random rng = new Random();

    /**
     * Broadcast simulated tick data every 2 seconds.
     * Simulates intraday micro-movements with mean reversion.
     */
    @Scheduled(fixedDelay = 2000)
    public void broadcastTicks() {
        List<String> symbols = marketDataService.getAvailableSymbols();
        if (symbols.isEmpty()) return;

        symbols.forEach(symbol -> {
            marketDataService.getLatest(symbol).ifPresent(md -> {
                double base = lastPrices.getOrDefault(symbol, md.getClose());
                // Micro-movement: ±0.15% normally distributed
                double change = base * 0.0015 * rng.nextGaussian();
                double newPrice = Math.round((base + change) * 100.0) / 100.0;
                newPrice = Math.max(newPrice, base * 0.98); // -2% floor per tick
                lastPrices.put(symbol, newPrice);

                Map<String, Object> tick = new HashMap<>();
                tick.put("symbol", symbol);
                tick.put("price", newPrice);
                tick.put("change", newPrice - md.getClose());
                tick.put("changePct", (newPrice - md.getClose()) / md.getClose() * 100.0);
                tick.put("volume", (long)(rng.nextInt(10000) + 1000));
                tick.put("timestamp", Instant.now().toEpochMilli());

                messagingTemplate.convertAndSend("/topic/ticks/" + symbol, tick);
            });
        });

        // Broadcast aggregate market pulse
        Map<String, Object> pulse = new HashMap<>();
        pulse.put("type", "MARKET_PULSE");
        pulse.put("timestamp", Instant.now().toEpochMilli());
        pulse.put("prices", new HashMap<>(lastPrices));
        messagingTemplate.convertAndSend("/topic/market/pulse", pulse);
    }
}
