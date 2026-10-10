package com.exchange.settlement.repository;

import com.exchange.settlement.domain.Trade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists executed fills to the `trades` table (system of record). The schema
 * lives in deployments/postgres/init/01_schema.sql.
 */
@Repository
public class TradeRepository {

    private final JdbcTemplate jdbc;

    public TradeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Trade t) {
        jdbc.update(
                "INSERT INTO trades(trade_id, symbol, price, quantity, fee, maker_order_id, taker_order_id, maker_account_id, taker_account_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (trade_id) DO NOTHING",
                t.tradeId(), t.symbol(), t.price(), t.quantity(),
                t.buyerFee().add(t.sellerFee()),
                t.makerOrderId(), t.takerOrderId(), t.makerAccountId(), t.takerAccountId());
    }
}