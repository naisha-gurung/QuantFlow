package com.quantflow.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @Column(nullable = false)
    private String symbol;

    private int quantity;

    @Column(name = "avg_cost")
    private double avgCost;

    @Column(name = "current_price")
    private double currentPrice;

    @Column(name = "market_value")
    private double marketValue;

    @Column(name = "unrealized_pnl")
    private double unrealizedPnl;

    @Column(name = "weight")
    private double weight; // % of portfolio

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public double getUnrealizedPnlPercent() {
        return ((currentPrice - avgCost) / avgCost) * 100.0;
    }
}
