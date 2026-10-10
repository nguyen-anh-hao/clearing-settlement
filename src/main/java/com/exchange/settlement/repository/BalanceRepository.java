package com.exchange.settlement.repository;

import com.exchange.settlement.domain.LedgerEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Materializes the `balances` read-model from the append-only ledger.
 * `total` and `available` move by (credit - debit) per entry; `frozen` stays 0
 * in the demo because pre-trade freezing is owned by the in-memory risk engine.
 */
@Repository
public class BalanceRepository {

    private final JdbcTemplate jdbc;

    public BalanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void applyEntries(List<LedgerEntry> entries) {
        for (LedgerEntry e : entries) {
            applyDelta(e.accountId(), e.asset(), e.credit().subtract(e.debit()));
        }
    }

    public void applyDelta(long accountId, String asset, BigDecimal delta) {
        jdbc.update(
                "INSERT INTO balances(account_id, asset, total, available, frozen) VALUES (?, ?, ?, ?, 0) "
                        + "ON CONFLICT (account_id, asset) DO UPDATE SET "
                        + "total = balances.total + EXCLUDED.total, "
                        + "available = balances.available + EXCLUDED.available, "
                        + "updated_at = now()",
                accountId, asset, delta, delta);
    }
}