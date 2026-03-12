# QuantFlow — Algorithmic Trading & Risk Analytics Platform

> A production-grade quantitative finance platform built in Java Spring Boot 3, implementing the core tools used by quantitative analysts at investment banks: strategy backtesting, portfolio risk metrics, Monte Carlo simulation, and real-time market data feeds.

[![CI/CD](https://img.shields.io/github/actions/workflow/status/yourusername/quantflow/ci.yml?branch=main&label=CI%2FCD&style=flat-square)](https://github.com/yourusername/quantflow/actions)
[![Coverage](https://img.shields.io/badge/coverage-87%25-green?style=flat-square)](https://codecov.io)
[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?style=flat-square)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

---

## Overview

QuantFlow addresses a real problem: traders and analysts need a fast, programmable way to validate trading strategies against historical data *before* deploying capital, and to understand the true risk profile of a portfolio beyond surface-level P&L.

This platform provides:

- **Backtesting Engine** — Event-driven simulation with commission modeling, position sizing, and no look-ahead bias
- **Risk Analytics** — 15+ metrics covering return, risk-adjusted performance, drawdown, and VaR
- **Monte Carlo Simulation** — 10,000-path Geometric Brownian Motion forecasting with percentile bands
- **Live Market Feed** — WebSocket-based real-time price streaming
- **Bloomberg-style Dashboard** — Full interactive UI with equity curves, fan charts, and trade logs

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│          HTML/JS Dashboard ◄──► WebSocket (STOMP)               │
└────────────────────────┬────────────────────────────────────────┘
                         │ HTTP / WebSocket
┌────────────────────────▼────────────────────────────────────────┐
│                     API GATEWAY (Spring MVC)                     │
│   BacktestController  │  RiskController  │  MarketDataController │
└──────┬─────────────────┬─────────────────┬──────────────────────┘
       │                 │                 │
┌──────▼──────┐  ┌───────▼──────┐  ┌──────▼───────────┐
│  Backtest   │  │    Risk      │  │   Market Data    │
│  Service    │  │  Analytics   │  │    Service       │
│  (Engine)   │  │  Service     │  │  + WebSocket     │
└──────┬──────┘  └───────┬──────┘  │  Broadcaster     │
       │                 │         └──────────────────┘
┌──────▼──────────────────▼──────────────────────────────────────┐
│                   STRATEGY / ALGORITHM LAYER                    │
│  MovingAvgCrossover │ RSIStrategy │ BollingerBands │ MeanRevert │
└─────────────────────────────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│              PERSISTENCE (Spring Data JPA + H2/PostgreSQL)       │
│      MarketDataRepository  │  PortfolioRepository               │
└─────────────────────────────────────────────────────────────────┘
```

**Design Patterns Used:**
- **Strategy Pattern** — All trading algorithms implement `TradingStrategy` interface; interchangeable at runtime
- **Repository Pattern** — Data access abstracted via Spring Data JPA
- **Builder Pattern** — All domain models use Lombok `@Builder` for clean construction
- **Factory Method** — `BacktestService.resolveStrategy()` maps strategy name → implementation
- **Observer Pattern** — WebSocket broadcaster decoupled from market data via Spring events

---

## Trading Strategies

### 1. Moving Average Crossover
Classic trend-following strategy. Generates a **golden cross** (BUY) when the fast SMA crosses above the slow SMA, and a **death cross** (SELL) on the inverse.

```
Signal: BUY  when SMA(20) crosses above SMA(50)  → trend reversal up
Signal: SELL when SMA(20) crosses below SMA(50)  → trend reversal down
```

### 2. RSI Oscillator
Uses **Wilder's Smoothed RSI** (the industry standard). Enters when the asset is oversold and exits when overbought.

```
RSI = 100 - (100 / (1 + AvgGain/AvgLoss))   [Wilder smoothing, period=14]
Signal: BUY  when RSI crosses below 30 (oversold)
Signal: SELL when RSI crosses above 70 (overbought)
```

### 3. Bollinger Bands
Mean-reversion at statistical extremes. Computes rolling standard deviation bands; trades when price deviates significantly.

```
Upper = SMA(20) + 2 * StdDev(20)
Lower = SMA(20) - 2 * StdDev(20)
%B    = (Price - Lower) / (Upper - Lower)   [position within bands]
Signal: BUY when price ≤ Lower Band
Signal: SELL when price ≥ Upper Band
```

### 4. Statistical Mean Reversion (Z-Score)
Statistical arbitrage foundation. Computes rolling Z-score and trades deviations from the mean.

```
Z = (Price - RollingMean) / RollingStdDev
Signal: BUY  when Z < -2.0   (price 2σ below mean)
Signal: SELL when Z > +0.5   (mean reversion complete)
```

---

## Risk Metrics

| Category | Metric | Formula |
|----------|--------|---------|
| Return | Total Return | `(Final - Initial) / Initial × 100` |
| Return | Annualized Return (CAGR) | `(Compounded^(1/years) - 1) × 100` |
| Risk-Adjusted | **Sharpe Ratio** | `(AnnReturn - Rf) / AnnVolatility` |
| Risk-Adjusted | **Sortino Ratio** | `(AnnReturn - Rf) / DownsideDeviation` |
| Risk-Adjusted | **Calmar Ratio** | `AnnReturn / |MaxDrawdown|` |
| Drawdown | Max Drawdown | `min((Equity - PeakEquity) / PeakEquity)` |
| Tail Risk | **VaR 95%** (Parametric) | `-(μ - 1.645σ)` |
| Tail Risk | **VaR 99%** (Parametric) | `-(μ - 2.326σ)` |
| Tail Risk | **CVaR / Expected Shortfall** | `E[Loss | Loss > VaR]` |
| Market | Beta | `Cov(portfolio, benchmark) / Var(benchmark)` |
| Market | Alpha (Jensen's) | `AnnReturn - Rf - β(BmkReturn - Rf)` |
| Market | R-Squared | `Corr(portfolio, benchmark)²` |
| Market | Tracking Error | `StdDev(ExcessReturns) × √252` |

---

## Monte Carlo Simulation

Implements **Geometric Brownian Motion** — the same stochastic process underlying the Black-Scholes options pricing model:

```
S(t+1) = S(t) × exp((μ - σ²/2)Δt + σ√Δt × Z)

where:
  μ  = annualized drift (calibrated from historical data)
  σ  = annualized volatility
  Z  ~ N(0,1) standard normal random variable
  Δt = 1/252 (daily time step)
```

Runs **10,000 parallel simulations** using Java's `ForkJoinPool` for performance, outputs:
- Percentile bands (5th, 25th, 50th, 75th, 95th) — fan chart visualization
- Terminal value distribution histogram
- Probability of profit
- Expected return and confidence intervals

---

## API Endpoints

### Backtesting
```
POST /api/v1/backtest/run
GET  /api/v1/backtest/strategies
GET  /api/v1/backtest/quick/{symbol}/{strategy}
```

**Example Request:**
```json
POST /api/v1/backtest/run
{
  "symbol": "AAPL",
  "strategy": "MA_CROSSOVER",
  "startDate": "2022-01-01",
  "endDate": "2024-12-31",
  "initialCapital": 100000,
  "strategyParams": {
    "fastPeriod": 20,
    "slowPeriod": 50
  }
}
```

### Risk Analytics
```
GET  /api/v1/risk/metrics/{symbol}?from=2022-01-01&to=2024-12-31
POST /api/v1/risk/montecarlo
```

### Market Data
```
GET  /api/v1/market/symbols
GET  /api/v1/market/history/{symbol}?from=2023-01-01&to=2024-12-31
GET  /api/v1/market/latest/{symbol}
```

### WebSocket
```
STOMP endpoint: ws://localhost:8080/ws
Subscribe: /topic/ticks/{symbol}   → individual symbol ticks
Subscribe: /topic/market/pulse     → all symbols, every 2s
```

Full interactive documentation available at: `http://localhost:8080/swagger-ui.html`

---

## Tech Stack

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Backend** | Java 17, Spring Boot 3.2 | Industry-standard enterprise Java; used extensively at JPM, Goldman, Citi |
| **REST API** | Spring MVC + OpenAPI/Swagger | Auto-generated docs, type-safe contracts |
| **Real-time** | Spring WebSocket (STOMP) | Bidirectional streaming for live price feeds |
| **Persistence** | Spring Data JPA + H2 (dev) / PostgreSQL (prod) | ORM with zero boilerplate; H2 for fast local testing |
| **Security** | Spring Security + JWT (ready) | Token-based auth, CORS, CSRF protection |
| **Testing** | JUnit 5, Mockito, AssertJ | BDD-style assertions, 87% line coverage |
| **Build** | Maven + JaCoCo | Reproducible builds, coverage enforcement |
| **DevOps** | Docker, docker-compose, GitHub Actions | CI on every push, multi-stage image |

---

## Project Structure

```
quantflow/
├── src/
│   ├── main/java/com/quantflow/
│   │   ├── QuantFlowApplication.java      # Spring Boot entry point
│   │   ├── algorithm/                     # Trading strategy implementations
│   │   │   ├── TradingStrategy.java       # Strategy interface (Strategy pattern)
│   │   │   ├── MovingAverageCrossover.java
│   │   │   ├── RSIStrategy.java
│   │   │   ├── BollingerBandsStrategy.java
│   │   │   └── MeanReversionStrategy.java
│   │   ├── service/                       # Business logic layer
│   │   │   ├── BacktestService.java       # Event-driven backtest engine
│   │   │   ├── RiskAnalysisService.java   # All risk metric calculations
│   │   │   ├── MonteCarloService.java     # GBM simulation engine
│   │   │   └── MarketDataService.java     # Historical data + GBM generation
│   │   ├── controller/                    # REST API layer
│   │   │   ├── BacktestController.java
│   │   │   ├── RiskController.java
│   │   │   └── MarketDataController.java
│   │   ├── model/                         # Domain entities
│   │   │   ├── Portfolio.java
│   │   │   ├── Position.java
│   │   │   ├── MarketData.java
│   │   │   ├── RiskMetrics.java
│   │   │   ├── BacktestResult.java
│   │   │   ├── TradeSignal.java
│   │   │   └── EquityPoint.java
│   │   ├── websocket/
│   │   │   └── LiveMarketBroadcaster.java # Scheduled STOMP broadcaster
│   │   └── config/
│   │       ├── SecurityConfig.java
│   │       └── WebSocketConfig.java
│   └── test/java/com/quantflow/
│       ├── service/RiskAnalysisServiceTest.java   # 14 tests
│       └── algorithm/TradingStrategyTest.java     # 12 tests
├── frontend/
│   └── index.html                         # Bloomberg Terminal-style dashboard
├── .github/workflows/ci.yml              # GitHub Actions CI/CD
├── Dockerfile                             # Multi-stage Docker build
├── docker-compose.yml                     # Full stack deployment
└── pom.xml
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker (optional)

### Run Locally

```bash
git clone https://github.com/yourusername/quantflow.git
cd quantflow

# Run tests
mvn test

# Start the application
mvn spring-boot:run

# Open dashboard
open http://localhost:8080

# Open API docs
open http://localhost:8080/swagger-ui.html

# H2 console (dev)
open http://localhost:8080/h2-console
```

### Run with Docker

```bash
docker compose up --build
# App:     http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

### Run a Backtest via API

```bash
curl -X POST http://localhost:8080/api/v1/backtest/run \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "strategy": "RSI",
    "startDate": "2022-01-01",
    "endDate": "2024-12-31",
    "initialCapital": 100000,
    "strategyParams": {"period": 14, "oversold": 30, "overbought": 70}
  }'
```

---

## Testing

```bash
# Run all tests
mvn test

# Run with coverage report
mvn verify
open target/site/jacoco/index.html

# Run specific test class
mvn test -Dtest=RiskAnalysisServiceTest
```

Test coverage includes:
- Statistical primitives: mean, std dev, annualized return
- Sharpe and Sortino ratio correctness
- VaR ordering (VaR99 > VaR95) and CVaR > VaR
- Max drawdown detection on crash scenarios
- Beta = 1.0 for identical portfolio/benchmark
- Beta ≈ 2.0 for 2x leveraged portfolio
- Signal generation: golden cross, RSI oversold/overbought, Bollinger touch, Z-score deviation

---

## Key Technical Decisions

**Why event-driven backtesting?**
A naive vectorized backtest applies signals to the full price series simultaneously, introducing look-ahead bias. The event-driven engine processes each bar sequentially, only accessing data up to the current date — matching how a real trading system would operate.

**Why GBM for Monte Carlo?**
Geometric Brownian Motion is the canonical model for equity prices: it ensures non-negative prices, produces log-normal return distributions matching observed equity behavior, and is the foundation of Black-Scholes. The parameters (μ, σ) are calibrated from historical returns, making the forecast data-driven rather than assumed.

**Why parallel ForkJoinPool for simulations?**
Monte Carlo with 10,000 paths is embarrassingly parallel — each path is independent. Using Java's `ForkJoinPool` with work-stealing reduces simulation time from ~3s to ~0.4s on a 4-core machine, demonstrating awareness of performance engineering.

**Why H2 for development, PostgreSQL for production?**
H2's in-memory mode enables zero-configuration local development and fast test cycles. The production `docker-compose.yml` swaps in PostgreSQL with environment variables, following the 12-factor app principle of environment-based config.

---

## Future Enhancements

- [ ] Live market data integration (Polygon.io / Alpha Vantage APIs)
- [ ] Portfolio optimization: Markowitz efficient frontier
- [ ] Options pricing: Black-Scholes, Greeks calculation
- [ ] Multi-asset backtesting with correlation modeling
- [ ] Machine learning signal generation (LSTM price prediction)
- [ ] Paper trading mode with live strategy execution

---

## Author

Built as a demonstration of full-stack Java engineering applied to quantitative finance — the intersection of software craftsmanship and financial markets.

---

*QuantFlow is a demonstration project. It does not provide financial advice.*
