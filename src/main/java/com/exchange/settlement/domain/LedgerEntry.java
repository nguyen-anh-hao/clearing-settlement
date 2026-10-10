package com.exchange.settlement.domain;

import java.math.BigDecimal;

/**
 * Immutable double-entry ledger row. debit is the amount leaving the account,
 * credit is the amount entering it; at least one of them is zero.
 */
public record LedgerEntry(
        long entryId,
        long accountId,
        String asset,
        BigDecimal debit,
        BigDecimal credit,
        String reference,
        String reason,
        long timestampNs) {
}
