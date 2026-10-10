package com.exchange.settlement.service;

import com.exchange.settlement.domain.LedgerEntry;
import com.exchange.settlement.domain.Trade;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Clears and settles trades by applying double-entry ledger entries against
 * cash and the traded asset for both counterparties.
 */
@Service
public class SettlementService {

    /**
     * The exchange's own treasury account (System Vault + fee revenue). All
     * deposit/withdraw/fee entries post a balancing leg here so the journal is
     * always double-entry (total debits == total credits per asset).
     */
    private static final long SYSTEM_ACCOUNT = 0L;

    private final LedgerService ledger;

    public SettlementService(LedgerService ledger) {
        this.ledger = ledger;
    }

    public List<LedgerEntry> settle(Trade t) {
        BigDecimal notional = t.price().multiply(t.quantity());
        BigDecimal buyerCost = notional.add(t.buyerFee());
        BigDecimal sellerProceeds = notional.subtract(t.sellerFee());
        BigDecimal feeRevenue = t.buyerFee().add(t.sellerFee());

        List<LedgerEntry> out = new ArrayList<>(6);
        String stock = "STOCK:" + t.symbol();
        String ref = "trade:" + t.tradeId();

        // Update cost basis / realized PnL BEFORE the ledger entries change balances.
        ledger.applyBuy(t.buyerAccountId(), t.symbol(), t.quantity(), t.price());
        ledger.applySell(t.sellerAccountId(), t.symbol(), t.quantity(), t.price());
        ledger.setLastPrice(t.symbol(), t.price());

        // Buyer: debit cash (notional + fee), credit stock.
        out.add(ledger.record(t.buyerAccountId(), "CASH", buyerCost, BigDecimal.ZERO, ref, "BUY"));
        out.add(ledger.record(t.buyerAccountId(), stock, BigDecimal.ZERO, t.quantity(), ref, "BUY"));

        // Seller: credit cash (notional - fee), debit stock.
        out.add(ledger.record(t.sellerAccountId(), "CASH", BigDecimal.ZERO, sellerProceeds, ref, "SELL"));
        out.add(ledger.record(t.sellerAccountId(), stock, t.quantity(), BigDecimal.ZERO, ref, "SELL"));

        // Exchange fee revenue (or rebate payout) so the journal stays balanced:
        // total CASH debits == total CASH credits across all accounts.
        if (feeRevenue.signum() > 0) {
            out.add(ledger.record(SYSTEM_ACCOUNT, "CASH", BigDecimal.ZERO, feeRevenue, ref, "FEE"));
        } else if (feeRevenue.signum() < 0) {
            out.add(ledger.record(SYSTEM_ACCOUNT, "CASH", feeRevenue.negate(), BigDecimal.ZERO, ref, "FEE_REBATE"));
        }

        return out;
    }

    public List<LedgerEntry> deposit(long accountId, String asset, BigDecimal amount) {
        // Balanced double-entry: credit the user, debit the System Vault.
        List<LedgerEntry> out = new ArrayList<>(2);
        out.add(ledger.record(accountId, asset, BigDecimal.ZERO, amount, "deposit", "DEPOSIT"));
        out.add(ledger.record(SYSTEM_ACCOUNT, asset, amount, BigDecimal.ZERO, "deposit", "DEPOSIT"));
        return out;
    }

    public List<LedgerEntry> withdraw(long accountId, String asset, BigDecimal amount) {
        if (ledger.balance(accountId, asset).compareTo(amount) < 0) {
            throw new InsufficientFundsException("insufficient " + asset);
        }
        // Balanced double-entry: debit the user, credit the System Vault.
        List<LedgerEntry> out = new ArrayList<>(2);
        out.add(ledger.record(accountId, asset, amount, BigDecimal.ZERO, "withdraw", "WITHDRAWAL"));
        out.add(ledger.record(SYSTEM_ACCOUNT, asset, BigDecimal.ZERO, amount, "withdraw", "WITHDRAWAL"));
        return out;
    }
}
