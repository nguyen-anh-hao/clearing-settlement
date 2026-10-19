package com.exchange.settlement.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Samples the transactional outbox so the settlement service exposes the same
 * `trading_outbox_pending` / `trading_outbox_oldest_age_seconds` gauges as the
 * Go producers. The database is allowed to be unavailable at boot, so a failed
 * sample keeps the previous value and logs at debug level.
 */
@Component
public class OutboxMetrics {

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

    private final JdbcTemplate jdbc;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        meters.gauge("trading_outbox_pending", pending, AtomicLong::doubleValue);
        meters.gauge("trading_outbox_oldest_age_seconds", oldestAgeSeconds, AtomicLong::doubleValue);
    }

    @Scheduled(fixedDelayString = "${app.outbox.metrics-interval-ms:15000}")
    public void sample() {
        try {
            Long count = jdbc.queryForObject(
                    "SELECT count(*) FROM outbox WHERE status = 'PENDING'", Long.class);
            pending.set(count == null ? 0 : count);

            Double age = jdbc.queryForObject(
                    "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - MIN(created_at))), 0) "
                            + "FROM outbox WHERE status = 'PENDING'",
                    Double.class);
            oldestAgeSeconds.set(age == null ? 0 : age.longValue());
        } catch (Exception ex) {
            log.debug("outbox metrics sample failed: {}", ex.getMessage());
        }
    }
}
