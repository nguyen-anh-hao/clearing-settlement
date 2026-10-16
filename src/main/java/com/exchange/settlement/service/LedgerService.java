package com.exchange.settlement.service;

import com.exchange.settlement.domain.LedgerEntry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only immutable journal plus the derived balances and portfolio/PnL
 * state. In production the journal is persisted to PostgreSQL
 * (deployments/postgres/init/01_schema.sql) via batch inserts; this in-memory
 * implementation keeps the service runnable standalone.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final JdbcTemplate jdbc;
    private final Map<Long, Map<String, BigDecimal>> balances = new ConcurrentHashMap<>();
    private final List<LedgerEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);
    private final Set<Long> settledTrades = ConcurrentHashMap.newKeySet();

    public LedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Seeds the entry-id sequence from the persisted journal so that restarting
     * the service against a non-empty database does not collide with the
     * {@code ledger_entries} primary key. The service is designed to boot even
     * when the database is unreachable, so a failure here is logged, not fatal.
     */
    @PostConstruct
    void seedEntryIdSequence() {
        try {
            Long max = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(entry_id), 0) FROM ledger_entries", Long.class);
            if (max != null && max > 0) {
                seq.set(max + 1);
                log.info("ledger entry id sequence seeded at {}", max + 1);
            }
        } catch (Exception ex) {
            log.warn("could not seed ledger entry id sequence from database, starting at 1: {}",
                    ex.getMessage());
        }
    }

    // Portfolio / PnL state.
    private final Map<Long, Map<String, BigDecimal>> avgCosts = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    private final Map<Long, BigDecimal> realizedPnl = new ConcurrentHashMap<>();

    public LedgerEntry record(long accountId, String asset, BigDecimal debit,
                              BigDecimal credit, String reference, String reason) {
        balances.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>())
                .merge(asset, credit.subtract(debit), BigDecimal::add);
        LedgerEntry e = new LedgerEntry(seq.getAndIncrement(), accountId, asset,
                debit, credit, reference, reason, System.nanoTime());
        entries.add(e);
        return e;
    }

    public List<LedgerEntry> ledger(long accountId) {
        return entries.stream().filter(e -> e.accountId() == accountId).toList();
    }

    public BigDecimal balance(long accountId, String asset) {
        return balances.getOrDefault(accountId, Map.of()).getOrDefault(asset, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> balances(long accountId) {
        return balances.getOrDefault(accountId, Map.of());
    }

    // Moving-average cost basis for a BUY (call before the stock balance grows).
    public void applyBuy(long accountId, String symbol, BigDecimal qty, BigDecimal price) {
        BigDecimal oldQty = balance(accountId, "STOCK:" + symbol);
        BigDecimal oldCost = avgCost(accountId, symbol);
        BigDecimal newQty = oldQty.add(qty);
        BigDecimal newCost = newQty.signum() == 0
                ? BigDecimal.ZERO
                : oldCost.multiply(oldQty).add(price.multiply(qty)).divide(newQty, 8, RoundingMode.HALF_UP);
        avgCosts.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>()).put(symbol, newCost);
    }

    // Realized PnL for a SELL (call before the stock balance shrinks).
    public void applySell(long accountId, String symbol, BigDecimal qty, BigDecimal price) {
        BigDecimal avg = avgCost(accountId, symbol);
        BigDecimal realized = price.subtract(avg).multiply(qty);
        realizedPnl.merge(accountId, realized, BigDecimal::add);
    }

    public BigDecimal avgCost(long accountId, String symbol) {
        return avgCosts.getOrDefault(accountId, Map.of()).getOrDefault(symbol, BigDecimal.ZERO);
    }

    public void setLastPrice(String symbol, BigDecimal price) {
        lastPrices.put(symbol, price);
    }

    public BigDecimal lastPrice(String symbol) {
        return lastPrices.getOrDefault(symbol, BigDecimal.ZERO);
    }

    public BigDecimal realizedPnl(long accountId) {
        return realizedPnl.getOrDefault(accountId, BigDecimal.ZERO);
    }

    /**
     * Marks a trade as settled, returning false if it was already settled. Used
     * to make the /settle endpoint idempotent against replays.
     */
    public boolean markTradeSettled(long tradeId) {
        return settledTrades.add(tradeId);
    }
}

