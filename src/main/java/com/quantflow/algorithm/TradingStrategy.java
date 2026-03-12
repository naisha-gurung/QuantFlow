package com.quantflow.algorithm;

import com.quantflow.model.MarketData;
import com.quantflow.model.TradeSignal;
import java.util.List;

/**
 * Strategy interface following the Strategy design pattern.
 * All trading algorithms implement this contract.
 */
public interface TradingStrategy {
    String getName();
    List<TradeSignal> generateSignals(List<MarketData> data);
}
