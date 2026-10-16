package com.exchange.settlement.controller;

import com.exchange.settlement.controller.dto.DepositRequest;
import com.exchange.settlement.controller.dto.SettleRequest;
import com.exchange.settlement.controller.dto.WithdrawRequest;
import com.exchange.settlement.domain.LedgerEntry;
import com.exchange.settlement.domain.Trade;
import com.exchange.settlement.service.InsufficientFundsException;
import com.exchange.settlement.service.LedgerService;
import com.exchange.settlement.service.SettlementTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Clearing & Settlement",
        description = "Double-entry general ledger and trade settlement. Monetary values are plain strings.")
@RestController
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final LedgerService ledger;
    private final SettlementTransactionService settlementTx;

    public SettlementController(LedgerService ledger, SettlementTransactionService settlementTx) {
        this.ledger = ledger;
        this.settlementTx = settlementTx;
    }

    @Operation(summary = "Settle a matched trade",
            description = "Writes balanced double-entry ledger rows and, when the transactional outbox is "
                    + "enabled, an accounting event. Replaying the same trade_id is an idempotent no-op.")
    @PostMapping("/settle")
    public Map<String, Object> settle(@RequestBody SettleRequest body) {
        Trade t = new Trade(
                body.tradeId(),
                body.symbol(),
                new BigDecimal(body.price()),
                new BigDecimal(body.quantity()),
                new BigDecimal(body.buyerFee() == null ? "0" : body.buyerFee()),
                new BigDecimal(body.sellerFee() == null ? "0" : body.sellerFee()),
                body.makerOrderId(),
                body.takerOrderId(),
                body.makerAccountId(),
                body.takerAccountId(),
                body.buyerAccountId(),
                body.sellerAccountId());
        // Idempotency: a replayed /settle for the same trade_id is a no-op.
        if (!ledger.markTradeSettled(t.tradeId())) {
            log.debug("duplicate settle ignored trade_id={}", t.tradeId());
            return Map.of("success", true);
        }
        settlementTx.settle(t);
        log.info("trade settled trade_id={} symbol={} qty={} price={}", t.tradeId(), t.symbol(), t.quantity(), t.price());
        return Map.of("success", true);
    }

    @Operation(summary = "Apply a deposit",
            description = "Credits the account with balanced double-entry rows and writes the accounting outbox events.")
    @PostMapping("/deposit")
    public Map<String, Object> deposit(@RequestBody DepositRequest body) {
        BigDecimal amount = new BigDecimal(body.amount());
        settlementTx.deposit(body.accountId(), body.asset(), amount);
        log.info("deposit account_id={} asset={} amount={}", body.accountId(), body.asset(), amount);
        return Map.of("success", true);
    }

    @Operation(summary = "Apply a withdrawal",
            description = "Debits the account. Responds with success=false when funds are insufficient.")
    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody WithdrawRequest body) {
        BigDecimal amount = new BigDecimal(body.amount());
        settlementTx.withdraw(body.accountId(), body.asset(), amount);
        log.info("withdraw account_id={} asset={} amount={}", body.accountId(), body.asset(), amount);
        return Map.of("success", true);
    }

    @Operation(summary = "Query the immutable ledger audit trail for an account")
    @GetMapping("/ledger")
    public List<Map<String, Object>> ledger(
            @Parameter(description = "Account id", example = "1001")
            @RequestParam("account_id") long accountId) {
        return ledger.ledger(accountId).stream().map(this::toMap).toList();
    }

    @Operation(summary = "Current stock positions with average cost and unrealized PnL")
    @GetMapping("/portfolio")
    public List<Map<String, Object>> portfolio(
            @Parameter(description = "Account id", example = "1001")
            @RequestParam("account_id") long accountId) {
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

    @Operation(summary = "Realized and unrealized PnL for an account")
    @GetMapping("/pnl")
    public Map<String, Object> pnl(
            @Parameter(description = "Account id", example = "1001")
            @RequestParam("account_id") long accountId) {
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
        log.warn("insufficient funds: {}", ex.getMessage());
        return ResponseEntity.ok(Map.of("success", false, "reason", ex.getMessage()));
    }
}
