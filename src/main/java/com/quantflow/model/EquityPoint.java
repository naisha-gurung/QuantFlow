package com.quantflow.model;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityPoint {
    private LocalDate date;
    private double value;
    private double drawdown;  // % from peak at this point
    private double benchmarkValue; // Buy-and-hold benchmark
}
