package com.quantflow.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "portfolios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "initial_capital", nullable = false)
    private double initialCapital;

    @Column(name = "current_value")
    private double currentValue;

    @Column(name = "cash_balance")
    private double cashBalance;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Position> positions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public double getTotalReturn() {
        return ((currentValue - initialCapital) / initialCapital) * 100.0;
    }
}
