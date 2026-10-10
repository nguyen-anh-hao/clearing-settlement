package com.exchange.settlement.controller;

import com.exchange.settlement.domain.LedgerEntry;
import com.exchange.settlement.domain.Trade;
import com.exchange.settlement.repository.BalanceRepository;
import com.exchange.settlement.repository.LedgerRepository;
import com.exchange.settlement.repository.TradeRepository;
import com.exchange.settlement.service.InsufficientFundsException;
import com.exchange.settlement.service.LedgerService;
import com.exchange.settlement.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP API for clearing & settlement. Monetary values are serialized as plain
 * strings to preserve fixed-point precision across languages.
 */
@RestController
public class SettlementController {

    private final SettlementService settlement;
    private final LedgerService ledger;
    private final LedgerRepository repository;
    private final TradeRepository tradeRepository;
    private final BalanceRepository balanceRepository;

    public SettlementController(SettlementService settlement,
                                LedgerService ledger, LedgerRepository repository,
                                TradeRepository tradeRepository, BalanceRepository balanceRepository) {
        this.settlement = settlement;
        this.ledger = ledger;
        this.repository = repository;
        this.tradeRepository = tradeRepository;
        this.balanceRepository = balanceRepository;
    }

    @PostMapping("/settle")
    public Map<String, Object> settle(@RequestBody Map<String, Object> body) {
        Trade t = new Trade(
                longField(body, "trade_id"),
                (String) body.get("symbol"),
                new BigDecimal((String) body.get("price")),
                new BigDecimal((String) body.get("quantity")),
                new BigDecimal((String) body.getOrDefault("buyer_fee", "0")),
                new BigDecimal((String) body.getOrDefault("seller_fee", "0")),
                longField(body, "maker_order_id"),
                longField(body, "taker_order_id"),
                longField(body, "maker_account_id"),
                longField(body, "taker_account_id"),
                longField(body, "buyer_account_id"),
                longField(body, "seller_account_id"));
        // Idempotency: a replayed /settle for the same trade_id is a no-op.
        if (!ledger.markTradeSettled(t.tradeId())) {
            return Map.of("success", true);
        }
        List<LedgerEntry> entries = settlement.settle(t);
        tradeRepository.insert(t);
        repository.batchInsert(entries);
        balanceRepository.applyEntries(entries);
        return Map.of("success", true);
    }

    @PostMapping("/deposit")
    public Map<String, Object> deposit(@RequestBody Map<String, Object> body) {
        long accountId = ((Number) body.get("account_id")).longValue();
        String asset = (String) body.get("asset");
        BigDecimal amount = new BigDecimal((String) body.get("amount"));
        List<LedgerEntry> entries = settlement.deposit(accountId, asset, amount);
        repository.batchInsert(entries);
        balanceRepository.applyEntries(entries);
        return Map.of("success", true);
    }

    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> body) {
        long accountId = ((Number) body.get("account_id")).longValue();
        String asset = (String) body.get("asset");
        BigDecimal amount = new BigDecimal((String) body.get("amount"));
        List<LedgerEntry> entries = settlement.withdraw(accountId, asset, amount);
        repository.batchInsert(entries);
        balanceRepository.applyEntries(entries);
        return Map.of("success", true);
    }

    @GetMapping("/ledger")
    public List<Map<String, Object>> ledger(@RequestParam("account_id") long accountId) {
        return ledger.ledger(accountId).stream().map(this::toMap).toList();
    }

    @GetMapping("/portfolio")
    public List<Map<String, Object>> portfolio(@RequestParam("account_id") long accountId) {
        List<Map<String, Object>> out = new ArrayList<>();
        ledger.balances(accountId).forEach((asset, amount) -> {
            if (!asset.startsWith("STOCK:")) {
                return;
            }
            String symbol = asset.substring("STOCK:".length());
            BigDecimal avg = ledger.avgCost(accountId, symbol);
            BigDecimal market = ledger.lastPrice(symbol);
            BigDecimal unrealized = market.subtract(avg).multiply(amount);

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("symbol", symbol);
            p.put("available_quantity", amount.toPlainString());
            p.put("pending_quantity", "0");
            p.put("average_cost", avg.toPlainString());
            p.put("market_price", market.toPlainString());
            p.put("unrealized_pnl", unrealized.toPlainString());
            out.add(p);
        });
        return out;
    }

    @GetMapping("/pnl")
    public Map<String, Object> pnl(@RequestParam("account_id") long accountId) {
        BigDecimal realized = ledger.realizedPnl(accountId);
        BigDecimal unrealized = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : ledger.balances(accountId).entrySet()) {
            if (!e.getKey().startsWith("STOCK:")) {
                continue;
            }
            String symbol = e.getKey().substring("STOCK:".length());
            BigDecimal avg = ledger.avgCost(accountId, symbol);
            BigDecimal market = ledger.lastPrice(symbol);
            unrealized = unrealized.add(market.subtract(avg).multiply(e.getValue()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("realized_pnl", realized.toPlainString());
        m.put("unrealized_pnl", unrealized.toPlainString());
        return m;
    }

    private static long longField(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? 0L : ((Number) v).longValue();
    }

    private Map<String, Object> toMap(LedgerEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry_id", e.entryId());
        m.put("asset", e.asset());
        m.put("debit", e.debit().toPlainString());
        m.put("credit", e.credit().toPlainString());
        m.put("reference", e.reference());
        m.put("reason", e.reason());
        m.put("timestamp_ns", e.timestampNs());
        return m;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficient(InsufficientFundsException ex) {
        return ResponseEntity.ok(Map.of("success", false, "reason", ex.getMessage()));
    }
}
