package com.quantflow.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "market_data", indexes = {
    @Index(name = "idx_symbol_date", columnList = "symbol, date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "open_price")
    private double open;

    @Column(name = "high_price")
    private double high;

    @Column(name = "low_price")
    private double low;

    @Column(name = "close_price")
    private double close;

    @Column(name = "adj_close")
    private double adjClose;

    private long volume;

    public double getDailyReturn() {
        return open != 0 ? ((close - open) / open) * 100.0 : 0.0;
    }
}
