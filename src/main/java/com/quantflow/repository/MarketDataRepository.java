package com.quantflow.repository;

import com.quantflow.model.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    List<MarketData> findBySymbolAndDateBetweenOrderByDateAsc(
        String symbol, LocalDate start, LocalDate end);

    Optional<MarketData> findTopBySymbolOrderByDateDesc(String symbol);

    @Query("SELECT DISTINCT m.symbol FROM MarketData m ORDER BY m.symbol")
    List<String> findDistinctSymbols();

    @Query("SELECT m FROM MarketData m WHERE m.symbol = :symbol ORDER BY m.date DESC")
    List<MarketData> findLatestN(@Param("symbol") String symbol,
                                  org.springframework.data.domain.Pageable pageable);
}
