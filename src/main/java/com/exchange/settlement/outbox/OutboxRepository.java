package com.exchange.settlement.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes rows to the {@code outbox} table (schema in
 * deployments/postgres/init/01_schema.sql). Rows are written in the same DB
 * transaction as the ledger/trades/balances writes, so the accounting event is
 * only durable when the business change commits. Debezium CDC tails this table
 * and relays the events to Kafka.
 */
@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String aggregateType, String aggregateId, String eventType, byte[] payload) {
        jdbc.update(
                "INSERT INTO outbox(aggregatetype, aggregateid, type, payload) VALUES (?, ?, ?, ?)",
                aggregateType, aggregateId, eventType, payload);
    }
}
