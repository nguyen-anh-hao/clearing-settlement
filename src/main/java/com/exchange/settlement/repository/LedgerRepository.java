package com.exchange.settlement.repository;

import com.exchange.settlement.domain.LedgerEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persists ledger entries to PostgreSQL via batch insert. The table schema
 * lives in deployments/postgres/init/01_schema.sql. When no datasource is
 * reachable the demo still works in-memory (LedgerService).
 */
@Repository
public class LedgerRepository {

    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void batchInsert(List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO ledger_entries(entry_id, account_id, asset, debit, credit, reference, reason, timestamp_ns) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                entries,
                entries.size(),
                (ps, e) -> {
                    ps.setLong(1, e.entryId());
                    ps.setLong(2, e.accountId());
                    ps.setString(3, e.asset());
                    ps.setBigDecimal(4, e.debit());
                    ps.setBigDecimal(5, e.credit());
                    ps.setString(6, e.reference());
                    ps.setString(7, e.reason());
                    ps.setLong(8, e.timestampNs());
                });
    }
}
