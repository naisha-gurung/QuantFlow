package com.quantflow.model;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignal {
    public enum Signal { BUY, SELL, HOLD }

    private LocalDate date;
    private Signal signal;
    private double price;
    private int quantity;
    private double pnl;           // PnL for this trade (SELL only)
    private double portfolioValue; // Portfolio value after this trade
    private String reason;        // Human-readable reason (e.g. "MA Crossover: 20-SMA crossed above 50-SMA")
}
