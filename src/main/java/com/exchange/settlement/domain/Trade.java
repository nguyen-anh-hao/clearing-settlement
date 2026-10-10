package com.exchange.settlement.domain;

import java.math.BigDecimal;

/**
 * A matched trade to be settled between a buyer and a seller. Carries both the
 * exchange-facing maker/taker identity (for the `trades` table) and the
 * settlement-facing buyer/seller identity (for the double-entry ledger). Each
 * side has its own role-based fee (maker fee vs taker fee).
 */
public record Trade(
        long tradeId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal buyerFee,
        BigDecimal sellerFee,
        long makerOrderId,
        long takerOrderId,
        long makerAccountId,
        long takerAccountId,
        long buyerAccountId,
        long sellerAccountId) {
}
