package com.quantflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * QuantFlow - Algorithmic Trading & Risk Analytics Platform
 *
 * A production-grade platform for backtesting algorithmic trading strategies,
 * real-time portfolio risk analysis, Monte Carlo simulations, and market data
 * visualization. Built with Spring Boot 3, WebSocket streaming, and H2 persistence.
 *
 * @author QuantFlow
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class QuantFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantFlowApplication.class, args);
    }
}
